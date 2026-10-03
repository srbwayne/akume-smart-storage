package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.port.in.CreateAddressCommand;
import dev.akume.storage.location.application.port.in.CreateAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class CreateAddressService implements CreateAddressUseCase {

    private final AddressRepository addresses;
    private final AddressTypeRepository addressTypes;
    private final SerializableTransactionExecutor serializableTransactions;

    public CreateAddressService(
            AddressRepository addresses,
            AddressTypeRepository addressTypes,
            SerializableTransactionExecutor serializableTransactions) {
        this.addresses = addresses;
        this.addressTypes = addressTypes;
        this.serializableTransactions = serializableTransactions;
    }

    @Override
    public Address create(CreateAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return serializableTransactions.execute(() -> createInTransaction(command));
    }

    private Address createInTransaction(CreateAddressCommand command) {
        AddressType addressType = addressTypes.findById(command.addressTypeId())
                .orElseThrow(() -> new AddressTypeNotFoundException(command.addressTypeId()));
        if (!addressType.active()) {
            throw new InactiveAddressTypeException(addressType.id());
        }

        if (command.parentId() != null) {
            Address parent = addresses.findById(command.parentId())
                    .orElseThrow(() -> new AddressNotFoundException(command.parentId()));
            if (!parent.active()) {
                throw new InactiveAddressParentException(parent.id());
            }
        }

        Address address = Address.create(command.name(), command.addressTypeId(), command.parentId());
        boolean siblingNameExists = command.parentId() == null
                ? addresses.existsRootNameKey(address.normalizedNameKey(), address.id())
                : addresses.existsChildNameKey(
                        command.parentId(), address.normalizedNameKey(), address.id());
        if (siblingNameExists) {
            throw new AddressSiblingNameAlreadyExistsException(address.name());
        }

        return addresses.insert(address);
    }
}
