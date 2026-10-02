package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.port.in.CreateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.CreateAddressTypeUseCase;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.AddressType;
import dev.akume.storage.location.domain.exception.AddressTypeCodeAlreadyExistsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAddressTypeService implements CreateAddressTypeUseCase {

    private final AddressTypeRepository addressTypes;

    public CreateAddressTypeService(AddressTypeRepository addressTypes) {
        this.addressTypes = addressTypes;
    }

    @Override
    @Transactional
    public AddressType create(CreateAddressTypeCommand command) {
        if (addressTypes.existsByCode(command.code())) {
            throw new AddressTypeCodeAlreadyExistsException(command.code());
        }

        AddressType addressType = AddressType.create(command.code(), command.name(), command.description());
        return addressTypes.save(addressType);
    }
}
