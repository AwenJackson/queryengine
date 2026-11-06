package dev.queryengine.boot;

import dev.queryengine.QueryEngine;
import dev.queryengine.authz.JdbcAccessStore;
import dev.queryengine.dialect.mysql.MySqlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BootSmokeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private QueryEngine engine;

    @Autowired
    private DataSource dataSource;

    @Test
    void seededUserIsQueryableOnGet() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"User\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.User.name").value("Tommy"))
                .andExpect(jsonPath("$.User.tag").value("student"));
    }

    @Test
    void userId38710BringsRelatedRows() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "User": { "id": 38710 },
                                  "Moment[]": { "Moment": { "userId": 38710 } },
                                  "Comment[]": { "Comment": { "userId": 38710 } },
                                  "Book[]": { "Book": { "user_id": 38710 } },
                                  "Book_menu[]": { "Book_menu": { "user_id": 38710 } },
                                  "Type[]": { "Type": { "user_id": 38710 } }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.User.name").value("Tommy"))
                .andExpect(jsonPath("$['Moment[]'][*].Moment.content", containsInAnyOrder("hello", "world")))
                .andExpect(jsonPath("$['Comment[]'][0].Comment.content").value("aoisa"))
                .andExpect(jsonPath("$['Book[]'][0].Book.book_name").value("安徒生童话"))
                .andExpect(jsonPath("$['Book_menu[]'][*].Book_menu.menu_name", containsInAnyOrder("章节一", "章节二")))
                .andExpect(jsonPath("$['Type[]'][*].Type.type_name", containsInAnyOrder("类型名称", "新增")));
    }

    @Test
    void tableNamesAreCaseInsensitive() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.user.name").value("Tommy"));

        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"BOOK\":{\"user_id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BOOK.book_name").value("安徒生童话"));

        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"book\":{\"id\":2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.book.book_name").value("安徒生童话"));
    }

    @Test
    void bookMenuTipsFollowTommyChapters() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "Book_menu_tip[]": { "count": 10, "Book_menu_tip": { "book_menu_id{}": [1, 2] } } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$['Book_menu_tip[]'][*].Book_menu_tip.tip_name",
                        containsInAnyOrder("章节一提示", "章节二提示")));
    }

    @Test
    void registerTableIsVisibleImmediatelyAndAfterReload() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS demo.`note`");
            statement.execute("CREATE TABLE demo.`note` (`id` BIGINT PRIMARY KEY, `title` VARCHAR(64))");
            statement.execute("INSERT INTO demo.`note` (`id`, `title`) VALUES (1, '即时登记')");
        }
        engine.registerTable("Note");
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Note\":{\"id\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Note.title").value("即时登记"));

        QueryEngine restarted = QueryEngine.builder()
                .dataSource(dataSource)
                .dialect(new MySqlDialect())
                .schema("demo")
                .accessStore(new JdbcAccessStore(dataSource, new MySqlDialect(), "access", "demo"))
                .build();
        String title = restarted.get("{\"note\":{\"id\":1}}").path("note").path("title").asText();
        org.junit.jupiter.api.Assertions.assertEquals("即时登记", title);
    }

    @Test
    void demo1DoesNotGrantBookMenuTip() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-QueryEngine-Schema", "demo1")
                        .content("{\"Book_menu_tip\":{\"id\":1}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-QueryEngine-Schema", "demo1")
                        .content("{\"User\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.User.name").value("Tommy"));
        mvc.perform(post("/access/list")
                        .header("X-QueryEngine-Schema", "demo1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'Book_menu_tip')]").isEmpty());
    }

    @Test
    void unregisterInDemo1DoesNotLeaveDemoGrantVisible() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE demo.`access` SET `schema_name` = 'demo' WHERE `name` = 'Book_menu_tip'");
        }
        engine.reloadAccess("demo");
        mvc.perform(post("/access/unregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Book_menu_tip\",\"schema\":\"demo1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/access/list")
                        .header("X-QueryEngine-Schema", "demo1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'Book_menu_tip')]").isEmpty());
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-QueryEngine-Schema", "demo1")
                        .content("{\"Book_menu_tip\":{\"id\":1}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Book_menu_tip\":{\"id\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.Book_menu_tip.id").value(1));
    }
}
