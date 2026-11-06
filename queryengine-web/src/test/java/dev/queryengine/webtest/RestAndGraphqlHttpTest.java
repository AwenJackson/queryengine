package dev.queryengine.webtest;

import dev.queryengine.QueryEngine;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RestAndGraphqlHttpTest.App.class)
@AutoConfigureMockMvc
class RestAndGraphqlHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private QueryEngine engine;

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `moment`");
            statement.execute("DROP TABLE IF EXISTS `user`");
            statement.execute("CREATE TABLE `user` (`id` BIGINT PRIMARY KEY, `name` VARCHAR(64))");
            statement.execute("INSERT INTO `user` (`id`, `name`) VALUES (38710, 'Tommy')");
            statement.execute("CREATE TABLE `moment` (`id` BIGINT PRIMARY KEY, `userId` BIGINT, `content` VARCHAR(64))");
            statement.execute("INSERT INTO `moment` (`id`, `userId`, `content`) VALUES (1, 38710, 'hello')");
        }
    }

    @Test
    void queryEngineFormatStillReturnsTheFlatEnvelope() throws Exception {
        mvc.perform(post("/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"User\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.User.name").value("Tommy"));
    }

    @Test
    void restListsFiltersGetsAndWrites() throws Exception {
        mvc.perform(get("/rest/user").param("name", "Tommy").param("count", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].name").value("Tommy"));
        mvc.perform(get("/rest/User/38710"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(38710))
                .andExpect(jsonPath("$.data.name").value("Tommy"));
        mvc.perform(get("/rest/Secret/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/rest/User")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9,\"name\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(put("/rest/User/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(get("/rest/User/9"))
                .andExpect(jsonPath("$.data.name").value("Ada Lovelace"));
        mvc.perform(delete("/rest/User/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(get("/rest/User/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void graphqlReadsNestedRowsAndMutations() throws Exception {
        engine.registerTable("Moment");
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ User(id: 38710) { id name Moment(on: \\\"userId\\\") { id content } } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensions.code").value(200))
                .andExpect(jsonPath("$.data.User.name").value("Tommy"))
                .andExpect(jsonPath("$.data.User.Moment[0].content").value("hello"));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"query ($id: Long) { User(id: $id) { name } }\",\"variables\":{\"id\":38710}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.User.name").value("Tommy"));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ UserList(name: \\\"Tommy\\\", count: 5) { id name } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.UserList[0].id").value(38710));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { createUser(id: 8, name: \\\"Lin\\\") { id name affected } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createUser.name").value("Lin"))
                .andExpect(jsonPath("$.data.createUser.affected").value(1));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { updateUser(id: 8, name: \\\"Lin Q\\\") { name affected } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateUser.affected").value(1));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { deleteUser(id: 8) { id affected } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleteUser.affected").value(1));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ Secret(id: 1) { id } }\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].extensions.code").value(400))
                .andExpect(jsonPath("$.extensions.code").value(400));
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].extensions.code").value(400))
                .andExpect(jsonPath("$.errors[0].message").value("请求不是合法 JSON"));
    }

    @SpringBootApplication
    static class App {
    }
}
