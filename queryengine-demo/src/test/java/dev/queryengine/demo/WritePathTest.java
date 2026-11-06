package dev.queryengine.demo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.QueryEngine;
import dev.queryengine.dialect.mysql.MySqlDialect;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.exception.QueryExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritePathTest {

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
    void registerTableAllowsFullCrudLifecycle() {
        QueryEngine engine = EmbeddingExample.create(database.dataSource());
        JsonNode created = engine.post("""
                { "Todo": { "title": "买牛奶", "done": 0 } }
                """);
        long id = created.path("Todo").path("id").asLong();
        assertTrue(id > 0);
        assertEquals(1, created.path("Todo").path("count").asInt());
        assertEquals("买牛奶", created.path("Todo").path("title").asText());

        JsonNode updated = engine.put("""
                { "Todo": { "id": %d, "title": "买面包", "done": 1 } }
                """.formatted(id));
        assertEquals(1, updated.path("Todo").path("count").asInt());

        JsonNode loaded = engine.get("""
                { "Todo": { "id": %d, "@column": "id,title,done" } }
                """.formatted(id));
        assertEquals("买面包", loaded.path("Todo").path("title").asText());
        assertEquals(1, loaded.path("Todo").path("done").asInt());

        assertEquals(1, engine.head("{ \"Todo\": { \"id\": %d } }".formatted(id)).path("Todo").path("count").asInt());

        JsonNode deleted = engine.delete("""
                { "Todo": { "id": %d } }
                """.formatted(id));
        assertEquals(1, deleted.path("Todo").path("count").asInt());
        assertTrue(engine.get("{ \"Todo\": { \"id\": %d } }".formatted(id)).get("Todo").isNull());
    }

    @Test
    void batchInsertAndInUpdate() {
        QueryEngine engine = EmbeddingExample.create(database.dataSource());
        JsonNode created = engine.post("""
                { "Todo[]": [ { "title": "一", "done": 0 }, { "title": "二", "done": 0 } ] }
                """);
        long first = created.get("Todo[]").get(0).get("id").asLong();
        long second = created.get("Todo[]").get(1).get("id").asLong();
        JsonNode updated = engine.put("""
                { "Todo": { "id{}": [%d, %d], "done": 1 } }
                """.formatted(first, second));
        assertEquals(2, updated.path("Todo").path("count").asInt());
        JsonNode rows = engine.get("""
                { "[]": { "count": 10, "Todo": { "id{}": [%d, %d], "@column": "done" } } }
                """.formatted(first, second));
        assertEquals(1, rows.get("[]").get(0).get("Todo").get("done").asInt());
        assertEquals(1, rows.get("[]").get(1).get("Todo").get("done").asInt());
    }

    @Test
    void missingIdIsRejectedAndFailedRequestRollsBack() {
        QueryEngine engine = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new MySqlDialect())
                .schema("demo")
                .registerTables("Todo", "TodoNote")
                .build();
        assertThrows(BadRequestException.class, () -> engine.delete("{ \"Todo\": { \"title\": \"x\" } }"));
        assertThrows(BadRequestException.class, () -> engine.put("{ \"Todo\": { \"title\": \"only\" } }"));

        assertThrows(QueryExecutionException.class, () -> engine.post("""
                {
                  "Todo": { "title": "should-rollback" },
                  "TodoNote": { "id": 1, "title": "missing-table" }
                }
                """));
        JsonNode left = engine.head("""
                { "Todo": { "title": "should-rollback" } }
                """);
        assertEquals(0, left.path("Todo").path("count").asInt());

        QueryEngine closed = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new MySqlDialect())
                .schema("demo")
                .registerTable("Todo")
                .build();
        assertThrows(AccessDeniedException.class, () -> closed.post("""
                { "Todo": { "title": "nope" }, "Secret": { "title": "x" } }
                """));
        assertEquals(0, closed.head("{ \"Todo\": { \"title\": \"nope\" } }").path("Todo").path("count").asInt());
        assertTrue(closed.get("{ \"Todo\": { \"title\": \"nope\" } }").get("Todo").isNull());
    }
}
