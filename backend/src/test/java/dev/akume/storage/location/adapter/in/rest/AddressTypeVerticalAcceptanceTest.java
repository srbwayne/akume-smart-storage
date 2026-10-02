package dev.akume.storage.location.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AddressTypeVerticalAcceptanceTest {

    private static final String CODE = "ACCEPTANCE_DRAWER";
    private static final String COLLECTION = "/api/address-types";

    @Autowired
    private MockMvc mvc;

    @Test
    void completesCreateGetListUpdateDeactivateAndActivateAcrossTheRealStack() throws Exception {
        MvcResult createResult = mvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ACCEPTANCE_DRAWER\",\"name\":\"Acceptance Drawer\",\"description\":\"Initial\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.name").value("Acceptance Drawer"))
                .andExpect(jsonPath("$.description").value("Initial"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        assertNotNull(createResult.getResponse().getHeader("Location"));
        UUID createdId = extractId(createResult);
        String id = createdId.toString();

        mvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.description").value("Initial"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(get(COLLECTION))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":\"" + CODE + "\"")));

        mvc.perform(put(COLLECTION + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Acceptance Drawer\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.name").value("Updated Acceptance Drawer"))
                .andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Acceptance Drawer"))
                .andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(post(COLLECTION + "/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post(COLLECTION + "/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void mapsDuplicateCodeToSafeSemanticConflict() throws Exception {
        mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ACCEPTANCE_DRAWER\",\"name\":\"Acceptance Drawer\",\"description\":\"Initial\"}"))
                .andExpect(status().isCreated());

        String duplicateBody = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ACCEPTANCE_DRAWER\",\"name\":\"Another Drawer\"}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(duplicateBody.contains("ADDRESS_TYPE_CODE_ALREADY_EXISTS"));
        assertTrue(duplicateBody.contains("Address type code already exists"));
        assertFalse(duplicateBody.contains("uk_address_types_code"));
        assertFalse(duplicateBody.contains("SQL"));
        assertFalse(duplicateBody.contains("Hibernate"));
        assertFalse(duplicateBody.contains("PostgreSQL"));
        assertFalse(duplicateBody.contains("stackTrace"));
        assertFalse(duplicateBody.contains("Exception"));
    }

    @Test
    void mapsMissingAndMalformedIdentifiersToClientErrors() throws Exception {
        mvc.perform(get(COLLECTION + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get(COLLECTION + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private static UUID extractId(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "create response should contain an id");
        return UUID.fromString(matcher.group(1));
    }
}
