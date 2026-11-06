package dev.queryengine.dialect.dameng;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.QueryEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 需要环境变量 DAMENG_JDBC_URL，以及可选的 DAMENG_JDBC_USER / DAMENG_JDBC_PASSWORD。
 * 驱动坐标：com.dameng:DmJdbcDriver18。没有驱动或 URL 时本测试跳过。
 */
class DamengIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "DAMENG_JDBC_URL", matches = ".+")
    void roundTripAgainstDameng() throws Exception {
        Class.forName("dm.jdbc.driver.DmDriver");
        String url = System.getenv("DAMENG_JDBC_URL");
        String user = System.getenv().getOrDefault("DAMENG_JDBC_USER", "SYSDBA");
        String password = System.getenv().getOrDefault("DAMENG_JDBC_PASSWORD", "");
        DataSource dataSource = new DriverDataSource(url, user, password);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try {
                statement.execute("DROP TABLE QE_IT_ITEM");
            } catch (SQLException ignored) {
                // 表可能还不存在
            }
            statement.execute("CREATE TABLE QE_IT_ITEM (ID BIGINT PRIMARY KEY, TITLE VARCHAR(100))");
        }
        QueryEngine engine = QueryEngine.builder()
                .dataSource(dataSource)
                .dialect(new DamengDialect(true))
                .registerTable("QE_IT_ITEM")
                .build();
        try {
            JsonNode inserted = engine.post("""
                    { "QE_IT_ITEM": { "id": 1, "title": "dameng" } }
                    """);
            assertEquals(1, inserted.path("QE_IT_ITEM").path("count").asInt());
            JsonNode loaded = engine.get("""
                    { "QE_IT_ITEM": { "id": 1, "@column": "id,title" } }
                    """);
            assertEquals("dameng", loaded.path("QE_IT_ITEM").path("title").asText());
            JsonNode updated = engine.put("""
                    { "QE_IT_ITEM": { "id": 1, "title": "updated" } }
                    """);
            assertEquals(1, updated.path("QE_IT_ITEM").path("count").asInt());
            JsonNode deleted = engine.delete("""
                    { "QE_IT_ITEM": { "id": 1 } }
                    """);
            assertEquals(1, deleted.path("QE_IT_ITEM").path("count").asInt());
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE QE_IT_ITEM");
            } catch (SQLException ignored) {
                // 清理失败不掩盖断言
            }
        }
    }

    private static final class DriverDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        private DriverDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unsupported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
