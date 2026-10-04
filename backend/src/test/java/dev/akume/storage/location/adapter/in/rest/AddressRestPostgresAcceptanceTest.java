package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.port.in.CreateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.CreateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Complete-stack Address REST acceptance against the configured PostgreSQL database. */
@SpringBootTest
@AutoConfigureMockMvc
class AddressRestPostgresAcceptanceTest {

    private static final String ADDRESS_API = "/api/addresses";
    private static final String TYPE_CODE_PREFIX = "M204C_ACCEPT_";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private CreateAddressTypeUseCase createAddressTypes;

    @Autowired
    private DeactivateAddressTypeUseCase deactivateAddressTypes;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearFixturesFromAnInterruptedPriorRun() {
        clearFixtures();
    }

    @AfterEach
    void cleanFixtures() {
        clearFixtures();
    }

    @Test
    void createsRootAndChildAndReadsThemAcrossTheRealStack() throws Exception {
        AddressType type = createType();

        // Carry-forward acceptance: parentId is intentionally absent from this JSON body.
        MvcResult rootResponse = mvc.perform(post(ADDRESS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Office\",\"addressTypeId\":\"" + type.id() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Office"))
                .andExpect(jsonPath("$.addressTypeId").value(type.id().toString()))
                .andExpect(jsonPath("$.parentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        UUID rootId = addressId(rootResponse);
        assertNotNull(rootResponse.getResponse().getHeader("Location"));
        assertTrue(rootResponse.getResponse().getHeader("Location").endsWith(ADDRESS_API + "/" + rootId));
        assertExactAddressFields(rootResponse);

        Address persistedRoot = reload(rootId);
        assertNull(persistedRoot.parentId(), "omitted parentId must persist as a root");
        assertEquals("Office", persistedRoot.name());

        MvcResult childResponse = mvc.perform(post(ADDRESS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cabinet\",\"addressTypeId\":\"" + type.id()
                                + "\",\"parentId\":\"" + rootId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(rootId.toString()))
                .andReturn();
        UUID childId = addressId(childResponse);
        assertEquals(rootId, reload(childId).parentId());
        assertExactAddressFields(childResponse);

        mvc.perform(get(ADDRESS_API + "/" + rootId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rootId.toString()))
                .andExpect(jsonPath("$.parentId").value(org.hamcrest.Matchers.nullValue()));
        assertCollectionContainsId(mvc.perform(get(ADDRESS_API + "/roots")).andExpect(status().isOk()).andReturn(), rootId);
        assertCollectionContainsId(mvc.perform(get(ADDRESS_API + "/" + rootId + "/children"))
                .andExpect(status().isOk()).andReturn(), childId);
        MvcResult allAddresses = mvc.perform(get(ADDRESS_API)).andExpect(status().isOk()).andReturn();
        assertCollectionContainsId(allAddresses, rootId);
        assertCollectionContainsId(allAddresses, childId);

        mvc.perform(get(ADDRESS_API + "/" + childId + "/children"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        assertError(mvc.perform(get(ADDRESS_API + "/" + UUID.randomUUID())), 404, "ADDRESS_NOT_FOUND");
        assertError(mvc.perform(get(ADDRESS_API + "/" + UUID.randomUUID() + "/children")),
                404, "ADDRESS_NOT_FOUND");
        assertError(mvc.perform(get(ADDRESS_API + "/not-a-uuid")), 400, "INVALID_REQUEST");
    }

    @Test
    void renamesMovesToParentAndRootAndChangesLifecycleWithCommittedVersions() throws Exception {
        AddressType type = createType();
        Address root = createAddress("House", type, null);
        Address child = createAddress("Cabinet", type, root.id());
        Address destination = createAddress("Workshop", type, null);

        MvcResult renameResponse = mvc.perform(put(ADDRESS_API + "/" + child.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main Cabinet\",\"expectedVersion\":" + child.version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Cabinet"))
                .andReturn();
        Address renamed = reload(child.id());
        assertEquals("Main Cabinet", renamed.name());
        assertTrue(renamed.version() > child.version());
        assertEquals(renamed.version(), jsonInt(renameResponse, "version"));

        MvcResult moveResponse = mvc.perform(post(ADDRESS_API + "/" + child.id() + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":\"" + destination.id() + "\",\"expectedVersion\":"
                                + renamed.version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(destination.id().toString()))
                .andReturn();
        Address moved = reload(child.id());
        assertEquals(destination.id(), moved.parentId());
        assertEquals(moved.version(), jsonInt(moveResponse, "version"));

        MvcResult omittedParentMove = mvc.perform(post(ADDRESS_API + "/" + child.id() + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + moved.version() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn();
        assertEquals(destination.id(), reload(child.id()).parentId(), "omitted newParentId must not move to root");
        assertFalse(omittedParentMove.getResponse().getContentAsString().contains("SQL"));

        MvcResult rootMoveResponse = mvc.perform(post(ADDRESS_API + "/" + child.id() + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":null,\"expectedVersion\":" + moved.version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        Address movedToRoot = reload(child.id());
        assertNull(movedToRoot.parentId());
        assertEquals(movedToRoot.version(), jsonInt(rootMoveResponse, "version"));

        int versionBeforeDeactivate = movedToRoot.version();
        MvcResult deactivateResponse = mvc.perform(post(ADDRESS_API + "/" + child.id() + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + versionBeforeDeactivate + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();
        Address inactive = reload(child.id());
        assertFalse(inactive.active());
        assertTrue(inactive.version() > versionBeforeDeactivate);
        assertEquals(inactive.version(), jsonInt(deactivateResponse, "version"));

        MvcResult activateResponse = mvc.perform(post(ADDRESS_API + "/" + child.id() + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + inactive.version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        Address active = reload(child.id());
        assertTrue(active.active());
        assertEquals(active.version(), jsonInt(activateResponse, "version"));
    }

    @Test
    void mapsCreateValidationAndSemanticConflictsAgainstPersistedState() throws Exception {
        AddressType activeType = createType();
        UUID missingTypeId = UUID.randomUUID();
        AddressType inactiveType = createType();
        deactivateAddressTypes.deactivate(inactiveType.id());
        assertFalse(addressTypes.findById(inactiveType.id()).orElseThrow().active());

        assertError(mvc.perform(get(ADDRESS_API + "/" + UUID.randomUUID())), 404, "ADDRESS_NOT_FOUND");
        assertError(mvc.perform(post(ADDRESS_API).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Missing type", missingTypeId, null, false))),
                404, "ADDRESS_TYPE_NOT_FOUND");
        assertError(mvc.perform(post(ADDRESS_API).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Inactive type", inactiveType.id(), null, false))),
                409, "ADDRESS_TYPE_INACTIVE");
        assertError(mvc.perform(post(ADDRESS_API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unknown\",\"addressTypeId\":\"" + missingTypeId
                                + "\",\"active\":true}")),
                400, "INVALID_REQUEST");

        Address inactiveParent = createAddress("Inactive parent", activeType, null);
        MvcResult inactiveParentResponse = mvc.perform(post(ADDRESS_API + "/" + inactiveParent.id() + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + inactiveParent.version() + "}"))
                .andExpect(status().isOk()).andReturn();
        Address persistedInactiveParent = reload(inactiveParent.id());
        assertFalse(persistedInactiveParent.active());
        int inactiveParentVersion = jsonInt(inactiveParentResponse, "version");
        assertEquals(inactiveParentVersion, persistedInactiveParent.version());
        assertError(mvc.perform(post(ADDRESS_API).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Child of inactive", activeType.id(), inactiveParent.id(), true))),
                409, "ADDRESS_PARENT_INACTIVE");

        Address siblingParent = createAddress("Sibling parent", activeType, null);
        Address sibling = createAddress("Drawer", activeType, siblingParent.id());
        assertError(mvc.perform(post(ADDRESS_API).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(" drawer ", activeType.id(), siblingParent.id(), true))),
                409, "ADDRESS_SIBLING_NAME_ALREADY_EXISTS");
        assertEquals(sibling.parentId(), reload(sibling.id()).parentId());
        assertEquals("Drawer", reload(sibling.id()).name());

        Address parentWithChild = createAddress("Occupied parent", activeType, null);
        Address activeChild = createAddress("Active child", activeType, parentWithChild.id());
        assertError(mvc.perform(post(ADDRESS_API + "/" + parentWithChild.id() + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + parentWithChild.version() + "}")),
                409, "ADDRESS_HAS_ACTIVE_CHILDREN");
        assertTrue(reload(parentWithChild.id()).active(), "blocked parent deactivation must not commit");
        assertTrue(reload(activeChild.id()).active());

        Address cycleParent = createAddress("Cycle A", activeType, null);
        Address cycleChild = createAddress("Cycle B", activeType, cycleParent.id());
        assertError(mvc.perform(post(ADDRESS_API + "/" + cycleParent.id() + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":\"" + cycleChild.id() + "\",\"expectedVersion\":"
                                + cycleParent.version() + "}")),
                409, "ADDRESS_CYCLE_DETECTED");
        assertNull(reload(cycleParent.id()).parentId());
        assertEquals(cycleParent.id(), reload(cycleChild.id()).parentId());
    }

    @Test
    void staleVersionRequestDoesNotOverwriteCommittedNameAndUnknownFieldsFail() throws Exception {
        AddressType type = createType();
        Address address = createAddress("Before", type, null);

        MvcResult updateResponse = mvc.perform(put(ADDRESS_API + "/" + address.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"After\",\"expectedVersion\":" + address.version() + "}"))
                .andExpect(status().isOk()).andReturn();
        Address committedAfterUpdate = reload(address.id());
        assertEquals("After", committedAfterUpdate.name());
        int committedVersion = committedAfterUpdate.version();
        assertEquals(committedVersion, jsonInt(updateResponse, "version"));

        assertError(mvc.perform(put(ADDRESS_API + "/" + address.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stale overwrite\",\"expectedVersion\":" + address.version() + "}")),
                409, "ADDRESS_CONCURRENT_MODIFICATION");
        assertEquals("After", reload(address.id()).name());
        assertEquals(committedVersion, reload(address.id()).version());
    }

    private AddressType createType() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        AddressType created = createAddressTypes.create(new CreateAddressTypeCommand(
                TYPE_CODE_PREFIX + suffix, "Acceptance " + suffix, null));
        assertTrue(addressTypes.findById(created.id()).orElseThrow().active());
        return created;
    }

    private Address createAddress(String name, AddressType type, UUID parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        MvcResult response = performCreate(name, type.id(), parent);
        return reload(addressId(response));
    }

    private MvcResult performCreate(String name, UUID typeId, String parentProperty) {
        try {
            return mvc.perform(post(ADDRESS_API).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\",\"addressTypeId\":\"" + typeId + "\""
                                    + parentProperty + "}"))
                    .andExpect(status().isCreated())
                    .andReturn();
        } catch (Exception exception) {
            throw new AssertionError("Address fixture creation failed", exception);
        }
    }

    private String createBody(String name, UUID typeId, UUID parentId, boolean includeParent) {
        return "{\"name\":\"" + name + "\",\"addressTypeId\":\"" + typeId + "\""
                + (includeParent ? ",\"parentId\":\"" + parentId + "\"" : "") + "}";
    }

    private void assertCollectionContainsId(MvcResult result, UUID id) throws Exception {
        assertTrue(result.getResponse().getContentAsString().contains("\"id\":\"" + id + "\""),
                "collection should contain Address " + id);
    }

    private void assertExactAddressFields(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        Set<String> fields = new HashSet<>();
        Matcher matcher = Pattern.compile("\\\"([A-Za-z]+)\\\":").matcher(response);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        assertEquals(Set.of("id", "name", "addressTypeId", "parentId", "active", "version"), fields);
        assertFalse(response.contains("normalizedNameKey"));
    }

    private void assertError(ResultActions resultActions, int expectedStatus, String expectedCode) throws Exception {
        MvcResult result = resultActions.andReturn();
        String body = result.getResponse().getContentAsString();
        assertEquals(expectedStatus, result.getResponse().getStatus());
        assertEquals(expectedStatus, jsonInt(body, "status"));
        assertTrue(body.contains("\"code\":\"" + expectedCode + "\""));
        assertTrue(body.contains("\"path\":"));
        assertFalse(body.contains("SQLSTATE"));
        assertFalse(body.contains("Hibernate"));
        assertFalse(body.contains("PostgreSQL"));
        assertFalse(body.contains("stackTrace"));
    }

    private UUID addressId(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\\\"id\\\":\\\"([0-9a-fA-F-]{36})\\\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "response should contain Address id");
        return UUID.fromString(matcher.group(1));
    }

    private int jsonInt(MvcResult result, String field) throws Exception {
        return jsonInt(result.getResponse().getContentAsString(), field);
    }

    private int jsonInt(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":(\\d+)").matcher(json);
        assertTrue(matcher.find(), "response should contain numeric " + field);
        return Integer.parseInt(matcher.group(1));
    }

    private Address reload(UUID id) {
        return addresses.findById(id).orElseThrow();
    }

    private void clearFixtures() {
        List<UUID> fixtureTypeIds = jdbc.query(
                "SELECT id FROM address_types WHERE left(code, 13) = ?",
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), TYPE_CODE_PREFIX);
        List<UUID> remaining = new ArrayList<>();
        for (UUID typeId : fixtureTypeIds) {
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
                throw new IllegalStateException("acceptance fixture hierarchy contains a cycle: " + remaining);
            }
            for (UUID leaf : leaves) {
                jdbc.update("DELETE FROM addresses WHERE id = ?", leaf);
                remaining.remove(leaf);
            }
        }
        for (UUID typeId : fixtureTypeIds) {
            jdbc.update("DELETE FROM address_types WHERE id = ?", typeId);
        }
    }
}
