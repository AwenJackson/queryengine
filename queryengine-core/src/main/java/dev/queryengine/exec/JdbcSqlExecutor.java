package dev.queryengine.exec;

import dev.queryengine.exception.QueryExecutionException;
import dev.queryengine.spi.InsertResult;
import dev.queryengine.spi.SqlExecutor;
import dev.queryengine.spi.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class JdbcSqlExecutor implements SqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcSqlExecutor.class);

    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcSqlExecutor(DataSource dataSource, int queryTimeoutSeconds) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        if (queryTimeoutSeconds < 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds 不能为负");
        }
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public <T> T transaction(Function<SqlSession, T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(new JdbcSession(connection, queryTimeoutSeconds));
                connection.commit();
                return result;
            } catch (RuntimeException ex) {
                rollbackQuietly(connection);
                throw ex;
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw new QueryExecutionException("提交事务失败: " + ex.getMessage(), ex);
            } finally {
                restoreAutoCommit(connection, previous);
            }
        } catch (SQLException ex) {
            throw new QueryExecutionException("获取数据库连接失败: " + ex.getMessage(), ex);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            log.warn("回滚事务失败", ex);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean previous) {
        try {
            connection.setAutoCommit(previous);
        } catch (SQLException ex) {
            log.warn("恢复 autoCommit 失败", ex);
        }
    }

    private static final class JdbcSession implements SqlSession {

        private final Connection connection;
        private final int queryTimeoutSeconds;

        private JdbcSession(Connection connection, int queryTimeoutSeconds) {
            this.connection = connection;
            this.queryTimeoutSeconds = queryTimeoutSeconds;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> parameters) {
            log.debug("SQL {} ({} params)", sql, parameters.size());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                prepare(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    return read(rows);
                }
            } catch (SQLException ex) {
                throw new QueryExecutionException("执行 SQL 失败: " + ex.getMessage() + " | " + sql, ex);
            }
        }

        @Override
        public int update(String sql, List<Object> parameters) {
            log.debug("SQL {} ({} params)", sql, parameters.size());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                prepare(statement, parameters);
                return statement.executeUpdate();
            } catch (SQLException ex) {
                throw new QueryExecutionException("执行 SQL 失败: " + ex.getMessage() + " | " + sql, ex);
            }
        }

        @Override
        public InsertResult insert(String sql, List<Object> parameters) {
            log.debug("SQL {} ({} params)", sql, parameters.size());
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                prepare(statement, parameters);
                int affected = statement.executeUpdate();
                Object key = null;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        key = JdbcValues.normalize(keys.getObject(1));
                    }
                }
                return new InsertResult(affected, key);
            } catch (SQLException ex) {
                throw new QueryExecutionException("执行 SQL 失败: " + ex.getMessage() + " | " + sql, ex);
            }
        }

        private void prepare(PreparedStatement statement, List<Object> parameters) throws SQLException {
            if (queryTimeoutSeconds > 0) {
                statement.setQueryTimeout(queryTimeoutSeconds);
            }
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
        }

        private static List<Map<String, Object>> read(ResultSet rows) throws SQLException {
            ResultSetMetaData meta = rows.getMetaData();
            int columns = meta.getColumnCount();
            List<Map<String, Object>> result = new ArrayList<>();
            while (rows.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(meta.getColumnLabel(i), JdbcValues.normalize(rows.getObject(i)));
                }
                result.add(row);
            }
            return result;
        }
    }
}
