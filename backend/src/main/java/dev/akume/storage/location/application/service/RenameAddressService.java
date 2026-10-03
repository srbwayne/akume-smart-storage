package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.port.in.RenameAddressCommand;
import dev.akume.storage.location.application.port.in.RenameAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RenameAddressService implements RenameAddressUseCase {

    private final AddressRepository addresses;

    public RenameAddressService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    @Transactional
    public Address rename(RenameAddressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Address address = addresses.findById(command.addressId())
                .orElseThrow(() -> new AddressNotFoundException(command.addressId()));
        if (address.version() != command.expectedVersion()) {
            throw new AddressConcurrentModificationException(address.id());
        }

        address.updateDetails(command.name());
        boolean siblingNameExists = address.parentId() == null
                ? addresses.existsRootNameKey(address.normalizedNameKey(), address.id())
                : addresses.existsChildNameKey(
                        address.parentId(), address.normalizedNameKey(), address.id());
        if (siblingNameExists) {
            throw new AddressSiblingNameAlreadyExistsException(address.name());
        }

        return addresses.update(address);
    }
}
