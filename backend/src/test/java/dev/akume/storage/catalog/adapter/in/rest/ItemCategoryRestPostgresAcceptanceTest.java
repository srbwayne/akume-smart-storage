package dev.akume.storage.catalog.adapter.in.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemCategoryRestPostgresAcceptanceTest {

    private static final String COLLECTION = "/api/item-categories";
    private static final String NAME_PREFIX = "M304 acceptance ";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearAcceptanceData() {
        jdbc.update("DELETE FROM item_categories WHERE name LIKE ?", NAME_PREFIX + "%");
    }

    @Test
    void completesCreateRenameLifecycleIdempotencyAndStaleVersionThroughPostgres() throws Exception {
        MvcResult created = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  M304 acceptance Café  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("M304 acceptance Café"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        UUID id = extractId(created);
        String persistedName = jdbc.queryForObject("SELECT name FROM item_categories WHERE id = ?", String.class, id);
        assertEquals("M304 acceptance Café", persistedName);

        MvcResult renamed = mvc.perform(put(COLLECTION + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M304 acceptance Renamed\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("M304 acceptance Renamed"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();
        int versionAfterRename = versionFrom(renamed);
        assertEquals("M304 acceptance Renamed", jdbc.queryForObject(
                "SELECT name FROM item_categories WHERE id = ?", String.class, id));

        MvcResult deactivated = mvc.perform(post(COLLECTION + "/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + versionAfterRename + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(2))
                .andReturn();
        int inactiveVersion = versionFrom(deactivated);
        mvc.perform(post(COLLECTION + "/" + id + "/deactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + inactiveVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(inactiveVersion));
        assertFalse(jdbc.queryForObject("SELECT active FROM item_categories WHERE id = ?", Boolean.class, id));

        MvcResult activated = mvc.perform(post(COLLECTION + "/" + id + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + inactiveVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(3))
                .andReturn();
        int activeVersion = versionFrom(activated);
        assertTrue(jdbc.queryForObject("SELECT active FROM item_categories WHERE id = ?", Boolean.class, id));
        mvc.perform(post(COLLECTION + "/" + id + "/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + activeVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(activeVersion));

        mvc.perform(put(COLLECTION + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M304 acceptance Stale\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_CONCURRENT_MODIFICATION"));
        assertEquals("M304 acceptance Renamed", jdbc.queryForObject(
                "SELECT name FROM item_categories WHERE id = ?", String.class, id));
        assertEquals(activeVersion, jdbc.queryForObject(
                "SELECT version FROM item_categories WHERE id = ?", Integer.class, id));
    }

    @Test
    void duplicateCanonicalNameReturnsSafeConflictAndBothLifecycleStatesRemainListed() throws Exception {
        mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M304 acceptance Duplicate\"}"))
                .andExpect(status().isCreated());
        String conflict = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"m304 ACCEPTANCE duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_NAME_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(conflict.contains("uk_item_categories"));
        assertFalse(conflict.contains("SQL"));
        assertFalse(conflict.contains("PostgreSQL"));
        assertFalse(conflict.contains("Hibernate"));
        assertFalse(conflict.contains("Exception"));

        UUID id = extractId(mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M304 acceptance Inactive\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post(COLLECTION + "/" + id + "/deactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get(COLLECTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "' && @.active == false)]").exists());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM item_categories WHERE name LIKE ?", Integer.class,
                NAME_PREFIX + "%") >= 2);
    }

    @Test
    void createsAndGetsCategoryThroughPostgres() throws Exception {
        MvcResult created = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  M304 acceptance Café create-get  \"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = extractId(created);

        String expected = "{\"id\":\"" + id + "\",\"name\":\"M304 acceptance Café create-get\","
                + "\"active\":true,\"version\":0}";
        mvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(content().json(expected, true));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_categories WHERE id = ? AND name = ? AND active = TRUE AND version = 0",
                Integer.class, id, "M304 acceptance Café create-get"));
    }

    @Test
    void rejectsStaleIdempotentLifecycleCommandWithoutChangingPostgresState() throws Exception {
        String name = "M304 acceptance Stale lifecycle";
        MvcResult created = mvc.perform(post(COLLECTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        UUID id = extractId(created);
        int initialVersion = versionFrom(created);

        MvcResult deactivated = mvc.perform(post(COLLECTION + "/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + initialVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();
        int updatedVersion = versionFrom(deactivated);
        assertEquals(1, updatedVersion);

        mvc.perform(post(COLLECTION + "/" + id + "/deactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + initialVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CATEGORY_CONCURRENT_MODIFICATION"));

        mvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":\"" + id + "\",\"name\":\"" + name
                        + "\",\"active\":false,\"version\":" + updatedVersion + "}", true));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_categories WHERE id = ? AND name = ? AND active = FALSE AND version = ?",
                Integer.class, id, name, updatedVersion));
    }

    private static UUID extractId(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "create response should contain an id");
        return UUID.fromString(matcher.group(1));
    }

    private static int versionFrom(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\"version\":(\\d+)")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "response should contain a version");
        return Integer.parseInt(matcher.group(1));
    }
}
