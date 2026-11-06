package dev.queryengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.plan.QuoteDialect;
import dev.queryengine.spi.InsertResult;
import dev.queryengine.spi.SqlExecutor;
import dev.queryengine.spi.SqlSession;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepNestingTest {

    @Test
    void twelveLevelsSucceed() {
        QueryEngine engine = QueryEngine.builder()
                .dialect(new QuoteDialect())
                .sqlExecutor(new FixedRowExecutor())
                .maxPageSize(0)
                .registerTable("Node")
                .build();

        JsonNode result = engine.get(nested(12));
        JsonNode level = result.get("Child[]");
        for (int i = 0; i < 12; i++) {
            assertTrue(level.isArray(), "level " + i);
            assertEquals(1, level.size());
            assertEquals(1L, level.get(0).get("Node").get("id").asLong());
            if (i < 11) {
                level = level.get(0).get("Child[]");
            } else {
                assertFalse(level.get(0).has("Child[]"));
            }
        }
        assertFalse(result.toString().contains("MAX_"));
    }

    private static JsonNode nested(int depth) {
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

    private static final class FixedRowExecutor implements SqlExecutor {
        @Override
        public <T> T transaction(java.util.function.Function<SqlSession, T> work) {
            return work.apply(new SqlSession() {
                @Override
                public List<Map<String, Object>> query(String sql, List<Object> parameters) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", 1L);
                    row.put("name", "n");
                    return List.of(row);
                }

                @Override
                public int update(String sql, List<Object> parameters) {
                    return 0;
                }

                @Override
                public InsertResult insert(String sql, List<Object> parameters) {
                    return new InsertResult(0, null);
                }
            });
        }
    }
}
