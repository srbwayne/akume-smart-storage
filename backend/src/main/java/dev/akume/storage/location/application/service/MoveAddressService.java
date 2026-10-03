package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressCycleDetectedException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.port.in.MoveAddressCommand;
import dev.akume.storage.location.application.port.in.MoveAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class MoveAddressService implements MoveAddressUseCase {

    private final AddressRepository addresses;
    private final SerializableTransactionExecutor serializableTransactions;

    public MoveAddressService(
            AddressRepository addresses,
            SerializableTransactionExecutor serializableTransactions) {
        this.addresses = addresses;
        this.serializableTransactions = serializableTransactions;
    }

    @Override
    public Address move(MoveAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return serializableTransactions.execute(() -> moveInTransaction(command));
    }

    private Address moveInTransaction(MoveAddressCommand command) {
        Address source = addresses.findById(command.addressId())
                .orElseThrow(() -> new AddressNotFoundException(command.addressId()));
        if (source.version() != command.expectedVersion()) {
            throw new AddressConcurrentModificationException(source.id());
        }

        if (Objects.equals(source.parentId(), command.newParentId())) {
            return source;
        }

        if (command.newParentId() == null) {
            requireRootNameAvailable(source);
            source.applyAuthorizedParentChange(null);
            return addresses.update(source);
        }

        Address destination = addresses.findById(command.newParentId())
                .orElseThrow(() -> new AddressNotFoundException(command.newParentId()));
        if (destination.id().equals(source.id())) {
            throw new AddressCycleDetectedException(source.id(), destination.id());
        }
        if (source.active() && !destination.active()) {
            throw new InactiveAddressParentException(destination.id());
        }
        if (addresses.isDescendant(source.id(), destination.id())) {
            throw new AddressCycleDetectedException(source.id(), destination.id());
        }
        requireChildNameAvailable(source, destination.id());

        source.applyAuthorizedParentChange(destination.id());
        return addresses.update(source);
    }

    private void requireRootNameAvailable(Address source) {
        if (addresses.existsRootNameKey(source.normalizedNameKey(), source.id())) {
            throw new AddressSiblingNameAlreadyExistsException(source.name());
        }
    }

    private void requireChildNameAvailable(Address source, UUID parentId) {
        if (addresses.existsChildNameKey(parentId, source.normalizedNameKey(), source.id())) {
            throw new AddressSiblingNameAlreadyExistsException(source.name());
        }
    }
}
