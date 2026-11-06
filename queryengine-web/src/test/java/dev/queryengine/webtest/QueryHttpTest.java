package dev.queryengine.webtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = QueryHttpTest.App.class)
@AutoConfigureMockMvc
class QueryHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `user`");
            statement.execute("CREATE TABLE `user` (`id` BIGINT PRIMARY KEY, `name` VARCHAR(64))");
            statement.execute("INSERT INTO `user` (`id`, `name`) VALUES (38710, 'Tommy')");
        }
    }

    @Test
    void getReturnsBusinessKeysAndSuccessEnvelope() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"User\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.User.id").value(38710))
                .andExpect(jsonPath("$.User.name").value("Tommy"));
    }

    @Test
    void nestqlGetIsAnAliasOfGet() throws Exception {
        mvc.perform(post("/nestql/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"User\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.User.name").value("Tommy"));
    }

    @Test
    void getsIsAnAliasOfGet() throws Exception {
        mvc.perform(post("/gets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"[]\":{\"count\":5,\"User\":{\"@column\":\"id,name\",\"@order\":\"id+\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$['[]'][0].User.name").value("Tommy"));
    }

    @Test
    void headWrapsCount() throws Exception {
        mvc.perform(post("/head")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"User\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.User.count").value(1));
    }

    @Test
    void unregisteredTableIsForbiddenEnvelope() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Secret\":{\"id\":1}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("未注册或无权访问的表: Secret"))
                .andExpect(jsonPath("$.Secret").doesNotExist());
    }

    @Test
    void invalidColumnIsBadRequestEnvelope() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"User\":{\"@column\":\"count(id)\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.User").doesNotExist());
    }

    @Test
    void malformedJsonIsBadRequestEnvelope() throws Exception {
        mvc.perform(post("/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求不是合法 JSON"));
    }

    @Test
    void registerThenGetThenUnregister() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `note`");
            statement.execute("CREATE TABLE `note` (`id` BIGINT PRIMARY KEY, `title` VARCHAR(64))");
            statement.execute("INSERT INTO `note` (`id`, `title`) VALUES (1, 'via-http')");
        }
        mvc.perform(post("/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Note\",\"alias\":\"note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.name").value("Note"))
                .andExpect(jsonPath("$.data.alias").value("note"))
                .andExpect(jsonPath("$.data.schema").value(nullValue()));
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Note\":{\"id\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Note.title").value("via-http"));
        mvc.perform(post("/access/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'Note')].alias", contains("note")));
        mvc.perform(post("/access/unregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("note"));
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Note\":{\"id\":1}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void registerRequiresName() throws Exception {
        mvc.perform(post("/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @SpringBootApplication
    static class App {
    }
}
