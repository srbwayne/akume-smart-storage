package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.port.in.ActivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.ActivateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.CreateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.GetItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.ListItemCategoriesUseCase;
import dev.akume.storage.catalog.application.port.in.RenameItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.RenameItemCategoryUseCase;
import dev.akume.storage.catalog.domain.model.ItemCategory;
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
@RequestMapping("/api/item-categories")
public class ItemCategoryRestController {

    private final CreateItemCategoryUseCase create;
    private final ListItemCategoriesUseCase list;
    private final GetItemCategoryUseCase get;
    private final RenameItemCategoryUseCase rename;
    private final ActivateItemCategoryUseCase activate;
    private final DeactivateItemCategoryUseCase deactivate;

    public ItemCategoryRestController(
            CreateItemCategoryUseCase create,
            ListItemCategoriesUseCase list,
            GetItemCategoryUseCase get,
            RenameItemCategoryUseCase rename,
            ActivateItemCategoryUseCase activate,
            DeactivateItemCategoryUseCase deactivate) {
        this.create = create;
        this.list = list;
        this.get = get;
        this.rename = rename;
        this.activate = activate;
        this.deactivate = deactivate;
    }

    @PostMapping
    public ResponseEntity<ItemCategoryResponse> create(@Valid @RequestBody CreateItemCategoryRequest request) {
        ItemCategory created = create.create(new CreateItemCategoryCommand(request.getName()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(ItemCategoryResponse.from(created));
    }

    @GetMapping
    public List<ItemCategoryResponse> list() {
        return list.listAll().stream().map(ItemCategoryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ItemCategoryResponse get(@PathVariable UUID id) {
        return ItemCategoryResponse.from(get.getById(id));
    }

    @PutMapping("/{id}")
    public ItemCategoryResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameItemCategoryRequest request) {
        return ItemCategoryResponse.from(rename.rename(
                new RenameItemCategoryCommand(id, request.getName(), request.getExpectedVersion())));
    }

    @PostMapping("/{id}/activate")
    public ItemCategoryResponse activate(
            @PathVariable UUID id, @Valid @RequestBody ItemCategoryLifecycleRequest request) {
        return ItemCategoryResponse.from(activate.activate(
                new ActivateItemCategoryCommand(id, request.getExpectedVersion())));
    }

    @PostMapping("/{id}/deactivate")
    public ItemCategoryResponse deactivate(
            @PathVariable UUID id, @Valid @RequestBody ItemCategoryLifecycleRequest request) {
        return ItemCategoryResponse.from(deactivate.deactivate(
                new DeactivateItemCategoryCommand(id, request.getExpectedVersion())));
    }
}
