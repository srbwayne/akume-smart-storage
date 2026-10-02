package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.port.in.ActivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.CreateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.CreateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.GetAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.ListAddressTypesUseCase;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeUseCase;
import dev.akume.storage.location.domain.model.AddressType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/address-types")
public class AddressTypeRestController {

    private final CreateAddressTypeUseCase createAddressType;
    private final GetAddressTypeUseCase getAddressType;
    private final ListAddressTypesUseCase listAddressTypes;
    private final UpdateAddressTypeUseCase updateAddressType;
    private final ActivateAddressTypeUseCase activateAddressType;
    private final DeactivateAddressTypeUseCase deactivateAddressType;

    public AddressTypeRestController(
            CreateAddressTypeUseCase createAddressType,
            GetAddressTypeUseCase getAddressType,
            ListAddressTypesUseCase listAddressTypes,
            UpdateAddressTypeUseCase updateAddressType,
            ActivateAddressTypeUseCase activateAddressType,
            DeactivateAddressTypeUseCase deactivateAddressType) {
        this.createAddressType = createAddressType;
        this.getAddressType = getAddressType;
        this.listAddressTypes = listAddressTypes;
        this.updateAddressType = updateAddressType;
        this.activateAddressType = activateAddressType;
        this.deactivateAddressType = deactivateAddressType;
    }

    @PostMapping
    public ResponseEntity<AddressTypeResponse> create(@Valid @RequestBody CreateAddressTypeRequest request) {
        AddressType created = createAddressType.create(
                new CreateAddressTypeCommand(request.getCode(), request.getName(), request.getDescription()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(AddressTypeResponse.from(created));
    }

    @GetMapping
    public List<AddressTypeResponse> list() {
        return listAddressTypes.listAll().stream().map(AddressTypeResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AddressTypeResponse get(@PathVariable UUID id) {
        return AddressTypeResponse.from(getAddressType.getById(id));
    }

    @PutMapping("/{id}")
    public AddressTypeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAddressTypeRequest request) {
        return AddressTypeResponse.from(updateAddressType.update(
                new UpdateAddressTypeCommand(id, request.getName(), request.getDescription())));
    }

    @PostMapping("/{id}/activate")
    public AddressTypeResponse activate(@PathVariable UUID id) {
        return AddressTypeResponse.from(activateAddressType.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    public AddressTypeResponse deactivate(@PathVariable UUID id) {
        return AddressTypeResponse.from(deactivateAddressType.deactivate(id));
    }
}
