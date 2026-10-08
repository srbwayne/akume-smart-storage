package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.exception.InactiveItemCategoryException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemNotFoundException;
import dev.akume.storage.catalog.application.model.ItemReadView;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemRestController.class)
class ItemRestControllerTest {

    private static final String COLLECTION = "/api/items";

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CreateItemUseCase create;
    @MockitoBean private ListItemsUseCase list;
    @MockitoBean private GetItemUseCase get;
    @MockitoBean private UpdateItemMetadataUseCase updateMetadata;
    @MockitoBean private ReassignItemCategoryUseCase reassignCategory;
    @MockitoBean private ActivateItemUseCase activate;
    @MockitoBean private DeactivateItemUseCase deactivate;

    private UUID id;
    private UUID categoryId;
    private UUID otherCategoryId;

    @BeforeEach
    void setUp() {
        id = UUID.fromString("80b24ea2-a9e2-4e98-b129-884bf2da4b6f");
        categoryId = UUID.fromString("c93596dc-c7e9-4667-a42a-b46c873f31ac");
        otherCategoryId = UUID.fromString("4c338e0e-66d8-4ee2-a99b-75679d1b5c9d");
    }

    @Test
    void createsWithAuthorizedFieldsAndReturnsAuthoritativeResponseAndLocation() throws Exception {
        ItemReadView created = item("Development board", "S3", categoryId, true, 0);
        when(create.create(any(CreateItemCommand.class))).thenReturn(created);

        mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Development board\",\"description\":\"S3\",\"itemCategoryId\":\"" + categoryId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + COLLECTION + "/" + id))
                .andExpect(content().json(responseJson(id, "Development board", "S3", categoryId, true, 0), true))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value("Category"))
                .andExpect(jsonPath("$.category.active").value(true));
        verify(create).create(new CreateItemCommand("Development board", "S3", categoryId));
    }

    @Test
    void permitsDuplicateNamesAndMapsCategoryEligibilityErrors() throws Exception {
        when(create.create(any(CreateItemCommand.class)))
                .thenReturn(item("Cable", null, categoryId, true, 0))
                .thenReturn(new ItemReadView(UUID.randomUUID(), "Cable", null, categoryId, true, 0,
                        new ItemReadView.CategorySummary(categoryId, "Category", true)));
        postCreate("Cable", categoryId).andExpect(status().isCreated());
        postCreate("Cable", categoryId).andExpect(status().isCreated());

        doThrow(new ItemCategoryNotFoundException(categoryId)).when(create)
                .create(new CreateItemCommand("Cable", null, categoryId));
        postCreate("Cable", categoryId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_NOT_FOUND"));
        doThrow(new InactiveItemCategoryException(categoryId)).when(create)
                .create(new CreateItemCommand("Cable", null, categoryId));
        postCreate("Cable", categoryId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_INACTIVE"));
    }

    @Test
    void rejectsInvalidCreatePayloadsWithoutCallingApplication() throws Exception {
        for (String body : List.of("null", "{}", "{\"name\":null,\"itemCategoryId\":\"" + categoryId + "\"}",
                "{\"name\":\"  \",\"itemCategoryId\":\"" + categoryId + "\"}",
                "{\"name\":\"X\"}", "{\"name\":\"X\",\"itemCategoryId\":null}",
                "{\"name\":\"X\",\"itemCategoryId\":\"not-a-uuid\"}",
                "{\"name\":\"X\",\"itemCategoryId\":\"" + categoryId + "\",\"active\":true}", "{")) {
            mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(create);
    }

    @Test
    void listsActiveAndInactiveItemsAndReturnsEmptyArray() throws Exception {
        when(list.listAll()).thenReturn(List.of(item("A", null, categoryId, true, 0),
                item("B", "old", otherCategoryId, false, 3)));
        mvc.perform(get(COLLECTION)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].itemCategoryId").value(otherCategoryId.toString()))
                .andExpect(jsonPath("$[1].version").value(3));
        when(list.listAll()).thenReturn(List.of());
        mvc.perform(get(COLLECTION)).andExpect(status().isOk()).andExpect(content().json("[]", true));
    }

    @Test
    void getsExistingMissingAndMalformedIdentifiers() throws Exception {
        when(get.getById(id)).thenReturn(item("Cable", null, categoryId, false, 2));
        when(get.getById(otherCategoryId)).thenThrow(new ItemNotFoundException(otherCategoryId));
        mvc.perform(get(COLLECTION + "/{id}", id)).andExpect(status().isOk())
                .andExpect(content().json(responseJson(id, "Cable", null, categoryId, false, 2), true));
        mvc.perform(get(COLLECTION + "/{id}", otherCategoryId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        mvc.perform(get(COLLECTION + "/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void updatesMetadataAndReassignsCategoryWithExpectedVersions() throws Exception {
        when(updateMetadata.updateMetadata(new UpdateItemMetadataCommand(id, "New name", null, 4)))
                .thenReturn(item("New name", null, categoryId, true, 5));
        mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\",\"description\":null,\"expectedVersion\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.description").doesNotExist());
        verify(updateMetadata).updateMetadata(new UpdateItemMetadataCommand(id, "New name", null, 4));

        when(reassignCategory.reassignCategory(new ReassignItemCategoryCommand(id, otherCategoryId, 5)))
                .thenReturn(item("New name", null, otherCategoryId, true, 6));
        mvc.perform(put(COLLECTION + "/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCategoryId").value(otherCategoryId.toString()))
                .andExpect(jsonPath("$.category.id").value(otherCategoryId.toString()))
                .andExpect(jsonPath("$.version").value(6));
        verify(reassignCategory).reassignCategory(new ReassignItemCategoryCommand(id, otherCategoryId, 5));
    }

    @Test
    void lifecycleForwardsVersionAndReturnsAuthoritativeNoOpOrChangedState() throws Exception {
        when(activate.activate(new ActivateItemCommand(id, 6))).thenReturn(item("Cable", null, categoryId, true, 6));
        mvc.perform(post(COLLECTION + "/{id}/activate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(6));
        when(deactivate.deactivate(new DeactivateItemCommand(id, 6)))
                .thenReturn(item("Cable", null, categoryId, false, 7));
        mvc.perform(post(COLLECTION + "/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(7));
        verify(activate).activate(new ActivateItemCommand(id, 6));
        verify(deactivate).deactivate(new DeactivateItemCommand(id, 6));
    }

    @Test
    void mapsStaleAndCategoryAssignmentFailuresToSafeErrors() throws Exception {
        doThrow(new ItemConcurrentModificationException(id)).when(updateMetadata)
                .updateMetadata(new UpdateItemMetadataCommand(id, "Name", null, 2));
        mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Name\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.message").value("Item was changed concurrently. Reload and retry."))
                .andExpect(jsonPath("$.path").value(COLLECTION + "/" + id));
        doThrow(new ItemCategoryNotFoundException(otherCategoryId)).when(reassignCategory)
                .reassignCategory(new ReassignItemCategoryCommand(id, otherCategoryId, 2));
        mvc.perform(put(COLLECTION + "/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":2}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_CATEGORY_NOT_FOUND"));
        doThrow(new InactiveItemCategoryException(otherCategoryId)).when(reassignCategory)
                .reassignCategory(new ReassignItemCategoryCommand(id, otherCategoryId, 2));
        mvc.perform(put(COLLECTION + "/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_CATEGORY_INACTIVE"));

        doThrow(new IllegalArgumentException("Invalid Item domain input")).when(updateMetadata)
                .updateMetadata(new UpdateItemMetadataCommand(id, "Name", null, 2));
        mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Name\",\"expectedVersion\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsMissingNegativeAndUnknownMutationFieldsAndHasNoDeleteEndpoint() throws Exception {
        List<String> badMetadata = List.of("{}", "{\"name\":\"X\"}",
                "{\"name\":\"X\",\"expectedVersion\":null}", "{\"name\":\"X\",\"expectedVersion\":-1}",
                "{\"name\":\"X\",\"expectedVersion\":0,\"active\":true}");
        for (String body : badMetadata) {
            mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"itemCategoryId\":\"" + otherCategoryId + "\"}",
                "{\"itemCategoryId\":\"invalid-uuid\",\"expectedVersion\":0}",
                "{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":-1}",
                "{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":0,\"active\":true}")) {
            mvc.perform(put(COLLECTION + "/{id}/category", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String action : List.of("activate", "deactivate")) {
            for (String body : List.of("{}", "{\"expectedVersion\":-1}",
                    "{\"expectedVersion\":0,\"active\":true}")) {
                mvc.perform(post(COLLECTION + "/{id}/" + action, id)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(COLLECTION + "/{id}", id))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(updateMetadata, reassignCategory, activate, deactivate);
    }

    @Test
    void rejectsFractionalExpectedVersionForEveryMutationRequest() throws Exception {
        mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"expectedVersion\":1.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(put(COLLECTION + "/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":1.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        for (String action : List.of("activate", "deactivate")) {
            mvc.perform(post(COLLECTION + "/{id}/" + action, id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":1.5}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(updateMetadata, reassignCategory, activate, deactivate);
    }

    @Test
    void rejectsOutOfRangeAndStringExpectedVersionForEveryMutationRequest() throws Exception {
        for (String version : List.of("2147483648", "\"1\"")) {
            mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"expectedVersion\":" + version + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mvc.perform(put(COLLECTION + "/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"itemCategoryId\":\"" + otherCategoryId + "\",\"expectedVersion\":" + version + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            for (String action : List.of("activate", "deactivate")) {
                mvc.perform(post(COLLECTION + "/{id}/" + action, id).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":" + version + "}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            }
        }
        verifyNoInteractions(updateMetadata, reassignCategory, activate, deactivate);
    }

    private org.springframework.test.web.servlet.ResultActions postCreate(String name, UUID category) throws Exception {
        return mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"itemCategoryId\":\"" + category + "\"}"));
    }

    private ItemReadView item(String name, String description, UUID category, boolean active, int version) {
        return new ItemReadView(id, name, description, category, active, version,
                new ItemReadView.CategorySummary(category, "Category", true));
    }

    private String responseJson(UUID itemId, String name, String description, UUID category, boolean active, int version) {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        return "{\"id\":\"" + itemId + "\",\"name\":\"" + name + "\",\"description\":" + descriptionJson
                + ",\"itemCategoryId\":\"" + category + "\",\"active\":" + active + ",\"version\":" + version
                + ",\"category\":{\"id\":\"" + category + "\",\"name\":\"Category\",\"active\":true}}";
    }
}
