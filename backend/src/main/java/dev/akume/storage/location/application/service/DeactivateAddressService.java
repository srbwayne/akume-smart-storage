package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressHasActiveChildrenException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.port.in.DeactivateAddressCommand;
import dev.akume.storage.location.application.port.in.DeactivateAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.model.Address;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DeactivateAddressService implements DeactivateAddressUseCase {

    private final AddressRepository addresses;
    private final SerializableTransactionExecutor serializableTransactions;

    public DeactivateAddressService(
            AddressRepository addresses,
            SerializableTransactionExecutor serializableTransactions) {
        this.addresses = addresses;
        this.serializableTransactions = serializableTransactions;
    }

    @Override
    public Address deactivate(DeactivateAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return serializableTransactions.execute(() -> deactivateInTransaction(command));
    }

    private Address deactivateInTransaction(DeactivateAddressCommand command) {
        Address address = addresses.findById(command.addressId())
                .orElseThrow(() -> new AddressNotFoundException(command.addressId()));
        if (address.version() != command.expectedVersion()) {
            throw new AddressConcurrentModificationException(address.id());
        }

        if (!address.active()) {
            return address;
        }

        if (addresses.hasActiveDirectChild(address.id())) {
            throw new AddressHasActiveChildrenException(address.id());
        }

        address.deactivate();
        return addresses.update(address);
    }
}
