package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.exception.AddressCycleDetectedException;
import dev.akume.storage.location.application.exception.AddressHasActiveChildrenException;
import dev.akume.storage.location.application.exception.AddressTypeInUseException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.exception.SerializableOperationConflictException;
import dev.akume.storage.location.application.port.in.ActivateAddressCommand;
import dev.akume.storage.location.application.port.in.ActivateAddressUseCase;
import dev.akume.storage.location.application.port.in.CreateAddressCommand;
import dev.akume.storage.location.application.port.in.CreateAddressUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressCommand;
import dev.akume.storage.location.application.port.in.DeactivateAddressUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.MoveAddressCommand;
import dev.akume.storage.location.application.port.in.MoveAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Real-PostgreSQL acceptance of the hierarchy and AddressType SSI invariants. */
@SpringBootTest
@Import(AddressConcurrencyAcceptanceTest.ConcurrencyTestConfiguration.class)
class AddressConcurrencyAcceptanceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private CreateAddressUseCase createAddresses;

    @Autowired
    private MoveAddressUseCase moveAddresses;

    @Autowired
    private ActivateAddressUseCase activateAddresses;

    @Autowired
    private DeactivateAddressUseCase deactivateAddresses;

    @Autowired
    private DeactivateAddressTypeUseCase deactivateAddressTypes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ActorContext actorContext;

    @Autowired
    private AttemptRecorder attemptRecorder;

    @Autowired
    private WriteGate writeGate;

    private final List<UUID> fixtureTypeIds = new ArrayList<>();

    @BeforeEach
    void removeFixturesFromAnInterruptedPriorRun() {
        writeGate.clear();
        actorContext.clear();
        attemptRecorder.clear();
        List<UUID> staleTypeIds = jdbc.query(
                "SELECT id FROM address_types WHERE left(code, 11) = 'M203F_RACE_'",
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class));
        deleteFixtureTypes(staleTypeIds);
    }

    @AfterEach
    void cleanUp() {
        writeGate.clear();
        actorContext.clear();
        attemptRecorder.clear();

        deleteFixtureTypes(fixtureTypeIds);
        fixtureTypeIds.clear();
    }

    private void deleteFixtureTypes(List<UUID> typeIds) {
        // Moves can change fixture depth, so delete current leaves until no fixture rows remain.
        List<UUID> remaining = new ArrayList<>();
        for (UUID typeId : typeIds) {
            remaining.addAll(jdbc.query(
                    "SELECT id FROM addresses WHERE address_type_id = ?",
                    (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), typeId));
        }
        while (!remaining.isEmpty()) {
            List<UUID> leaves = remaining.stream()
                    .filter(id -> !Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM addresses WHERE parent_id = ?)", Boolean.class, id)))
                    .toList();
            if (leaves.isEmpty()) {
                throw new IllegalStateException("fixture hierarchy contains a cycle; cannot clean safely: " + remaining);
            }
            for (UUID leaf : leaves) {
                jdbc.update("DELETE FROM addresses WHERE id = ?", leaf);
                remaining.remove(leaf);
            }
        }
        for (UUID typeId : typeIds) {
            jdbc.update("DELETE FROM address_types WHERE id = ?", typeId);
        }
    }

    @Test
    void createAddressRacingAddressTypeDeactivationPreservesTypeInvariant() {
        AddressType type = createType();
        writeGate.install("CREATE", "TYPE_DEACTIVATE");

        RaceResult result = race(
                "CREATE", "TYPE_DEACTIVATE",
                () -> createAddresses.create(new CreateAddressCommand("Race A " + UUID.randomUUID(), type.id(), null)),
                () -> deactivateAddressTypes.deactivate(type.id()),
                Set.of(InactiveAddressTypeException.class),
                Set.of(AddressTypeInUseException.class));

        AddressType typeAfter = addressTypes.findById(type.id()).orElseThrow();
        List<Address> usingType = addresses.findAll().stream()
                .filter(address -> address.addressTypeId().equals(type.id()))
                .toList();

        // The persisted state is authoritative. Check the invariant before classifying the
        // terminal outcomes so an unexpected result cannot mask a committed violation.
        assertTrue(usingType.stream().noneMatch(Address::active) || typeAfter.active(), result.diagnostics());
        assertTrue(result.failedBeforeGate().isEmpty(), result.diagnostics());
        assertEquals(2, writeGate.successfulArrivals().size(), result.diagnostics());

        Outcome createOutcome = result.firstOutcome();
        Outcome typeDeactivateOutcome = result.secondOutcome();
        if (isSuccess(createOutcome)
                && isSemanticRejection(typeDeactivateOutcome, AddressTypeInUseException.class)) {
            assertActorRetried(result, "TYPE_DEACTIVATE");
        } else if (isSuccess(createOutcome) && isSerializationExhausted(typeDeactivateOutcome)) {
            assertActorExhausted(result, "TYPE_DEACTIVATE", typeDeactivateOutcome);
        } else if (isSemanticRejection(createOutcome, InactiveAddressTypeException.class)
                && isSuccess(typeDeactivateOutcome)) {
            assertActorRetried(result, "CREATE");
        } else if (isSerializationExhausted(createOutcome) && isSuccess(typeDeactivateOutcome)) {
            assertActorExhausted(result, "CREATE", createOutcome);
        } else {
            fail("unexpected Race A terminal outcome pair: " + result.diagnostics());
        }
    }

    @Test
    void createChildRacingParentDeactivationPreservesParentInvariant() {
        AddressType type = createType();
        Address parent = createAddress("Race B parent", type, null);
        writeGate.install("CHILD_CREATE", "PARENT_DEACTIVATE");

        RaceResult result = race(
                "CHILD_CREATE", "PARENT_DEACTIVATE",
                () -> createAddresses.create(new CreateAddressCommand(
                        "Race B child " + UUID.randomUUID(), type.id(), parent.id())),
                () -> deactivateAddresses.deactivate(new DeactivateAddressCommand(parent.id(), parent.version())),
                Set.of(InactiveAddressParentException.class),
                Set.of(AddressHasActiveChildrenException.class));

        Address parentAfter = reload(parent.id());
        List<Address> children = addresses.findDirectChildren(parent.id());

        // The committed database state is authoritative. Check the safety invariant before
        // classifying terminal outcomes, so an unexpected outcome cannot mask a violation.
        assertTrue(children.stream().noneMatch(Address::active) || parentAfter.active(), result.diagnostics());
        assertTrue(result.failedBeforeGate().isEmpty(), result.diagnostics());
        assertEquals(2, writeGate.successfulArrivals().size(), result.diagnostics());

        Outcome childOutcome = result.firstOutcome();
        Outcome parentOutcome = result.secondOutcome();
        if (isSuccess(childOutcome)
                && isSemanticRejection(parentOutcome, AddressHasActiveChildrenException.class)) {
            assertActorRetried(result, "PARENT_DEACTIVATE");
        } else if (isSuccess(childOutcome) && isSerializationExhausted(parentOutcome)) {
            assertActorExhausted(result, "PARENT_DEACTIVATE", parentOutcome);
        } else if (isSemanticRejection(childOutcome, InactiveAddressParentException.class)
                && isSuccess(parentOutcome)) {
            assertActorRetried(result, "CHILD_CREATE");
        } else if (isSerializationExhausted(childOutcome) && isSuccess(parentOutcome)) {
            assertActorExhausted(result, "CHILD_CREATE", childOutcome);
        } else {
            fail("unexpected Race B terminal outcome pair: " + result.diagnostics());
        }
    }

    @Test
    void moveRacingDestinationDeactivationPreservesParentInvariant() {
        AddressType type = createType();
        Address source = createAddress("Race C source", type, null);
        Address destination = createAddress("Race C destination", type, null);
        writeGate.install("MOVE", "DESTINATION_DEACTIVATE");

        RaceResult result = race(
                "MOVE", "DESTINATION_DEACTIVATE",
                () -> moveAddresses.move(new MoveAddressCommand(
                        source.id(), destination.id(), source.version())),
                () -> deactivateAddresses.deactivate(
                        new DeactivateAddressCommand(destination.id(), destination.version())),
                Set.of(InactiveAddressParentException.class),
                Set.of(AddressHasActiveChildrenException.class));

        Address sourceAfter = reload(source.id());
        Address destinationAfter = reload(destination.id());

        // The committed database state is authoritative. Check the safety invariant before
        // classifying terminal outcomes, so an unexpected result cannot mask a violation.
        assertFalse(sourceAfter.active() && destination.id().equals(sourceAfter.parentId())
                && !destinationAfter.active(), result.diagnostics());
        assertTrue(result.failedBeforeGate().isEmpty(), result.diagnostics());
        assertEquals(2, writeGate.successfulArrivals().size(), result.diagnostics());

        Outcome moveOutcome = result.firstOutcome();
        Outcome destinationDeactivateOutcome = result.secondOutcome();
        if (isSuccess(moveOutcome)
                && isSemanticRejection(destinationDeactivateOutcome, AddressHasActiveChildrenException.class)) {
            assertActorRetried(result, "DESTINATION_DEACTIVATE");
        } else if (isSuccess(moveOutcome) && isSerializationExhausted(destinationDeactivateOutcome)) {
            assertActorExhausted(result, "DESTINATION_DEACTIVATE", destinationDeactivateOutcome);
        } else if (isSemanticRejection(moveOutcome, InactiveAddressParentException.class)
                && isSuccess(destinationDeactivateOutcome)) {
            assertActorRetried(result, "MOVE");
        } else if (isSerializationExhausted(moveOutcome) && isSuccess(destinationDeactivateOutcome)) {
            assertActorExhausted(result, "MOVE", moveOutcome);
        } else {
            fail("unexpected Race C terminal outcome pair: " + result.diagnostics());
        }
    }

    @Test
    void childActivationRacingParentDeactivationPreservesParentInvariant() {
        AddressType type = createType();
        Address parent = createAddress("Race D parent", type, null);
        Address child = createAddress("Race D child", type, parent.id());
        Address inactiveChild = deactivateAddresses.deactivate(
                new DeactivateAddressCommand(child.id(), child.version()));
        assertFalse(inactiveChild.active());
        writeGate.install("CHILD_ACTIVATE", "PARENT_DEACTIVATE");

        RaceResult result = race(
                "CHILD_ACTIVATE", "PARENT_DEACTIVATE",
                () -> activateAddresses.activate(new ActivateAddressCommand(child.id(), inactiveChild.version())),
                () -> deactivateAddresses.deactivate(new DeactivateAddressCommand(parent.id(), parent.version())),
                Set.of(InactiveAddressParentException.class),
                Set.of(AddressHasActiveChildrenException.class));

        Address parentAfter = reload(parent.id());
        Address childAfter = reload(child.id());

        String raceDiagnostics = result.diagnostics()
                + ", persisted={parentActive=" + parentAfter.active()
                + ", childActive=" + childAfter.active()
                + ", childParentId=" + childAfter.parentId()
                + ", expectedParentId=" + parent.id() + "}";

        assertTrue(!childAfter.active() || parentAfter.active(), raceDiagnostics);
        assertEquals(parent.id(), childAfter.parentId(), raceDiagnostics);
        assertTrue(result.failedBeforeGate().isEmpty(), raceDiagnostics);
        assertEquals(2, writeGate.successfulArrivals().size(), raceDiagnostics);

        Outcome childOutcome = result.firstOutcome();
        Outcome parentOutcome = result.secondOutcome();
        String retriedActor = null;
        String exhaustedActor = null;
        Outcome exhaustedOutcome = null;
        if (isSuccess(childOutcome)
                && isSemanticRejection(parentOutcome, AddressHasActiveChildrenException.class)) {
            retriedActor = "PARENT_DEACTIVATE";
        } else if (isSuccess(parentOutcome)
                && isSemanticRejection(childOutcome, InactiveAddressParentException.class)) {
            retriedActor = "CHILD_ACTIVATE";
        } else if (isSuccess(childOutcome) && isSerializationExhausted(parentOutcome)) {
            exhaustedActor = "PARENT_DEACTIVATE";
            exhaustedOutcome = parentOutcome;
        } else if (isSuccess(parentOutcome) && isSerializationExhausted(childOutcome)) {
            exhaustedActor = "CHILD_ACTIVATE";
            exhaustedOutcome = childOutcome;
        } else {
            fail("unexpected Race D terminal outcome pair: " + raceDiagnostics);
        }

        if (retriedActor != null) {
            List<Long> transactionIds = result.attempts().get(retriedActor);
            assertTrue(transactionIds != null && transactionIds.size() >= 2,
                    "expected semantic loser to retry in a fresh transaction for "
                            + retriedActor + ": " + raceDiagnostics);
            assertNotEquals(transactionIds.getFirst(), transactionIds.get(1),
                    "semantic retry reused transaction for " + retriedActor + ": " + raceDiagnostics);
        }

        if (exhaustedActor != null) {
            assertTrue(exhaustedOutcome.failure() != null
                            && exhaustedOutcome.failure().getClass()
                            == SerializableOperationConflictException.class,
                    "exhaustion must be the executor's semantic exhaustion exception for "
                            + exhaustedActor + ": " + raceDiagnostics);
            List<Long> transactionIds = result.attempts().get(exhaustedActor);
            assertTrue(transactionIds != null,
                    "missing transaction attempts for exhausted actor "
                            + exhaustedActor + ": " + raceDiagnostics);
            assertEquals(3, transactionIds.size(),
                    "executor exhaustion requires three transaction attempts for "
                            + exhaustedActor + ": " + raceDiagnostics);
            assertEquals(3, transactionIds.stream().distinct().count(),
                    "exhaustion attempts must use distinct PostgreSQL transactions for "
                            + exhaustedActor + ": " + raceDiagnostics);
        }
    }

    @Test
    void opposingMovesCannotCommitHierarchyCycle() {
        AddressType type = createType();
        Address root = createAddress("Race E root", type, null);
        Address a = createAddress("Race E A", type, root.id());
        Address b = createAddress("Race E B", type, root.id());
        writeGate.install("MOVE_A", "MOVE_B");

        RaceResult result = race(
                "MOVE_A", "MOVE_B",
                () -> moveAddresses.move(new MoveAddressCommand(a.id(), b.id(), a.version())),
                () -> moveAddresses.move(new MoveAddressCommand(b.id(), a.id(), b.version())),
                Set.of(AddressCycleDetectedException.class),
                Set.of(AddressCycleDetectedException.class));

        Address aAfter = reload(a.id());
        Address bAfter = reload(b.id());

        assertExactlyOneSuccessAndRejection(result);
        assertRetryObserved(result);
        assertEquals(2, writeGate.successfulArrivals().size(), result.diagnostics());
        assertFalse(b.id().equals(aAfter.parentId()) && a.id().equals(bAfter.parentId()), result.diagnostics());
        assertFalse(addresses.isDescendant(a.id(), b.id()) && addresses.isDescendant(b.id(), a.id()),
                result.diagnostics());
    }

    private RaceResult race(
            String firstActor,
            String secondActor,
            ThrowingSupplier<?> firstOperation,
            ThrowingSupplier<?> secondOperation,
            Set<Class<? extends RuntimeException>> firstSemanticExceptions,
            Set<Class<? extends RuntimeException>> secondSemanticExceptions) {
        attemptRecorder.clear();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<Outcome> first = workers.submit(() -> runAs(
                firstActor, firstOperation, firstSemanticExceptions));
        Future<Outcome> second = workers.submit(() -> runAs(
                secondActor, secondOperation, secondSemanticExceptions));

        try {
            writeGate.awaitGateOutcome(TIMEOUT);
            if (!writeGate.failedBeforeGate().isEmpty()) {
                // A first-attempt failure means the intended two-write overlap was not established.
                // Release the peer so it can terminate, but never count a retry as a substitute arrival.
                writeGate.release();
            } else {
                assertInitialGateProof(firstActor, secondActor);
                writeGate.release();
            }

            Outcome firstOutcome = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            Outcome secondOutcome = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            RaceResult result = new RaceResult(firstActor, firstOutcome, secondActor, secondOutcome,
                    attemptRecorder.snapshot(), writeGate.successfulArrivals(), writeGate.failedBeforeGate());
            if (!result.failedBeforeGate().isEmpty()) {
                throw new AssertionError("first-attempt overlap was not established: " + result.diagnostics());
            }
            return result;
        } catch (Exception exception) {
            writeGate.release();
            first.cancel(true);
            second.cancel(true);
            throw new AssertionError("concurrent race did not complete: "
                    + diagnostics(firstActor, secondActor), exception);
        } finally {
            writeGate.release();
            workers.shutdownNow();
            try {
                assertTrue(workers.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                        "race worker executor did not terminate");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("interrupted while terminating race workers", exception);
            }
            actorContext.clear();
        }
    }

    private Outcome runAs(
            String actor,
            ThrowingSupplier<?> operation,
            Set<Class<? extends RuntimeException>> semanticExceptions) {
        actorContext.set(actor);
        Outcome outcome;
        try {
            operation.get();
            outcome = Outcome.success();
        } catch (Throwable failure) {
            if (failure instanceof RuntimeException runtime
                    && semanticExceptions.stream().anyMatch(type -> type.isInstance(runtime))) {
                outcome = Outcome.semanticRejection(runtime);
            } else {
                outcome = Outcome.unexpectedFailure(failure);
            }
        } finally {
            actorContext.clear();
        }
        writeGate.actorFinished(actor, outcome);
        return outcome;
    }

    private void assertInitialGateProof(String firstActor, String secondActor) {
        List<GateArrival> arrivals = writeGate.successfulArrivals();
        assertEquals(2, arrivals.size(), "both attempt-1 writes must flush before release: "
                + diagnostics(firstActor, secondActor));
        for (String actor : List.of(firstActor, secondActor)) {
            GateArrival arrival = arrivals.stream().filter(item -> item.actor().equals(actor)).findFirst()
                    .orElseThrow(() -> new AssertionError("missing gate arrival for " + actor));
            assertEquals(1, arrival.attemptNumber(), "gate must only accept attempt 1: " + diagnostics(firstActor, secondActor));
            List<Long> txids = attemptRecorder.snapshot().get(actor);
            assertTrue(txids != null && !txids.isEmpty(), "missing PostgreSQL attempt ID for " + actor);
            assertEquals(txids.getFirst(), arrival.transactionId(),
                    "gate transaction must be the actor's first recorded transaction: "
                            + diagnostics(firstActor, secondActor));
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private void assertExactlyOneSuccessAndRejection(RaceResult result) {
        List<Outcome> outcomes = List.of(result.firstOutcome(), result.secondOutcome());
        assertEquals(1, outcomes.stream().filter(outcome -> outcome.kind() == OutcomeKind.SUCCESS).count(),
                result.diagnostics());
        assertEquals(1, outcomes.stream().filter(outcome -> outcome.kind() == OutcomeKind.SEMANTIC_REJECTION).count(),
                result.diagnostics());
        assertTrue(outcomes.stream().noneMatch(outcome -> outcome.kind() == OutcomeKind.UNEXPECTED_FAILURE
                        || outcome.kind() == OutcomeKind.SERIALIZATION_EXHAUSTED), result.diagnostics());
    }

    private void assertRetryObserved(RaceResult result) {
        boolean retry = result.attempts().values().stream().anyMatch(ids -> ids.size() >= 2);
        assertTrue(retry, "no executor retry observed: " + result.diagnostics());
        result.attempts().forEach((actor, ids) -> {
            if (ids.size() >= 2) {
                assertNotEquals(ids.get(0), ids.get(1), "retry reused transaction for " + actor + ": " + result.diagnostics());
            }
        });
    }

    private void assertActorRetried(RaceResult result, String actor) {
        List<Long> transactionIds = result.attempts().get(actor);
        assertTrue(transactionIds != null && transactionIds.size() >= 2,
                "expected semantic loser to retry in a fresh transaction for " + actor + ": "
                        + result.diagnostics());
        assertNotEquals(transactionIds.getFirst(), transactionIds.get(1),
                "semantic retry reused transaction for " + actor + ": " + result.diagnostics());
    }

    private void assertActorExhausted(RaceResult result, String actor, Outcome outcome) {
        assertTrue(outcome.failure() instanceof SerializableOperationConflictException,
                "exhaustion must be the executor's semantic exhaustion exception for " + actor + ": "
                        + result.diagnostics());
        List<Long> transactionIds = result.attempts().get(actor);
        assertTrue(transactionIds != null, "missing transaction attempts for exhausted actor " + actor);
        assertEquals(3, transactionIds.size(),
                "executor exhaustion requires three distinct transaction attempts for " + actor + ": "
                        + result.diagnostics());
        assertEquals(3, transactionIds.stream().distinct().count(),
                "exhaustion attempts must use distinct PostgreSQL transactions for " + actor + ": "
                        + result.diagnostics());
    }

    private boolean isSuccess(Outcome outcome) {
        return outcome.kind() == OutcomeKind.SUCCESS && outcome.failure() == null;
    }

    private boolean isSemanticRejection(Outcome outcome, Class<? extends RuntimeException> expectedType) {
        return outcome.kind() == OutcomeKind.SEMANTIC_REJECTION
                && outcome.failure() != null
                && outcome.failure().getClass() == expectedType;
    }

    private boolean isSerializationExhausted(Outcome outcome) {
        return outcome.kind() == OutcomeKind.SERIALIZATION_EXHAUSTED
                && outcome.failure() instanceof SerializableOperationConflictException;
    }

    private String diagnostics(String firstActor, String secondActor) {
        return "gate=" + writeGate.successfulArrivals()
                + ", attempts=" + attemptRecorder.snapshot()
                + ", actors=" + firstActor + "/" + secondActor;
    }

    private AddressType createType() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        AddressType type = addressTypes.save(AddressType.create("M203F_RACE_" + suffix, "Concurrency " + suffix, null));
        fixtureTypeIds.add(type.id());
        return type;
    }

    private Address createAddress(String name, AddressType type, UUID parentId) {
        Address created = createAddresses.create(new CreateAddressCommand(name, type.id(), parentId));
        return created;
    }

    private Address reload(UUID id) {
        return addresses.findById(id).orElseThrow();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private enum OutcomeKind {
        SUCCESS,
        SEMANTIC_REJECTION,
        SERIALIZATION_EXHAUSTED,
        UNEXPECTED_FAILURE
    }

    private record Outcome(OutcomeKind kind, Throwable failure) {
        static Outcome success() {
            return new Outcome(OutcomeKind.SUCCESS, null);
        }

        static Outcome semanticRejection(Throwable failure) {
            return new Outcome(OutcomeKind.SEMANTIC_REJECTION, failure);
        }

        static Outcome unexpectedFailure(Throwable failure) {
            OutcomeKind kind = hasCause(failure,
                    dev.akume.storage.location.application.exception.SerializableOperationConflictException.class)
                    ? OutcomeKind.SERIALIZATION_EXHAUSTED : OutcomeKind.UNEXPECTED_FAILURE;
            return new Outcome(kind, failure);
        }

        private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (type.isInstance(cause)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record RaceResult(
            String firstActor,
            Outcome firstOutcome,
            String secondActor,
            Outcome secondOutcome,
            java.util.Map<String, List<Long>> attempts,
            List<GateArrival> arrivals,
            List<FailedBeforeGate> failedBeforeGate) {
        String diagnostics() {
            return "outcomes={" + firstActor + "=" + firstOutcome + ", " + secondActor + "=" + secondOutcome
                    + "}, attempts=" + attempts + ", gate=" + arrivals + ", failedBeforeGate=" + failedBeforeGate;
        }
    }

    private record GateArrival(String actor, int attemptNumber, long transactionId) {
    }

    private record AttemptIdentity(int attemptNumber, long transactionId) {
    }

    private record FailedBeforeGate(String actor, int attemptNumber, long transactionId, String failure) {
    }

    static final class ActorContext {
        private final ThreadLocal<String> actor = new ThreadLocal<>();

        void set(String value) {
            actor.set(value);
        }

        String current() {
            return actor.get();
        }

        void clear() {
            actor.remove();
        }
    }

    static final class AttemptRecorder {
        private final JdbcTemplate jdbc;
        private final java.util.concurrent.ConcurrentMap<String, ActorAttempts> attempts =
                new java.util.concurrent.ConcurrentHashMap<>();

        AttemptRecorder(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        AttemptIdentity record(String actor) {
            if (actor == null) {
                return null;
            }
            Long transactionId = jdbc.queryForObject("SELECT txid_current()", Long.class);
            return attempts.computeIfAbsent(actor, ignored -> new ActorAttempts()).record(transactionId);
        }

        java.util.Map<String, List<Long>> snapshot() {
            java.util.Map<String, List<Long>> copy = new java.util.HashMap<>();
            attempts.forEach((actor, actorAttempts) -> copy.put(actor, actorAttempts.transactionIds()));
            return java.util.Map.copyOf(copy);
        }

        long firstTransactionId(String actor) {
            List<Long> ids = snapshot().get(actor);
            return ids == null || ids.isEmpty() ? -1L : ids.getFirst();
        }

        void clear() {
            attempts.clear();
        }

        private static final class ActorAttempts {
            private final java.util.LinkedHashMap<Long, Integer> numbersByTransaction = new java.util.LinkedHashMap<>();

            synchronized AttemptIdentity record(long transactionId) {
                int attemptNumber = numbersByTransaction.computeIfAbsent(
                        transactionId, ignored -> numbersByTransaction.size() + 1);
                return new AttemptIdentity(attemptNumber, transactionId);
            }

            synchronized List<Long> transactionIds() {
                return List.copyOf(numbersByTransaction.keySet());
            }
        }
    }

    static final class WriteGate {
        private final AtomicReference<Coordinator> current = new AtomicReference<>();

        void install(String firstActor, String secondActor) {
            current.set(new Coordinator(Set.of(firstActor, secondActor)));
        }

        boolean isParticipant(String actor) {
            Coordinator coordinator = current.get();
            return coordinator != null && actor != null && coordinator.actors.contains(actor);
        }

        void afterSuccessfulFirstAttemptWrite(String actor, AttemptIdentity attempt) {
            Coordinator coordinator = current.get();
            if (coordinator != null && actor != null) {
                coordinator.afterSuccessfulFirstAttemptWrite(actor, attempt);
            }
        }

        void firstAttemptWriteFailed(String actor, AttemptIdentity attempt, Throwable failure) {
            Coordinator coordinator = current.get();
            if (coordinator != null && actor != null) {
                coordinator.firstAttemptWriteFailed(actor, attempt, failure);
            }
        }

        void retryEnteredBeforeFirstGate(String actor, AttemptIdentity attempt, long firstTransactionId) {
            Coordinator coordinator = current.get();
            if (coordinator != null && actor != null) {
                coordinator.retryEnteredBeforeFirstGate(actor, attempt, firstTransactionId);
            }
        }

        void actorFinished(String actor, Outcome outcome) {
            Coordinator coordinator = current.get();
            if (coordinator != null && actor != null) {
                coordinator.actorFinished(actor, outcome);
            }
        }

        void awaitGateOutcome(Duration timeout) throws InterruptedException {
            Coordinator coordinator = requireCoordinator();
            if (!coordinator.gateOutcome.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("timed out waiting for attempt-1 gate outcome: " + coordinator.diagnostics());
            }
        }

        void release() {
            Coordinator coordinator = current.get();
            if (coordinator != null) {
                coordinator.release.countDown();
            }
        }

        List<GateArrival> successfulArrivals() {
            Coordinator coordinator = current.get();
            return coordinator == null ? List.of() : coordinator.arrivalSnapshot();
        }

        List<FailedBeforeGate> failedBeforeGate() {
            Coordinator coordinator = current.get();
            return coordinator == null ? List.of() : coordinator.failedSnapshot();
        }

        void clear() {
            release();
            current.set(null);
        }

        private Coordinator requireCoordinator() {
            Coordinator coordinator = current.get();
            if (coordinator == null) {
                throw new IllegalStateException("no write-gate coordinator installed");
            }
            return coordinator;
        }

        private static final class Coordinator {
            private final Set<String> actors;
            private final Set<String> finished = java.util.concurrent.ConcurrentHashMap.newKeySet();
            private final List<GateArrival> arrivals = java.util.Collections.synchronizedList(new ArrayList<>());
            private final java.util.concurrent.ConcurrentMap<String, GateArrival> arrivalsByActor =
                    new java.util.concurrent.ConcurrentHashMap<>();
            private final java.util.concurrent.ConcurrentMap<String, FailedBeforeGate> failuresByActor =
                    new java.util.concurrent.ConcurrentHashMap<>();
            private final CountDownLatch gateOutcome = new CountDownLatch(1);
            private final CountDownLatch release = new CountDownLatch(1);

            private Coordinator(Set<String> actors) {
                this.actors = actors;
            }

            private void afterSuccessfulFirstAttemptWrite(String actor, AttemptIdentity attempt) {
                if (!actors.contains(actor) || attempt == null || attempt.attemptNumber() != 1) {
                    return;
                }
                GateArrival arrival = new GateArrival(actor, attempt.attemptNumber(), attempt.transactionId());
                if (arrivalsByActor.putIfAbsent(actor, arrival) != null) {
                    return;
                }
                arrivals.add(arrival);
                if (arrivalsByActor.keySet().containsAll(actors)) {
                    gateOutcome.countDown();
                }
                try {
                    if (!release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new AssertionError("timed out at post-flush gate for " + actor);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted at post-flush gate for " + actor, exception);
                }
            }

            private void firstAttemptWriteFailed(String actor, AttemptIdentity attempt, Throwable failure) {
                if (actors.contains(actor) && attempt != null && attempt.attemptNumber() == 1
                        && !arrivalsByActor.containsKey(actor)) {
                    recordFailure(actor, attempt, failure.getClass().getSimpleName() + ": " + failure.getMessage());
                }
            }

            private void retryEnteredBeforeFirstGate(
                    String actor, AttemptIdentity attempt, long firstTransactionId) {
                if (actors.contains(actor) && attempt != null && attempt.attemptNumber() > 1
                        && !arrivalsByActor.containsKey(actor)) {
                    recordFailure(actor, new AttemptIdentity(1, firstTransactionId),
                            "attempt " + attempt.attemptNumber() + " entered without an attempt-1 gate arrival");
                }
            }

            private void actorFinished(String actor, Outcome outcome) {
                if (actors.contains(actor) && finished.add(actor) && !arrivalsByActor.containsKey(actor)
                        && !failuresByActor.containsKey(actor)) {
                    String detail = outcome.failure() == null ? outcome.kind().name()
                            : outcome.failure().getClass().getSimpleName() + ": " + outcome.failure().getMessage();
                    recordFailure(actor, new AttemptIdentity(1, -1L),
                            "operation ended before attempt-1 post-flush gate: " + detail);
                }
            }

            private void recordFailure(String actor, AttemptIdentity attempt, String failure) {
                failuresByActor.putIfAbsent(actor,
                        new FailedBeforeGate(actor, attempt.attemptNumber(), attempt.transactionId(), failure));
                gateOutcome.countDown();
                // Failure path only: wake any peer already waiting so futures can terminate.
                release.countDown();
            }

            private List<GateArrival> arrivalSnapshot() {
                synchronized (arrivals) {
                    return List.copyOf(arrivals);
                }
            }

            private List<FailedBeforeGate> failedSnapshot() {
                return List.copyOf(failuresByActor.values());
            }

            private String diagnostics() {
                return "arrivals=" + arrivalSnapshot() + ", failedBeforeGate=" + failedSnapshot();
            }
        }
    }

    private static final class CoordinatedAddressRepository implements AddressRepository {
        private final AddressRepository delegate;
        private final ActorContext actorContext;
        private final AttemptRecorder attempts;
        private final WriteGate gate;
        private CoordinatedAddressRepository(
                AddressRepository delegate, ActorContext actorContext, AttemptRecorder attempts,
                WriteGate gate) {
            this.delegate = delegate;
            this.actorContext = actorContext;
            this.attempts = attempts;
            this.gate = gate;
        }

        private AttemptIdentity recordAttempt() {
            String actor = actorContext.current();
            AttemptIdentity attempt = attempts.record(actor);
            if (attempt != null && attempt.attemptNumber() > 1) {
                gate.retryEnteredBeforeFirstGate(actor, attempt, attempts.firstTransactionId(actor));
            }
            return attempt;
        }

        private void afterSuccessfulWrite(String actor, AttemptIdentity attempt) {
            if (gate.isParticipant(actor) && attempt.attemptNumber() == 1) {
                gate.afterSuccessfulFirstAttemptWrite(actor, attempt);
            }
        }

        @Override
        public Address insert(Address address) {
            String actor = actorContext.current();
            AttemptIdentity attempt = recordAttempt();
            boolean gateEligible = gate.isParticipant(actor) && attempt.attemptNumber() == 1;
            try {
                Address inserted = delegate.insert(address);
                if (gateEligible) {
                    afterSuccessfulWrite(actor, attempt);
                }
                return inserted;
            } catch (RuntimeException | Error failure) {
                if (gateEligible) {
                    gate.firstAttemptWriteFailed(actor, attempt, failure);
                }
                throw failure;
            }
        }

        @Override
        public Address update(Address address) {
            String actor = actorContext.current();
            AttemptIdentity attempt = recordAttempt();
            boolean gateEligible = gate.isParticipant(actor) && attempt.attemptNumber() == 1;
            try {
                Address updated = delegate.update(address);
                if (gateEligible) {
                    afterSuccessfulWrite(actor, attempt);
                }
                return updated;
            } catch (RuntimeException | Error failure) {
                if (gateEligible) {
                    gate.firstAttemptWriteFailed(actor, attempt, failure);
                }
                throw failure;
            }
        }

        @Override
        public Optional<Address> findById(UUID id) {
            recordAttempt();
            return delegate.findById(id);
        }

        @Override
        public List<Address> findAll() {
            recordAttempt();
            return delegate.findAll();
        }

        @Override
        public List<Address> findRoots() {
            recordAttempt();
            return delegate.findRoots();
        }

        @Override
        public List<Address> findDirectChildren(UUID parentId) {
            recordAttempt();
            return delegate.findDirectChildren(parentId);
        }

        @Override
        public boolean existsRootNameKey(String normalizedNameKey, UUID excludedAddressId) {
            recordAttempt();
            return delegate.existsRootNameKey(normalizedNameKey, excludedAddressId);
        }

        @Override
        public boolean existsChildNameKey(UUID parentId, String normalizedNameKey, UUID excludedAddressId) {
            recordAttempt();
            return delegate.existsChildNameKey(parentId, normalizedNameKey, excludedAddressId);
        }

        @Override
        public boolean isDescendant(UUID ancestorId, UUID candidateId) {
            recordAttempt();
            return delegate.isDescendant(ancestorId, candidateId);
        }

        @Override
        public boolean hasActiveDirectChild(UUID parentId) {
            recordAttempt();
            return delegate.hasActiveDirectChild(parentId);
        }

        @Override
        public boolean existsActiveByAddressTypeId(UUID addressTypeId) {
            recordAttempt();
            return delegate.existsActiveByAddressTypeId(addressTypeId);
        }
    }

    private static final class CoordinatedAddressTypeRepository implements AddressTypeRepository {
        private final AddressTypeRepository delegate;
        private final ActorContext actorContext;
        private final AttemptRecorder attempts;
        private final WriteGate gate;
        private CoordinatedAddressTypeRepository(
                AddressTypeRepository delegate, ActorContext actorContext, AttemptRecorder attempts,
                WriteGate gate) {
            this.delegate = delegate;
            this.actorContext = actorContext;
            this.attempts = attempts;
            this.gate = gate;
        }

        private void recordAttempt() {
            String actor = actorContext.current();
            AttemptIdentity attempt = attempts.record(actor);
            if (attempt != null && attempt.attemptNumber() > 1) {
                gate.retryEnteredBeforeFirstGate(actor, attempt, attempts.firstTransactionId(actor));
            }
        }

        @Override
        public AddressType save(AddressType addressType) {
            String actor = actorContext.current();
            AttemptIdentity attempt = attempts.record(actor);
            if (attempt != null && attempt.attemptNumber() > 1) {
                gate.retryEnteredBeforeFirstGate(actor, attempt, attempts.firstTransactionId(actor));
            }
            boolean gateEligible = gate.isParticipant(actor) && attempt.attemptNumber() == 1;
            try {
                AddressType saved = delegate.save(addressType);
                if (gateEligible) {
                    gate.afterSuccessfulFirstAttemptWrite(actor, attempt);
                }
                return saved;
            } catch (RuntimeException | Error failure) {
                if (gateEligible) {
                    gate.firstAttemptWriteFailed(actor, attempt, failure);
                }
                throw failure;
            }
        }

        @Override
        public Optional<AddressType> findById(UUID id) {
            recordAttempt();
            return delegate.findById(id);
        }

        @Override
        public Optional<AddressType> findByCode(String code) {
            recordAttempt();
            return delegate.findByCode(code);
        }

        @Override
        public boolean existsByCode(String code) {
            recordAttempt();
            return delegate.existsByCode(code);
        }

        @Override
        public List<AddressType> findAll() {
            recordAttempt();
            return delegate.findAll();
        }
    }

    @TestConfiguration
    static class ConcurrencyTestConfiguration {
        @Bean
        ActorContext actorContext() {
            return new ActorContext();
        }

        @Bean
        AttemptRecorder attemptRecorder(JdbcTemplate jdbc) {
            return new AttemptRecorder(jdbc);
        }

        @Bean
        WriteGate writeGate() {
            return new WriteGate();
        }

        @Bean
        @Primary
        AddressRepository coordinatedAddressRepository(
                @Qualifier("addressPersistenceAdapter") AddressRepository delegate,
                ActorContext actorContext,
                AttemptRecorder attempts,
                WriteGate gate) {
            return new CoordinatedAddressRepository(delegate, actorContext, attempts, gate);
        }

        @Bean
        @Primary
        AddressTypeRepository coordinatedAddressTypeRepository(
                @Qualifier("addressTypePersistenceAdapter") AddressTypeRepository delegate,
                ActorContext actorContext,
                AttemptRecorder attempts,
                WriteGate gate) {
            return new CoordinatedAddressTypeRepository(delegate, actorContext, attempts, gate);
        }
    }
}
