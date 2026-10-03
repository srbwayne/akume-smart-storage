package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.port.in.ActivateAddressCommand;
import dev.akume.storage.location.application.port.in.ActivateAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ActivateAddressService implements ActivateAddressUseCase {

    private final AddressRepository addresses;
    private final AddressTypeRepository addressTypes;
    private final SerializableTransactionExecutor serializableTransactions;

    public ActivateAddressService(
            AddressRepository addresses,
            AddressTypeRepository addressTypes,
            SerializableTransactionExecutor serializableTransactions) {
        this.addresses = addresses;
        this.addressTypes = addressTypes;
        this.serializableTransactions = serializableTransactions;
    }

    @Override
    public Address activate(ActivateAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return serializableTransactions.execute(() -> activateInTransaction(command));
    }

    private Address activateInTransaction(ActivateAddressCommand command) {
        Address address = addresses.findById(command.addressId())
                .orElseThrow(() -> new AddressNotFoundException(command.addressId()));
        if (address.version() != command.expectedVersion()) {
            throw new AddressConcurrentModificationException(address.id());
        }

        AddressType addressType = addressTypes.findById(address.addressTypeId())
                .orElseThrow(() -> new AddressTypeNotFoundException(address.addressTypeId()));
        if (!addressType.active()) {
            throw new InactiveAddressTypeException(addressType.id());
        }

        if (address.parentId() != null) {
            Address parent = addresses.findById(address.parentId())
                    .orElseThrow(() -> new AddressNotFoundException(address.parentId()));
            if (!parent.active()) {
                throw new InactiveAddressParentException(parent.id());
            }
        }

        if (address.active()) {
            return address;
        }

        address.activate();
        return addresses.update(address);
    }
}
