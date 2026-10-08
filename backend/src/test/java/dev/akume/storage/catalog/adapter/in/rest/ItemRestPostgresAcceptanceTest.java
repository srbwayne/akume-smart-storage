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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemRestPostgresAcceptanceTest {

    private static final String ITEMS = "/api/items";
    private static final String CATEGORIES = "/api/item-categories";
    private static final String PREFIX = "M404 REST ";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearAcceptanceData() {
        jdbc.update("DELETE FROM items WHERE item_category_id IN "
                + "(SELECT id FROM item_categories WHERE name LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM item_categories WHERE name LIKE ?", PREFIX + "%");
    }

    @Test
    void createsAndGetsItemWithJoinedCategoryProjectionThroughPostgres() throws Exception {
        UUID categoryId = createCategory("Create Get");
        MvcResult created = createItem(" " + PREFIX + "ESP32-S3 board ", " development kit ", categoryId)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value(PREFIX + "ESP32-S3 board"))
                .andExpect(jsonPath("$.description").value("development kit"))
                .andExpect(jsonPath("$.itemCategoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value(PREFIX + "Create Get"))
                .andExpect(jsonPath("$.category.active").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        UUID itemId = idFrom(created);
        assertEquals("http://localhost" + ITEMS + "/" + itemId,
                created.getResponse().getHeader("Location"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM items WHERE id = ? AND name = ? AND item_category_id = ? AND active = TRUE AND version = 0",
                Integer.class, itemId, PREFIX + "ESP32-S3 board", categoryId));

        String expected = "{\"id\":\"" + itemId + "\",\"name\":\"" + PREFIX + "ESP32-S3 board\","
                + "\"description\":\"development kit\",\"itemCategoryId\":\"" + categoryId + "\","
                + "\"active\":true,\"version\":0,\"category\":{\"id\":\"" + categoryId + "\","
                + "\"name\":\"" + PREFIX + "Create Get\",\"active\":true}}";
        mvc.perform(get(ITEMS + "/" + itemId))
                .andExpect(status().isOk())
                .andExpect(content().json(expected, true));
    }

    @Test
    void listContainsActiveAndInactiveItemsWithCurrentCategorySummaries() throws Exception {
        UUID categoryId = createCategory("List");
        UUID activeId = idFrom(createItem(PREFIX + "List active", null, categoryId).andReturn());
        UUID inactiveId = idFrom(createItem(PREFIX + "List inactive", null, categoryId).andReturn());
        mvc.perform(post(ITEMS + "/" + inactiveId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCategoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()));

        mvc.perform(get(ITEMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + activeId + "' && @.active == true)]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + inactiveId + "' && @.active == false)]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + activeId + "')].category.id").value(categoryId.toString()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE name LIKE ?", Integer.class,
                PREFIX + "List%"));
    }

    @Test
    void metadataReassignmentAndLifecycleResponsesUsePersistedProjectionAndVersion() throws Exception {
        UUID firstCategory = createCategory("Metadata source");
        UUID secondCategory = createCategory("Metadata target");
        UUID itemId = idFrom(createItem(PREFIX + "Metadata item", "Initial", firstCategory).andReturn());

        mvc.perform(put(ITEMS + "/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated item\",\"description\":\"Updated detail\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated item"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.itemCategoryId").value(firstCategory.toString()))
                .andExpect(jsonPath("$.category.id").value(firstCategory.toString()));

        mvc.perform(put(ITEMS + "/" + itemId + "/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + secondCategory + "\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.itemCategoryId").value(secondCategory.toString()))
                .andExpect(jsonPath("$.category.id").value(secondCategory.toString()))
                .andExpect(jsonPath("$.category.name").value(PREFIX + "Metadata target"));

        mvc.perform(post(ITEMS + "/" + itemId + "/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(2));
        assertEquals(2, jdbc.queryForObject("SELECT version FROM items WHERE id = ?", Integer.class, itemId));

        mvc.perform(post(ITEMS + "/" + itemId + "/deactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(3));
        mvc.perform(post(ITEMS + "/" + itemId + "/deactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(3));
        assertEquals(3, jdbc.queryForObject("SELECT version FROM items WHERE id = ?", Integer.class, itemId));
    }

    @Test
    void renamedAndDeactivatedCategoryIsReflectedWithoutInvalidatingExistingItem() throws Exception {
        UUID categoryId = createCategory("Projection old");
        UUID itemId = idFrom(createItem(PREFIX + "Projection item", null, categoryId).andReturn());

        mvc.perform(put(CATEGORIES + "/" + categoryId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + PREFIX + "Projection renamed\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get(ITEMS + "/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCategoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value(PREFIX + "Projection renamed"))
                .andExpect(jsonPath("$.category.active").value(true));

        mvc.perform(post(CATEGORIES + "/" + categoryId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(get(ITEMS + "/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCategoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value(PREFIX + "Projection renamed"))
                .andExpect(jsonPath("$.category.active").value(false));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE id = ? AND item_category_id = ?",
                Integer.class, itemId, categoryId));
    }

    @Test
    void rejectsInactiveCategoryAssignmentsAndPreservesItemsAndVersions() throws Exception {
        UUID inactiveForCreate = createCategory("Inactive create");
        mvc.perform(post(CATEGORIES + "/" + inactiveForCreate + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        createItem(PREFIX + "Rejected create", null, inactiveForCreate)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_CATEGORY_INACTIVE"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE name = ?", Integer.class,
                PREFIX + "Rejected create"));

        UUID source = createCategory("Inactive target source");
        UUID target = createCategory("Inactive target");
        UUID itemId = idFrom(createItem(PREFIX + "Preserve rejected reassignment", "details", source).andReturn());
        mvc.perform(post(CATEGORIES + "/" + target + "/deactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(put(ITEMS + "/" + itemId + "/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + target + "\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_CATEGORY_INACTIVE"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM items WHERE id = ? AND item_category_id = ? AND name = ? AND active = TRUE AND version = 0",
                Integer.class, itemId, source, PREFIX + "Preserve rejected reassignment"));
        mvc.perform(get(ITEMS + "/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCategoryId").value(source.toString()))
                .andExpect(jsonPath("$.category.id").value(source.toString()));
    }

    @Test
    void staleMutationAndFractionalVersionsAreRejectedWithoutWrites() throws Exception {
        UUID categoryId = createCategory("Version");
        UUID otherCategory = createCategory("Version target");
        UUID itemId = idFrom(createItem(PREFIX + "Versioned item", null, categoryId).andReturn());
        mvc.perform(put(ITEMS + "/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Current\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mvc.perform(put(ITEMS + "/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stale\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_CONCURRENT_MODIFICATION"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM items WHERE id = ? AND name = 'Current' AND item_category_id = ? AND version = 1",
                Integer.class, itemId, categoryId));

        mvc.perform(put(ITEMS + "/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fractional metadata\",\"expectedVersion\":1.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(put(ITEMS + "/" + itemId + "/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCategoryId\":\"" + otherCategory + "\",\"expectedVersion\":1.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        for (String action : new String[] {"activate", "deactivate"}) {
            mvc.perform(post(ITEMS + "/" + itemId + "/" + action).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":1.5}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM items WHERE id = ? AND name = 'Current' AND item_category_id = ? AND version = 1",
                Integer.class, itemId, categoryId));
    }

    private UUID createCategory(String suffix) throws Exception {
        UUID categoryId = idFrom(mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + PREFIX + suffix + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        return categoryId;
    }

    private org.springframework.test.web.servlet.ResultActions createItem(String name, String description,
            UUID categoryId) throws Exception {
        String descriptionJson = description == null ? "" : ",\"description\":\"" + description + "\"";
        return mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"" + descriptionJson
                        + ",\"itemCategoryId\":\"" + categoryId + "\"}"));
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "response should contain an id");
        return UUID.fromString(matcher.group(1));
    }
}
