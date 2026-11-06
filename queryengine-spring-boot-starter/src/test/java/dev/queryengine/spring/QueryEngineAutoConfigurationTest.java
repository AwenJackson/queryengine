package dev.queryengine.spring;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.QueryEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryEngineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    QueryEngineAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:starter;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "queryengine.tables=User",
                    "queryengine.access.enabled=false"
            );

    @Test
    void exposesEngineWithoutWebAndRunsAQuery() {
        runner.run(context -> {
            assertEquals(1, context.getBeansOfType(QueryEngine.class).size());
            DataSource dataSource = context.getBean(DataSource.class);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE `user` (`id` BIGINT PRIMARY KEY, `name` VARCHAR(32))");
                statement.execute("INSERT INTO `user` (`id`, `name`) VALUES (1, 'starter')");
            }
            QueryEngine engine = context.getBean(QueryEngine.class);
            assertEquals("mysql", engine.dialectName());
            JsonNode loaded = engine.get("{ \"User\": { \"id\": 1 } }");
            assertEquals("starter", loaded.path("User").path("name").asText());
            assertEquals("starter", engine.get("{ \"user\": { \"id\": 1 } }").path("user").path("name").asText());
            assertThrows(dev.queryengine.exception.AccessDeniedException.class,
                    () -> engine.get("{ \"Moment\": { \"id\": 1 } }"));
        });
    }

    @Test
    void damengDialectCanBeSelected() {
        runner.withPropertyValues("queryengine.dialect=dameng").run(context -> {
            assertEquals("dameng", context.getBean(QueryEngine.class).dialectName());
        });
    }
}
