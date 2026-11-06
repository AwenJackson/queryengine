package dev.queryengine.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.QueryEngine;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.exception.BadRequestException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryContractTest {

    private static DemoDatabase database;
    private QueryEngine engine;

    @BeforeAll
    static void startDatabase() {
        database = DemoDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @BeforeEach
    void engine() {
        engine = EmbeddingExample.create(database.dataSource());
    }

    @Test
    void getUserByIdMatchesDemoShape() {
        JsonNode result = engine.get("""
                { "User": { "id": 38710 } }
                """);
        JsonNode user = result.get("User");
        assertEquals(38710L, user.get("id").asLong());
        assertEquals("Tommy", user.get("name").asText());
        assertEquals(0, user.get("sex").asInt());
        assertEquals("student", user.get("tag").asText());
        assertFalse(result.has("code"));
        assertFalse(result.has("msg"));
    }

    @Test
    void arrayPageColumnAndOrder() {
        JsonNode result = engine.get("""
                {
                  "[]": {
                    "page": 0,
                    "count": 2,
                    "User": { "@column": "id,name", "@order": "id+" }
                  }
                }
                """);
        JsonNode rows = result.get("[]");
        assertEquals(2, rows.size());
        assertEquals(10001L, rows.get(0).get("User").get("id").asLong());
        assertEquals("Blank", rows.get(0).get("User").get("name").asText());
        assertEquals(38710L, rows.get(1).get("User").get("id").asLong());
        assertFalse(rows.get(0).get("User").has("sex"));
    }

    @Test
    void filtersInCompareLikeNullAndNotIn() {
        JsonNode likes = engine.get("""
                { "[]": { "count": 10, "User": { "name$": "%Tom%", "@column": "id", "@order": "id+" } } }
                """);
        assertEquals(2, likes.get("[]").size());
        assertEquals(38710L, likes.get("[]").get(0).get("User").get("id").asLong());
        assertEquals(82001L, likes.get("[]").get(1).get("User").get("id").asLong());

        JsonNode compared = engine.get("""
                { "[]": { "count": 10, "User": { "id>=": 80000, "@column": "name" } } }
                """);
        assertEquals(1, compared.get("[]").size());
        assertEquals("Tom", compared.get("[]").get(0).get("User").get("name").asText());

        JsonNode range = engine.get("""
                { "[]": { "count": 10, "User": { "id{}": ">=38710,<80000", "@column": "id", "@order": "id+" } } }
                """);
        assertEquals(2, range.get("[]").size());
        assertEquals(38710L, range.get("[]").get(0).get("User").get("id").asLong());
        assertEquals(70793L, range.get("[]").get(1).get("User").get("id").asLong());

        JsonNode included = engine.get("""
                { "[]": { "count": 10, "User": { "id{}": [38710, 82001], "@column": "id", "@order": "id-" } } }
                """);
        assertEquals(82001L, included.get("[]").get(0).get("User").get("id").asLong());

        JsonNode excluded = engine.get("""
                { "[]": { "count": 10, "User": { "sex!": 0, "@column": "name" } } }
                """);
        assertEquals("Lily", excluded.get("[]").get(0).get("User").get("name").asText());

        JsonNode blanks = engine.get("""
                { "[]": { "User": { "tag": null, "@column": "name" } } }
                """);
        assertEquals("Blank", blanks.get("[]").get(0).get("User").get("name").asText());

        JsonNode notIn = engine.get("""
                { "[]": { "count": 10, "User": { "id!{}": [38710], "sex": 0, "@column": "id", "@order": "id+" } } }
                """);
        assertEquals(10001L, notIn.get("[]").get(0).get("User").get("id").asLong());
        assertEquals(82001L, notIn.get("[]").get(1).get("User").get("id").asLong());
    }

    @Test
    void emptyInMatchesNothingAndQuoteStaysLiteral() {
        JsonNode empty = engine.get("""
                { "[]": { "User": { "id{}": [] } } }
                """);
        assertEquals(0, empty.get("[]").size());

        JsonNode injected = engine.get("""
                { "[]": { "User": { "name": "Tommy' OR '1'='1" } } }
                """);
        assertEquals(0, injected.get("[]").size());
    }

    @Test
    void headCountsAndIgnoresPage() {
        JsonNode result = engine.head("""
                { "User": { "sex": 0 }, "tag": "ignored", "format": true }
                """);
        assertEquals(3, result.get("User").get("count").asInt());
        assertFalse(result.has("tag"));
        assertEquals(4, engine.head("{ \"[]\": { \"count\": 1, \"User\": {} } }").get("[]").get("count").asInt());
    }

    @Test
    void referenceAssignWorksRegardlessOfKeyOrder() {
        JsonNode forward = engine.get("""
                {
                  "User": { "id": 82001, "@column": "id,name" },
                  "Moment": { "userId@": "/User/id", "@column": "id,content" }
                }
                """);
        assertEquals("Tom", forward.get("User").get("name").asText());
        assertEquals("other", forward.get("Moment").get("content").asText());

        JsonNode backward = engine.get("""
                {
                  "Moment[]": {
                    "count": 10,
                    "Moment": { "userId@": "/User/id", "@column": "id,content", "@order": "id+" }
                  },
                  "User": { "id": 38710, "@column": "id,name" }
                }
                """);
        assertEquals("Moment[]", backward.fieldNames().next());
        assertEquals("Tommy", backward.get("User").get("name").asText());
        assertEquals(2, backward.get("Moment[]").size());
        assertEquals("hello", backward.get("Moment[]").get(0).get("Moment").get("content").asText());
    }

    @Test
    void nestedMomentsAndComments() {
        JsonNode result = engine.get("""
                {
                  "[]": {
                    "count": 5,
                    "page": 0,
                    "User": {
                      "id{}": [38710, 82001],
                      "@column": "id,name",
                      "@order": "id+"
                    },
                    "Moment[]": {
                      "count": 5,
                      "Moment": {
                        "userId@": "[]/User/id",
                        "@column": "id,content",
                        "@order": "id+"
                      },
                      "Comment[]": {
                        "count": 5,
                        "Comment": {
                          "momentId@": "[]/Moment/id",
                          "@column": "id,content",
                          "@order": "id+"
                        }
                      }
                    }
                  }
                }
                """);
        JsonNode tommy = result.get("[]").get(0);
        assertEquals("Tommy", tommy.get("User").get("name").asText());
        assertEquals(2, tommy.get("Moment[]").size());
        JsonNode hello = tommy.get("Moment[]").get(0);
        assertEquals("hello", hello.get("Moment").get("content").asText());
        assertEquals(3, hello.get("Comment[]").size());
        assertEquals("nice", hello.get("Comment[]").get(0).get("Comment").get("content").asText());
        assertEquals("good", hello.get("Comment[]").get(1).get("Comment").get("content").asText());
        assertEquals("aoisa", hello.get("Comment[]").get(2).get("Comment").get("content").asText());
        assertEquals(1, tommy.get("Moment[]").get(1).get("Comment[]").size());
        JsonNode tom = result.get("[]").get(1);
        assertEquals("Tom", tom.get("User").get("name").asText());
        assertEquals(0, tom.get("Moment[]").get(0).get("Comment[]").size());
    }

    @Test
    void deepParentChainHasNoDepthRejection() {
        JsonNode result = engine.get(chain(12));
        JsonNode level = result.get("Child[]");
        long expected = 2L;
        for (int i = 0; i < 12; i++) {
            assertEquals(1, level.size(), "depth " + i);
            JsonNode node = level.get(0).get("Node");
            assertEquals(expected, node.get("id").asLong());
            assertEquals("n" + expected, node.get("name").asText());
            level = level.get(0).get("Child[]");
            expected++;
        }
        assertEquals(14L, expected);
    }

    @Test
    void softPageClampDoesNotThrow() {
        QueryEngine capped = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new dev.queryengine.dialect.mysql.MySqlDialect())
                .schema("demo")
                .maxPageSize(2)
                .registerTable("User")
                .build();
        JsonNode result = capped.get("""
                { "[]": { "count": 1000, "page": 0, "User": { "@column": "id", "@order": "id+" } } }
                """);
        assertEquals(2, result.get("[]").size());
        assertEquals(10001L, result.get("[]").get(0).get("User").get("id").asLong());
        assertEquals(38710L, result.get("[]").get(1).get("User").get("id").asLong());
    }

    @Test
    void unregisteredTableAndAmbiguousObjectAreRejected() {
        AccessDeniedException denied = assertThrows(AccessDeniedException.class,
                () -> engine.get("{ \"Secret\": { \"id\": 1 } }"));
        assertTrue(denied.getMessage().contains("Secret"));
        assertThrows(BadRequestException.class, () -> engine.get("{ \"User\": { \"sex\": 0 } }"));
        assertThrows(BadRequestException.class, () -> engine.get("{ \"User\": { \"@column\": \"count(id)\" } }"));
        engine.unregisterTable("Comment");
        assertThrows(AccessDeniedException.class, () -> engine.get("{ \"Comment\": { \"id\": 10 } }"));
    }

    private static JsonNode chain(int depth) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode current = null;
        for (int level = depth; level >= 1; level--) {
            ObjectNode node = mapper.createObjectNode();
            node.put("@column", "id,name");
            node.put("parentId@", level == 1 ? "/Node/id" : "[]/Node/id");
            ObjectNode array = mapper.createObjectNode();
            array.put("count", 5);
            array.set("Node", node);
            if (current != null) {
                array.set("Child[]", current);
            }
            current = array;
        }
        ObjectNode root = mapper.createObjectNode();
        ObjectNode top = mapper.createObjectNode();
        top.put("id", 1);
        top.put("@column", "id,name");
        root.set("Node", top);
        root.set("Child[]", current);
        return root;
    }
}
