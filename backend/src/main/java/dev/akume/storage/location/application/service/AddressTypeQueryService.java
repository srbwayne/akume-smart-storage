package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.in.GetAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.ListAddressTypesUseCase;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.AddressType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AddressTypeQueryService implements GetAddressTypeUseCase, ListAddressTypesUseCase {

    private final AddressTypeRepository addressTypes;

    public AddressTypeQueryService(AddressTypeRepository addressTypes) {
        this.addressTypes = addressTypes;
    }

    @Override
    @Transactional(readOnly = true)
    public AddressType getById(UUID id) {
        return addressTypes.findById(id).orElseThrow(() -> new AddressTypeNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressType> listAll() {
        return addressTypes.findAll();
    }
}
