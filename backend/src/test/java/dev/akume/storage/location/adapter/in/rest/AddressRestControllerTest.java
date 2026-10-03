package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.exception.AddressAlreadyExistsException;
import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressCycleDetectedException;
import dev.akume.storage.location.application.exception.AddressHasActiveChildrenException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.exception.SerializableOperationConflictException;
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
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
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

@WebMvcTest(AddressRestController.class)
class AddressRestControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CreateAddressUseCase create;
    @MockitoBean private GetAddressUseCase get;
    @MockitoBean private ListAddressesUseCase list;
    @MockitoBean private ListAddressRootsUseCase roots;
    @MockitoBean private ListAddressChildrenUseCase children;
    @MockitoBean private RenameAddressUseCase rename;
    @MockitoBean private MoveAddressUseCase move;
    @MockitoBean private ActivateAddressUseCase activate;
    @MockitoBean private DeactivateAddressUseCase deactivate;

    private UUID id;
    private UUID typeId;
    private UUID parentId;
    private Address address;

    @BeforeEach
    void setUp() {
        id = UUID.fromString("bdc50ed7-e475-493e-97b4-3a6d6b81fcf0");
        typeId = UUID.fromString("217fbb83-7ce1-4cf6-8b88-c61667396898");
        parentId = UUID.fromString("1c17f793-6401-4ed7-bf64-634873145262");
        address = Address.reconstitute(id, "Drawer 01", "drawer 01", typeId, parentId, true, 3);
    }

    @Test
    void createsWithLocationAndOnlyPublicRepresentation() throws Exception {
        when(create.create(any())).thenReturn(address);
        mvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drawer 01\",\"addressTypeId\":\"" + typeId + "\",\"parentId\":\"" + parentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/addresses/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Drawer 01"))
                .andExpect(jsonPath("$.addressTypeId").value(typeId.toString()))
                .andExpect(jsonPath("$.parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.normalizedNameKey").doesNotExist());
        verify(create).create(new CreateAddressCommand("Drawer 01", typeId, parentId));
    }

    @Test
    void listRootsGetAndChildrenMapDomainResults() throws Exception {
        when(list.listAll()).thenReturn(List.of(address));
        when(roots.listRoots()).thenReturn(List.of(address));
        when(get.getById(id)).thenReturn(address);
        when(children.listDirectChildren(id)).thenReturn(List.of());
        mvc.perform(get("/api/addresses")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(get("/api/addresses/roots")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(get("/api/addresses/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(get("/api/addresses/{id}/children", id)).andExpect(status().isOk()).andExpect(content().json("[]"));
        when(children.listDirectChildren(parentId)).thenThrow(new AddressNotFoundException(parentId));
        mvc.perform(get("/api/addresses/{id}/children", parentId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        when(list.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/addresses")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void renameMapsOnlyEditableNameAndExpectedVersion() throws Exception {
        when(rename.rename(any())).thenReturn(address);
        mvc.perform(put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drawer 02\",\"expectedVersion\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        verify(rename).rename(new RenameAddressCommand(id, "Drawer 02", 3));
    }

    @Test
    void moveRequiresPropertyPresenceButAcceptsExplicitNull() throws Exception {
        when(move.move(any())).thenReturn(address);
        mvc.perform(post("/api/addresses/{id}/move", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":null,\"expectedVersion\":3}"))
                .andExpect(status().isOk());
        verify(move).move(new MoveAddressCommand(id, null, 3));
        mvc.perform(post("/api/addresses/{id}/move", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void lifecycleEndpointsReturnAddressForSuccessfulAndNoOpResults() throws Exception {
        when(activate.activate(any())).thenReturn(address);
        when(deactivate.deactivate(any())).thenReturn(Address.reconstitute(id, "Drawer 01", "drawer 01", typeId, parentId, false, 4));
        mvc.perform(post("/api/addresses/{id}/activate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/addresses/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        verify(activate).activate(new ActivateAddressCommand(id, 3));
        verify(deactivate).deactivate(new DeactivateAddressCommand(id, 3));
    }

    @Test
    void rejectsInvalidShapesUnknownPropertiesAndMalformedIds() throws Exception {
        String type = "\"addressTypeId\":\"" + typeId + "\"";
        mvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"," + type + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Drawer\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Drawer\"," + type + ",\"active\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Drawer\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Drawer\",\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Drawer\",\"expectedVersion\":1,\"parentId\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/move", id).contentType(MediaType.APPLICATION_JSON).content("{\"newParentId\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/move", id).contentType(MediaType.APPLICATION_JSON).content("{\"newParentId\":null,\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/activate", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"force\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses/{id}/activate", id).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/addresses/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(create, rename, move, activate, deactivate);
    }

    @Test
    void mapsSemanticFailuresToSafeStableErrors() throws Exception {
        assertError(() -> doThrow(new AddressNotFoundException(id)).when(get).getById(id),
                get("/api/addresses/{id}", id), 404, "ADDRESS_NOT_FOUND", "Address was not found.");
        assertError(() -> doThrow(new AddressTypeNotFoundException(typeId)).when(create).create(any()),
                post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"addressTypeId\":\"" + typeId + "\"}"),
                404, "ADDRESS_TYPE_NOT_FOUND", "Address type was not found.");
        assertError(() -> doThrow(new InactiveAddressTypeException(typeId)).when(create).create(any()),
                post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"addressTypeId\":\"" + typeId + "\"}"),
                409, "ADDRESS_TYPE_INACTIVE", "Address type is inactive.");
        assertError(() -> doThrow(new InactiveAddressParentException(parentId)).when(create).create(any()),
                post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"addressTypeId\":\"" + typeId + "\",\"parentId\":\"" + parentId + "\"}"),
                409, "ADDRESS_PARENT_INACTIVE", "An active Address requires an active parent.");
        assertError(() -> doThrow(new AddressSiblingNameAlreadyExistsException("secret")).when(rename).rename(any()),
                put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"expectedVersion\":1}"),
                409, "ADDRESS_SIBLING_NAME_ALREADY_EXISTS", "An Address with this sibling name already exists.");
        assertError(() -> doThrow(new AddressCycleDetectedException(id, parentId)).when(move).move(any()),
                post("/api/addresses/{id}/move", id).contentType(MediaType.APPLICATION_JSON).content("{\"newParentId\":\"" + parentId + "\",\"expectedVersion\":1}"),
                409, "ADDRESS_CYCLE_DETECTED", "The move would create a hierarchy cycle.");
        assertError(() -> doThrow(new AddressHasActiveChildrenException(id)).when(deactivate).deactivate(any()),
                post("/api/addresses/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"),
                409, "ADDRESS_HAS_ACTIVE_CHILDREN", "Address has active direct children.");
        assertError(() -> doThrow(new AddressConcurrentModificationException(id)).when(rename).rename(any()),
                put("/api/addresses/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"expectedVersion\":1}"),
                409, "ADDRESS_CONCURRENT_MODIFICATION", "Address was changed concurrently. Reload and retry.");
        assertError(() -> doThrow(new SerializableOperationConflictException(new IllegalStateException("secret SQLSTATE 40001")))
                        .when(move).move(any()),
                post("/api/addresses/{id}/move", id).contentType(MediaType.APPLICATION_JSON).content("{\"newParentId\":null,\"expectedVersion\":1}"),
                409, "ADDRESS_CONCURRENT_MODIFICATION", "Address was changed concurrently. Reload and retry.");
        assertError(() -> doThrow(new AddressAlreadyExistsException(id)).when(create).create(any()),
                post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"addressTypeId\":\"" + typeId + "\"}"),
                409, "ADDRESS_ALREADY_EXISTS", "Address already exists.");
    }

    private void assertError(Runnable stub, org.springframework.test.web.servlet.RequestBuilder request,
            int status, String code, String message) throws Exception {
        stub.run();
        mvc.perform(request).andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQLSTATE"))));
    }
}
