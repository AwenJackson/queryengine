package dev.queryengine.plan;

import dev.queryengine.spi.Dialect;

import java.util.List;

public final class QuoteDialect implements Dialect {

    @Override
    public String name() {
        return "quote";
    }

    @Override
    public String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String quoteAlias(String alias) {
        return "\"" + alias + "\"";
    }

    @Override
    public void appendPage(StringBuilder sql, List<Object> params, int limit, int offset) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
    }
}
