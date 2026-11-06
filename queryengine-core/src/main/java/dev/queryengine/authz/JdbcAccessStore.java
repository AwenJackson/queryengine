package dev.queryengine.authz;

import dev.queryengine.exec.JdbcSqlExecutor;
import dev.queryengine.spi.Dialect;
import dev.queryengine.spi.SqlExecutor;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 读写 {@code access} 表。表名按方言做大小写折叠，列名按建表时的大小写引用。
 */
public final class JdbcAccessStore implements AccessStore {

    private final SqlExecutor executor;
    private final Dialect dialect;
    private final String table;
    private final String schema;

    public JdbcAccessStore(DataSource dataSource, Dialect dialect, String table, String schema) {
        this(new JdbcSqlExecutor(dataSource, 0), dialect, table, schema);
    }

    JdbcAccessStore(SqlExecutor executor, Dialect dialect, String table, String schema) {
        if (executor == null || dialect == null) {
            throw new IllegalArgumentException("executor 和 dialect 不能为空");
        }
        this.executor = executor;
        this.dialect = dialect;
        this.table = table == null || table.isBlank() ? "access" : table;
        this.schema = schema == null || schema.isBlank() ? null : schema;
    }

    @Override
    public List<AccessRecord> loadEnabled(String schema) {
        String sql = "SELECT " + q("name") + ", " + q("alias") + ", " + q("schema_name") + ", " + q("enabled")
                + " FROM " + tableRef(schema) + " WHERE " + q("enabled") + " = ?";
        return executor.transaction(session -> {
            List<AccessRecord> records = new ArrayList<>();
            for (Map<String, Object> row : session.query(sql, List.of(1))) {
                Object enabled = cell(row, "enabled");
                boolean on = enabled instanceof Number number && number.intValue() != 0
                        || enabled instanceof Boolean flag && flag;
                if (!on) {
                    continue;
                }
                records.add(new AccessRecord(
                        text(cell(row, "name")),
                        text(cell(row, "alias")),
                        text(cell(row, "schema_name")),
                        true
                ));
            }
            return records;
        });
    }

    @Override
    public void save(String schema, AccessRecord record) {
        String delete = "DELETE FROM " + tableRef(schema) + " WHERE LOWER(" + q("name") + ") = ?";
        String insert = "INSERT INTO " + tableRef(schema) + " (" + q("name") + ", " + q("alias") + ", "
                + q("schema_name") + ", " + q("enabled") + ") VALUES (?, ?, ?, ?)";
        executor.transaction(session -> {
            session.update(delete, List.of(record.name().toLowerCase(Locale.ROOT)));
            session.update(insert, Arrays.asList(
                    record.name(),
                    blankToNull(record.alias()),
                    blankToNull(record.schema()),
                    record.enabled() ? 1 : 0
            ));
            return null;
        });
    }

    @Override
    public void disable(String schema, String name) {
        String sql = "UPDATE " + tableRef(schema) + " SET " + q("enabled") + " = ? WHERE LOWER(" + q("name") + ") = ?";
        executor.transaction(session -> {
            session.update(sql, List.of(0, name.toLowerCase(Locale.ROOT)));
            return null;
        });
    }

    private String tableRef(String requestedSchema) {
        String quoted = dialect.quote(dialect.canonicalTable(table));
        String effective = requestedSchema == null || requestedSchema.isBlank() ? schema : requestedSchema;
        if (effective == null || effective.isBlank()) {
            return quoted;
        }
        return dialect.quote(effective) + "." + quoted;
    }

    private String q(String identifier) {
        return dialect.quote(identifier);
    }

    private static Object cell(Map<String, Object> row, String column) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
