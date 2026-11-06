package dev.queryengine.demo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.QueryEngine;
import dev.queryengine.dialect.mysql.MySqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 有 Docker 时用真实 MySQL 再跑一条黄金请求。没有 Docker 时跳过，契约测试仍由 H2 的 MySQL 模式执行。
 */
class MySqlContainerTest {

    @Test
    void goldRequestAgainstMysqlWhenDockerExists() throws Exception {
        Assumptions.assumeTrue(
                Files.exists(Path.of("/var/run/docker.sock")),
                "没有 Docker，跳过真实 MySQL；契约由 H2 MySQL 模式覆盖"
        );
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")) {
            mysql.withDatabaseName("demo");
            mysql.start();
            try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE `user` (`id` BIGINT PRIMARY KEY, `name` VARCHAR(64), `sex` INT, `tag` VARCHAR(64))");
                statement.execute("INSERT INTO `user` (`id`, `name`, `sex`, `tag`) VALUES (38710, 'Tommy', 0, 'student')");
            }
            QueryEngine engine = QueryEngine.builder()
                    .dataSource(new MysqlDataSource(mysql))
                    .dialect(new MySqlDialect())
                    .schema("demo")
                    .registerTable("User")
                    .build();
            JsonNode result = engine.get("{ \"User\": { \"id\": 38710 } }");
            assertEquals("Tommy", result.path("User").path("name").asText());
        }
    }

    private static final class MysqlDataSource implements javax.sql.DataSource {
        private final MySQLContainer<?> mysql;

        private MysqlDataSource(MySQLContainer<?> mysql) {
            this.mysql = mysql;
        }

        @Override
        public Connection getConnection() throws java.sql.SQLException {
            return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }

        @Override
        public Connection getConnection(String username, String password) throws java.sql.SQLException {
            return DriverManager.getConnection(mysql.getJdbcUrl(), username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger("mysql");
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
