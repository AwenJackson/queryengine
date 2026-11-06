package dev.queryengine.demo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.Method;
import dev.queryengine.QueryEngine;
import dev.queryengine.SchemaScope;
import dev.queryengine.dialect.mysql.MySqlDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaSwitchTest {

    private static DemoDatabase database;

    @BeforeAll
    static void startDatabase() {
        database = DemoDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @Test
    void threadSchemaAndCallOverrideSwitchDatabases() {
        QueryEngine engine = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new MySqlDialect())
                .build();
        engine.useSchema("app_a");
        engine.registerTable("User");
        engine.useSchema("app_b");
        engine.registerTable("User");
        engine.useSchema("app_a");
        assertEquals("alpha", engine.get("{ \"User\": { \"id\": 1, \"@column\": \"name\" } }")
                .path("User").path("name").asText());
        try (SchemaScope ignored = engine.withSchema("app_b")) {
            assertEquals("beta", engine.get("{ \"User\": { \"id\": 1, \"@column\": \"name\" } }")
                    .path("User").path("name").asText());
            assertEquals("app_b", engine.currentSchema());
        }
        assertEquals("app_a", engine.currentSchema());
        JsonNode override = engine.execute(
                Method.GET,
                "{ \"User\": { \"id\": 1, \"@column\": \"name\" } }",
                "app_b"
        );
        assertEquals("beta", override.path("User").path("name").asText());
        assertEquals("app_a", engine.currentSchema());
    }

    @Test
    void pinnedSchemaIgnoresRuntimeSwitch() {
        QueryEngine engine = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new MySqlDialect())
                .schema("app_b")
                .build();
        engine.accessRegistry().registerIn("app_b", "User", "user", "app_a");
        assertEquals("alpha", engine.get("{ \"User\": { \"id\": 1, \"@column\": \"name\" } }")
                .path("User").path("name").asText());
        try (SchemaScope ignored = engine.withSchema("app_b")) {
            assertEquals("alpha", engine.get("{ \"User\": { \"id\": 1, \"@column\": \"name\" } }")
                    .path("User").path("name").asText());
        }
        assertTrue(engine.get("{ \"User\": { \"id\": 999 } }").get("User").isNull());
    }
}
