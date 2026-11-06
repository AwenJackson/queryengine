package dev.queryengine.dialect.mysql;

import dev.queryengine.spi.Dialect;

import java.util.List;
import java.util.Locale;

/** MySQL 标识符使用反引号。表名折成小写后再引用，列名保持原样。分页使用 {@code LIMIT ? OFFSET ?}。 */
public final class MySqlDialect implements Dialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String canonicalTable(String table) {
        return table.toLowerCase(Locale.ROOT);
    }

    @Override
    public String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String quoteAlias(String alias) {
        return quote(alias);
    }

    @Override
    public void appendPage(StringBuilder sql, List<Object> params, int limit, int offset) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
    }
}
