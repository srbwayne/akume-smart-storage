package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.exception.ItemCategoryConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNameAlreadyExistsException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
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
import static org.mockito.Mockito.doReturn;
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

@WebMvcTest(ItemCategoryRestController.class)
class ItemCategoryRestControllerTest {

    private static final String COLLECTION = "/api/item-categories";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateItemCategoryUseCase create;
    @MockitoBean
    private ListItemCategoriesUseCase list;
    @MockitoBean
    private GetItemCategoryUseCase get;
    @MockitoBean
    private RenameItemCategoryUseCase rename;
    @MockitoBean
    private ActivateItemCategoryUseCase activate;
    @MockitoBean
    private DeactivateItemCategoryUseCase deactivate;

    private UUID id;

    @BeforeEach
    void setUp() {
        id = UUID.fromString("bdc50ed7-e475-493e-97b4-3a6d6b81fcf0");
    }

    @Test
    void createsWithOnlyAuthorizedFieldsAndReturnsLocationAndNormalizedResult() throws Exception {
        ItemCategory created = ItemCategory.reconstitute(id, "Café", true, 0);
        when(create.create(any(CreateItemCategoryCommand.class))).thenReturn(created);

        String body = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Café \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + COLLECTION + "/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Café"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(content().json("{\"id\":\"" + id + "\",\"name\":\"Café\",\"active\":true,\"version\":0}", true))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("canonical"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("normalized"));
        verify(create).create(new CreateItemCategoryCommand(" Café "));
    }

    @Test
    void rejectsMissingNullBlankAndWhitespaceOnlyCreateName() throws Exception {
        for (String body : List.of("null", "{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}")) {
            mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(create);
    }

    @Test
    void mapsCreateDuplicateAndRejectsUnknownOrMalformedBodies() throws Exception {
        when(create.create(any(CreateItemCategoryCommand.class)))
                .thenThrow(new ItemCategoryNameAlreadyExistsException("Cable"));
        String conflict = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cable\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_NAME_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();
        assertSafeError(conflict);

        mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cable\",\"active\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listsActiveAndInactiveCategoriesAndReturnsEmptyArray() throws Exception {
        when(list.listAll()).thenReturn(List.of(
                ItemCategory.reconstitute(id, "Cable", true, 0),
                ItemCategory.reconstitute(UUID.randomUUID(), "Power", false, 3)));
        String body = mvc.perform(get(COLLECTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[0].version").value(0))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("canonicalNameKey"));

        when(list.listAll()).thenReturn(List.of());
        mvc.perform(get(COLLECTION)).andExpect(status().isOk()).andExpect(content().json("[]", true));
    }

    @Test
    void getsExistingMissingAndMalformedIdentifiers() throws Exception {
        ItemCategory found = ItemCategory.reconstitute(id, "Cable", false, 7);
        UUID missingId = UUID.randomUUID();
        doReturn(found).when(get).getById(id);
        doThrow(new ItemCategoryNotFoundException(missingId)).when(get).getById(missingId);

        mvc.perform(get(COLLECTION + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(7));
        mvc.perform(get(COLLECTION + "/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_NOT_FOUND"));
        mvc.perform(get(COLLECTION + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void renamesUsingPathIdForwardsVersionAndMapsSemanticFailures() throws Exception {
        ItemCategory updated = ItemCategory.reconstitute(id, "Power", true, 14);
        UUID missingId = UUID.randomUUID();
        doReturn(updated).when(rename).rename(new RenameItemCategoryCommand(id, "Power", 3));
        doThrow(new ItemCategoryNameAlreadyExistsException("Power")).when(rename)
                .rename(new RenameItemCategoryCommand(missingId, "Power", 3));
        doThrow(new ItemCategoryConcurrentModificationException(id)).when(rename)
                .rename(new RenameItemCategoryCommand(id, "Stale", 2));
        doThrow(new ItemCategoryNotFoundException(missingId)).when(rename)
                .rename(new RenameItemCategoryCommand(missingId, "Missing", 3));

        mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Power\",\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Power"))
                .andExpect(jsonPath("$.version").value(14));
        verify(rename).rename(new RenameItemCategoryCommand(id, "Power", 3));
        mvc.perform(put(COLLECTION + "/{id}", missingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Power\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_NAME_ALREADY_EXISTS"));
        mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stale\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_CONCURRENT_MODIFICATION"));
        mvc.perform(put(COLLECTION + "/{id}", missingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\",\"expectedVersion\":3}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidRenameVersionNameAndUnknownFields() throws Exception {
        for (String body : List.of(
                "null",
                "{\"expectedVersion\":0}",
                "{\"name\":\"Power\"}",
                "{\"name\":\"Power\",\"expectedVersion\":null}",
                "{\"name\":\"Power\",\"expectedVersion\":-1}",
                "{\"name\":\" \",\"expectedVersion\":0}",
                "{\"name\":\"Power\",\"expectedVersion\":0,\"active\":false}")) {
            mvc.perform(put(COLLECTION + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(rename);
    }

    @Test
    void activatesAndForwardsExpectedVersionIncludingIdempotentResponse() throws Exception {
        doReturn(ItemCategory.reconstitute(id, "Cable", true, 9)).when(activate)
                .activate(new ActivateItemCategoryCommand(id, 8));
        doThrow(new ItemCategoryConcurrentModificationException(id)).when(activate)
                .activate(new ActivateItemCategoryCommand(id, 2));
        doThrow(new ItemCategoryNotFoundException(id)).when(activate)
                .activate(new ActivateItemCategoryCommand(id, 5));

        mvc.perform(post(COLLECTION + "/{id}/activate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(9));
        verify(activate).activate(new ActivateItemCategoryCommand(id, 8));
        mvc.perform(post(COLLECTION + "/{id}/activate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(post(COLLECTION + "/{id}/activate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatesAndForwardsExpectedVersionIncludingIdempotentResponse() throws Exception {
        doReturn(ItemCategory.reconstitute(id, "Cable", false, 10)).when(deactivate)
                .deactivate(new DeactivateItemCategoryCommand(id, 9));
        doThrow(new ItemCategoryConcurrentModificationException(id)).when(deactivate)
                .deactivate(new DeactivateItemCategoryCommand(id, 2));
        doThrow(new ItemCategoryNotFoundException(id)).when(deactivate)
                .deactivate(new DeactivateItemCategoryCommand(id, 5));

        mvc.perform(post(COLLECTION + "/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(10));
        verify(deactivate).deactivate(new DeactivateItemCategoryCommand(id, 9));
        mvc.perform(post(COLLECTION + "/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(post(COLLECTION + "/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingNegativeAndUnknownLifecycleRequestFields() throws Exception {
        for (String path : List.of("activate", "deactivate")) {
            for (String body : List.of(
                    "null", "{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":-1}",
                    "{\"expectedVersion\":0,\"active\":true}")) {
                mvc.perform(post(COLLECTION + "/{id}/" + path, id)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(activate, deactivate);
    }

    private static void assertSafeError(String body) {
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("uk_item_categories"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("SQL"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("PostgreSQL"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("Hibernate"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("Exception"));
    }
}
