package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.in.ActivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.AddressType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AddressTypeActivationService implements ActivateAddressTypeUseCase, DeactivateAddressTypeUseCase {

    private final AddressTypeRepository addressTypes;

    public AddressTypeActivationService(AddressTypeRepository addressTypes) {
        this.addressTypes = addressTypes;
    }

    @Override
    @Transactional
    public AddressType activate(UUID id) {
        AddressType addressType = findById(id);
        addressType.activate();
        return addressTypes.save(addressType);
    }

    @Override
    @Transactional
    public AddressType deactivate(UUID id) {
        AddressType addressType = findById(id);
        addressType.deactivate();
        return addressTypes.save(addressType);
    }

    private AddressType findById(UUID id) {
        return addressTypes.findById(id).orElseThrow(() -> new AddressTypeNotFoundException(id));
    }
}
