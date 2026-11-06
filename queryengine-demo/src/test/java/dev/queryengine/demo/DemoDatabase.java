package dev.queryengine.demo;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

final class DemoDatabase implements AutoCloseable {

    private final JdbcDataSource dataSource;

    private DemoDatabase(JdbcDataSource dataSource) {
        this.dataSource = dataSource;
    }

    static DemoDatabase start() {
        JdbcDataSource dataSource = new JdbcDataSource();
        String name = "qe" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setURL("jdbc:h2:mem:" + name
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=FALSE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        DemoDatabase database = new DemoDatabase(dataSource);
        database.load();
        return database;
    }

    DataSource dataSource() {
        return dataSource;
    }

    private void load() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String script = new String(
                    DemoDatabase.class.getResourceAsStream("/demo-schema.sql").readAllBytes(),
                    StandardCharsets.UTF_8
            );
            StringBuilder current = new StringBuilder();
            for (String line : script.split("\n")) {
                if (line.trim().startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
            }
            for (String part : current.toString().split(";")) {
                String sql = part.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("初始化演示库失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // 内存库随进程退出
        }
    }
}
