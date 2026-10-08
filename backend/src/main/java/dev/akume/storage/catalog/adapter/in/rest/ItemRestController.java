package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.port.in.ActivateItemCommand;
import dev.akume.storage.catalog.application.port.in.ActivateItemUseCase;
import dev.akume.storage.catalog.application.port.in.CreateItemCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemUseCase;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemUseCase;
import dev.akume.storage.catalog.application.port.in.GetItemUseCase;
import dev.akume.storage.catalog.application.port.in.ListItemsUseCase;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataCommand;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataUseCase;
import dev.akume.storage.catalog.application.model.ItemReadView;
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
@RequestMapping("/api/items")
public class ItemRestController {

    private final CreateItemUseCase create;
    private final ListItemsUseCase list;
    private final GetItemUseCase get;
    private final UpdateItemMetadataUseCase updateMetadata;
    private final ReassignItemCategoryUseCase reassignCategory;
    private final ActivateItemUseCase activate;
    private final DeactivateItemUseCase deactivate;

    public ItemRestController(CreateItemUseCase create, ListItemsUseCase list, GetItemUseCase get,
            UpdateItemMetadataUseCase updateMetadata, ReassignItemCategoryUseCase reassignCategory,
            ActivateItemUseCase activate, DeactivateItemUseCase deactivate) {
        this.create = create;
        this.list = list;
        this.get = get;
        this.updateMetadata = updateMetadata;
        this.reassignCategory = reassignCategory;
        this.activate = activate;
        this.deactivate = deactivate;
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemReadView created = create.create(new CreateItemCommand(
                request.getName(), request.getDescription(), request.getItemCategoryId()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(ItemResponse.from(created));
    }

    @GetMapping
    public List<ItemResponse> list() {
        return list.listAll().stream().map(ItemResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable UUID id) {
        return ItemResponse.from(get.getById(id));
    }

    @PutMapping("/{id}")
    public ItemResponse updateMetadata(@PathVariable UUID id,
            @Valid @RequestBody UpdateItemMetadataRequest request) {
        return ItemResponse.from(updateMetadata.updateMetadata(new UpdateItemMetadataCommand(
                id, request.getName(), request.getDescription(), request.getExpectedVersion())));
    }

    @PutMapping("/{id}/category")
    public ItemResponse reassignCategory(@PathVariable UUID id,
            @Valid @RequestBody ReassignItemCategoryRequest request) {
        return ItemResponse.from(reassignCategory.reassignCategory(new ReassignItemCategoryCommand(
                id, request.getItemCategoryId(), request.getExpectedVersion())));
    }

    @PostMapping("/{id}/activate")
    public ItemResponse activate(@PathVariable UUID id, @Valid @RequestBody ItemLifecycleRequest request) {
        return ItemResponse.from(activate.activate(new ActivateItemCommand(id, request.getExpectedVersion())));
    }

    @PostMapping("/{id}/deactivate")
    public ItemResponse deactivate(@PathVariable UUID id, @Valid @RequestBody ItemLifecycleRequest request) {
        return ItemResponse.from(deactivate.deactivate(new DeactivateItemCommand(id, request.getExpectedVersion())));
    }
}
