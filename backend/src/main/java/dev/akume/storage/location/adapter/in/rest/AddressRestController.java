package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.port.in.ActivateAddressCommand;
import dev.akume.storage.location.application.port.in.ActivateAddressUseCase;
import dev.akume.storage.location.application.port.in.CreateAddressCommand;
import dev.akume.storage.location.application.port.in.CreateAddressUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressCommand;
import dev.akume.storage.location.application.port.in.DeactivateAddressUseCase;
import dev.akume.storage.location.application.port.in.GetAddressUseCase;
import dev.akume.storage.location.application.port.in.ListAddressChildrenUseCase;
import dev.akume.storage.location.application.port.in.ListAddressRootsUseCase;
import dev.akume.storage.location.application.port.in.ListAddressesUseCase;
import dev.akume.storage.location.application.port.in.MoveAddressCommand;
import dev.akume.storage.location.application.port.in.MoveAddressUseCase;
import dev.akume.storage.location.application.port.in.RenameAddressCommand;
import dev.akume.storage.location.application.port.in.RenameAddressUseCase;
import dev.akume.storage.location.domain.model.Address;
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
@RequestMapping("/api/addresses")
public class AddressRestController {
    private final CreateAddressUseCase create;
    private final GetAddressUseCase get;
    private final ListAddressesUseCase list;
    private final ListAddressRootsUseCase roots;
    private final ListAddressChildrenUseCase children;
    private final RenameAddressUseCase rename;
    private final MoveAddressUseCase move;
    private final ActivateAddressUseCase activate;
    private final DeactivateAddressUseCase deactivate;

    public AddressRestController(CreateAddressUseCase create, GetAddressUseCase get,
            ListAddressesUseCase list, ListAddressRootsUseCase roots,
            ListAddressChildrenUseCase children, RenameAddressUseCase rename,
            MoveAddressUseCase move, ActivateAddressUseCase activate,
            DeactivateAddressUseCase deactivate) {
        this.create = create;
        this.get = get;
        this.list = list;
        this.roots = roots;
        this.children = children;
        this.rename = rename;
        this.move = move;
        this.activate = activate;
        this.deactivate = deactivate;
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@Valid @RequestBody CreateAddressRequest request) {
        Address created = create.create(new CreateAddressCommand(
                request.getName(), request.getAddressTypeId(), request.getParentId()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(AddressResponse.from(created));
    }

    @GetMapping
    public List<AddressResponse> list() {
        return list.listAll().stream().map(AddressResponse::from).toList();
    }

    @GetMapping("/roots")
    public List<AddressResponse> roots() {
        return roots.listRoots().stream().map(AddressResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AddressResponse get(@PathVariable UUID id) {
        return AddressResponse.from(get.getById(id));
    }

    @GetMapping("/{id}/children")
    public List<AddressResponse> children(@PathVariable UUID id) {
        return children.listDirectChildren(id).stream().map(AddressResponse::from).toList();
    }

    @PutMapping("/{id}")
    public AddressResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameAddressRequest request) {
        return AddressResponse.from(rename.rename(
                new RenameAddressCommand(id, request.getName(), request.getExpectedVersion())));
    }

    @PostMapping("/{id}/move")
    public AddressResponse move(@PathVariable UUID id, @Valid @RequestBody MoveAddressRequest request) {
        return AddressResponse.from(move.move(
                new MoveAddressCommand(id, request.getNewParentId(), request.getExpectedVersion())));
    }

    @PostMapping("/{id}/activate")
    public AddressResponse activate(@PathVariable UUID id, @Valid @RequestBody AddressLifecycleRequest request) {
        return AddressResponse.from(activate.activate(new ActivateAddressCommand(id, request.getExpectedVersion())));
    }

    @PostMapping("/{id}/deactivate")
    public AddressResponse deactivate(@PathVariable UUID id, @Valid @RequestBody AddressLifecycleRequest request) {
        return AddressResponse.from(deactivate.deactivate(new DeactivateAddressCommand(id, request.getExpectedVersion())));
    }
}
