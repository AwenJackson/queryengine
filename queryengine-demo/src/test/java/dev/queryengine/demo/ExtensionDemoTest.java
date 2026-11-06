package dev.queryengine.demo;

import dev.queryengine.QueryEngine;
import dev.queryengine.demo.ext.CommentedSqlPlanner;
import dev.queryengine.demo.ext.HidePasswordParser;
import dev.queryengine.demo.ext.NoDeleteRegistry;
import dev.queryengine.dialect.mysql.MySqlDialect;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.spi.SqlExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionDemoTest {

    @Test
    void subclassedParserRejectsPasswordColumn() {
        QueryEngine engine = QueryEngine.builder()
                .dialect(new MySqlDialect())
                .sqlExecutor(new CapturingExecutor())
                .fieldParser(new HidePasswordParser())
                .registerTable("User")
                .build();
        assertThrows(BadRequestException.class, () -> engine.get("{ \"User\": { \"@column\": \"password\" } }"));
    }

    @Test
    void subclassedPlannerPrefixesSelect() {
        CapturingExecutor executor = new CapturingExecutor();
        QueryEngine engine = QueryEngine.builder()
                .dialect(new MySqlDialect())
                .sqlExecutor(executor)
                .sqlPlanner(new CommentedSqlPlanner(new MySqlDialect()))
                .registerTable("User")
                .build();
        engine.get("{ \"User\": { \"id\": 1, \"@column\": \"id\" } }");
        assertTrue(executor.sql.get(0).startsWith("/* ext */ SELECT"));
    }

    @Test
    void subclassedRegistryBlocksDelete() {
        NoDeleteRegistry registry = new NoDeleteRegistry();
        QueryEngine engine = QueryEngine.builder()
                .dialect(new MySqlDialect())
                .sqlExecutor(new CapturingExecutor())
                .accessRegistry(registry)
                .registerTable("User")
                .build();
        assertThrows(AccessDeniedException.class, () -> engine.delete("{ \"User\": { \"id\": 1 } }"));
    }

    private static final class CapturingExecutor implements SqlExecutor {
        private final List<String> sql = new ArrayList<>();

        @Override
        public <T> T transaction(java.util.function.Function<dev.queryengine.spi.SqlSession, T> work) {
            return work.apply(new dev.queryengine.spi.SqlSession() {
                @Override
                public List<Map<String, Object>> query(String statement, List<Object> parameters) {
                    sql.add(statement);
                    return List.of(Map.of("id", 1));
                }

                @Override
                public int update(String statement, List<Object> parameters) {
                    sql.add(statement);
                    return 1;
                }

                @Override
                public dev.queryengine.spi.InsertResult insert(String statement, List<Object> parameters) {
                    sql.add(statement);
                    return new dev.queryengine.spi.InsertResult(1, 1);
                }
            });
        }
    }
}
