package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeUseCase;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.AddressType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateAddressTypeService implements UpdateAddressTypeUseCase {

    private final AddressTypeRepository addressTypes;

    public UpdateAddressTypeService(AddressTypeRepository addressTypes) {
        this.addressTypes = addressTypes;
    }

    @Override
    @Transactional
    public AddressType update(UpdateAddressTypeCommand command) {
        AddressType addressType = addressTypes.findById(command.id())
                .orElseThrow(() -> new AddressTypeNotFoundException(command.id()));

        addressType.updateDetails(command.name(), command.description());
        return addressTypes.save(addressType);
    }
}
