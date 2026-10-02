package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.in.ActivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.CreateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.CreateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.GetAddressTypeUseCase;
import dev.akume.storage.location.application.port.in.ListAddressTypesUseCase;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeCommand;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeUseCase;
import dev.akume.storage.location.domain.exception.AddressTypeCodeAlreadyExistsException;
import dev.akume.storage.location.domain.model.AddressType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressTypeRestController.class)
class AddressTypeRestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateAddressTypeUseCase createAddressType;
    @MockitoBean
    private GetAddressTypeUseCase getAddressType;
    @MockitoBean
    private ListAddressTypesUseCase listAddressTypes;
    @MockitoBean
    private UpdateAddressTypeUseCase updateAddressType;
    @MockitoBean
    private ActivateAddressTypeUseCase activateAddressType;
    @MockitoBean
    private DeactivateAddressTypeUseCase deactivateAddressType;

    private UUID id;

    @BeforeEach
    void setUp() {
        id = UUID.fromString("bdc50ed7-e475-493e-97b4-3a6d6b81fcf0");
    }

    @Test
    void createsAndReturnsLocationAndDomainFields() throws Exception {
        AddressType created = AddressType.create("DRAWER", "Drawer", "Storage drawer");
        when(createAddressType.create(any(CreateAddressTypeCommand.class))).thenReturn(created);

        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DRAWER\",\"name\":\"Drawer\",\"description\":\"Storage drawer\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/address-types/" + created.id()))
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.code").value("DRAWER"))
                .andExpect(jsonPath("$.name").value("Drawer"))
                .andExpect(jsonPath("$.description").value("Storage drawer"))
                .andExpect(jsonPath("$.active").value(true));
        verify(createAddressType).create(new CreateAddressTypeCommand("DRAWER", "Drawer", "Storage drawer"));
    }

    @Test
    void rejectsMissingAndBlankCreateFields() throws Exception {
        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drawer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"   \",\"name\":\"Drawer\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DRAWER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DRAWER\",\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownCreateFieldsBeforeInvokingUseCase() throws Exception {
        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DRAWER\",\"name\":\"Drawer\",\"active\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createAddressType);
    }

    @Test
    void mapsDuplicateCodeToSafeConflictResponse() throws Exception {
        when(createAddressType.create(any(CreateAddressTypeCommand.class)))
                .thenThrow(new AddressTypeCodeAlreadyExistsException("DRAWER"));

        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DRAWER\",\"name\":\"Drawer\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ADDRESS_TYPE_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Address type code already exists"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("constraint"))));
    }

    @Test
    void getsExistingAndMapsMissingAndMalformedIds() throws Exception {
        AddressType found = AddressType.reconstitute(id, "DRAWER", "Drawer", null, false);
        UUID missingId = UUID.randomUUID();
        doReturn(found).when(getAddressType).getById(id);
        doThrow(new AddressTypeNotFoundException(missingId)).when(getAddressType).getById(missingId);

        mvc.perform(get("/api/address-types/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/address-types/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_TYPE_NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("persistence"))));
        mvc.perform(get("/api/address-types/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listsActiveAndInactiveValuesAndReturnsEmptyArray() throws Exception {
        when(listAddressTypes.listAll()).thenReturn(List.of(
                AddressType.reconstitute(id, "DRAWER", "Drawer", null, true),
                AddressType.reconstitute(UUID.randomUUID(), "BIN", "Bin", "Parts", false)));

        mvc.perform(get("/api/address-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false));

        when(listAddressTypes.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/address-types"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void updatesAllowedDetailsAndRejectsInvalidOrUnknownFields() throws Exception {
        AddressType updated = AddressType.reconstitute(id, "DRAWER", "Updated Drawer", "Updated", false);
        UUID missingId = UUID.randomUUID();
        doReturn(updated).when(updateAddressType).update(new UpdateAddressTypeCommand(id, "Updated Drawer", "Updated"));
        doThrow(new AddressTypeNotFoundException(missingId)).when(updateAddressType)
                .update(new UpdateAddressTypeCommand(missingId, "Drawer", null));

        mvc.perform(put("/api/address-types/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Drawer\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DRAWER"))
                .andExpect(jsonPath("$.active").value(false));
        verify(updateAddressType).update(new UpdateAddressTypeCommand(id, "Updated Drawer", "Updated"));
        mvc.perform(put("/api/address-types/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/address-types/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drawer\",\"code\":\"MUTATION-NOT-ALLOWED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("MUTATION-NOT-ALLOWED"))));
        mvc.perform(put("/api/address-types/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drawer\",\"active\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/address-types/{id}", missingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drawer\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activationAndDeactivationReturnStateAndMapMissingIds() throws Exception {
        UUID missingId = UUID.randomUUID();
        doReturn(AddressType.reconstitute(id, "DRAWER", "Drawer", null, true))
                .when(activateAddressType).activate(id);
        doReturn(AddressType.reconstitute(id, "DRAWER", "Drawer", null, false))
                .when(deactivateAddressType).deactivate(id);
        doThrow(new AddressTypeNotFoundException(missingId)).when(activateAddressType).activate(missingId);
        doThrow(new AddressTypeNotFoundException(missingId)).when(deactivateAddressType).deactivate(missingId);

        mvc.perform(post("/api/address-types/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/address-types/{id}/activate", id))
                .andExpect(status().isOk());
        mvc.perform(post("/api/address-types/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/address-types/{id}/deactivate", id))
                .andExpect(status().isOk());
        mvc.perform(post("/api/address-types/{id}/activate", missingId))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/address-types/{id}/deactivate", missingId))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestBodiesDoNotExposeValidationInternals() throws Exception {
        mvc.perform(post("/api/address-types").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request is malformed or contains an unknown property"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("stackTrace"))));
    }
}
