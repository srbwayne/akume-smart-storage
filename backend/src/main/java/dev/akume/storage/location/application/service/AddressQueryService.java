package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.port.in.GetAddressUseCase;
import dev.akume.storage.location.application.port.in.ListAddressChildrenUseCase;
import dev.akume.storage.location.application.port.in.ListAddressRootsUseCase;
import dev.akume.storage.location.application.port.in.ListAddressesUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.domain.model.Address;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AddressQueryService implements GetAddressUseCase, ListAddressesUseCase,
        ListAddressRootsUseCase, ListAddressChildrenUseCase {

    private final AddressRepository addresses;

    public AddressQueryService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    @Transactional(readOnly = true)
    public Address getById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return addresses.findById(id).orElseThrow(() -> new AddressNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> listAll() {
        return addresses.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> listRoots() {
        return addresses.findRoots();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> listDirectChildren(UUID parentId) {
        Objects.requireNonNull(parentId, "parentId must not be null");
        addresses.findById(parentId).orElseThrow(() -> new AddressNotFoundException(parentId));
        return addresses.findDirectChildren(parentId);
    }
}
