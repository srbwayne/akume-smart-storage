package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeInUseException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.in.ActivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.model.AddressType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AddressTypeActivationService implements ActivateAddressTypeUseCase, DeactivateAddressTypeUseCase {

    private final AddressTypeRepository addressTypes;
    private final AddressRepository addresses;
    private final SerializableTransactionExecutor serializableTransactions;

    public AddressTypeActivationService(
            AddressTypeRepository addressTypes,
            AddressRepository addresses,
            SerializableTransactionExecutor serializableTransactions) {
        this.addressTypes = addressTypes;
        this.addresses = addresses;
        this.serializableTransactions = serializableTransactions;
    }

    @Override
    @Transactional
    public AddressType activate(UUID id) {
        AddressType addressType = findById(id);
        addressType.activate();
        return addressTypes.save(addressType);
    }

    @Override
    public AddressType deactivate(UUID id) {
        return serializableTransactions.execute(() -> deactivateInTransaction(id));
    }

    private AddressType deactivateInTransaction(UUID id) {
        AddressType addressType = findById(id);
        if (!addressType.active()) {
            return addressType;
        }
        if (addresses.existsActiveByAddressTypeId(addressType.id())) {
            throw new AddressTypeInUseException(addressType.id());
        }
        addressType.deactivate();
        return addressTypes.save(addressType);
    }

    private AddressType findById(UUID id) {
        return addressTypes.findById(id).orElseThrow(() -> new AddressTypeNotFoundException(id));
    }
}
