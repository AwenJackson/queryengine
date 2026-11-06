package dev.queryengine.plan;

import dev.queryengine.exception.BadRequestException;
import dev.queryengine.spi.Dialect;

import java.util.ArrayList;
import java.util.List;

/**
 * 把单表查询渲染成参数化 SQL。字面量只出现在参数列表中。
 */
public class SqlPlanner {

    protected final Dialect dialect;

    public SqlPlanner(Dialect dialect) {
        if (dialect == null) {
            throw new IllegalArgumentException("dialect 不能为空");
        }
        this.dialect = dialect;
    }

    public PlannedSql select(SingleTableQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<Object> params = new ArrayList<>();
        if (query.columns().isEmpty()) {
            sql.append("*");
        } else {
            for (int i = 0; i < query.columns().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                SelectItem item = query.columns().get(i);
                String alias = item.alias() == null ? item.name() : item.alias();
                sql.append(dialect.quote(item.name()))
                        .append(" AS ")
                        .append(dialect.quoteAlias(alias));
            }
        }
        sql.append(" FROM ").append(tableRef(query));
        appendWhere(sql, params, query.where());
        if (!query.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < query.orderBy().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                OrderBy order = query.orderBy().get(i);
                sql.append(dialect.quote(order.column()))
                        .append(order.descending() ? " DESC" : " ASC");
            }
        }
        if (query.limit() != null) {
            dialect.appendPage(sql, params, query.limit(), query.offset());
        }
        return new PlannedSql(sql.toString(), params);
    }

    public PlannedSql count(SingleTableQuery query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS ")
                .append(dialect.quoteAlias("cnt"))
                .append(" FROM ")
                .append(tableRef(query));
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, query.where());
        return new PlannedSql(sql.toString(), params);
    }

    public PlannedSql insert(SingleTableQuery query) {
        if (query.assignments().isEmpty()) {
            throw new BadRequestException("没有可插入的字段");
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(tableRef(query))
                .append(" (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < query.assignments().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(dialect.quote(query.assignments().get(i).column()));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < query.assignments().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(query.assignments().get(i).value());
        }
        sql.append(")");
        return new PlannedSql(sql.toString(), params);
    }

    public PlannedSql update(SingleTableQuery query) {
        if (query.assignments().isEmpty()) {
            throw new BadRequestException("没有可更新的字段");
        }
        if (query.where().isEmpty()) {
            throw new BadRequestException("更新必须带条件");
        }
        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(tableRef(query))
                .append(" SET ");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < query.assignments().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            Assignment assignment = query.assignments().get(i);
            sql.append(dialect.quote(assignment.column())).append(" = ?");
            params.add(assignment.value());
        }
        appendWhere(sql, params, query.where());
        return new PlannedSql(sql.toString(), params);
    }

    public PlannedSql delete(SingleTableQuery query) {
        if (query.where().isEmpty()) {
            throw new BadRequestException("删除必须带条件");
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableRef(query));
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, query.where());
        return new PlannedSql(sql.toString(), params);
    }

    private String tableRef(SingleTableQuery query) {
        String table = dialect.quote(dialect.canonicalTable(query.table()));
        if (query.schema() == null || query.schema().isBlank()) {
            return table;
        }
        return dialect.quote(query.schema()) + "." + table;
    }

    private void appendWhere(StringBuilder sql, List<Object> params, List<Predicate> where) {
        if (where.isEmpty()) {
            return;
        }
        sql.append(" WHERE ");
        for (int i = 0; i < where.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            appendPredicate(sql, params, where.get(i));
        }
    }

    private void appendPredicate(StringBuilder sql, List<Object> params, Predicate predicate) {
        String column = dialect.quote(predicate.column());
        switch (predicate.op()) {
            case EQ -> {
                sql.append(column).append(" = ?");
                params.add(predicate.values().get(0));
            }
            case NE -> {
                sql.append(column).append(" <> ?");
                params.add(predicate.values().get(0));
            }
            case GT -> {
                sql.append(column).append(" > ?");
                params.add(predicate.values().get(0));
            }
            case GTE -> {
                sql.append(column).append(" >= ?");
                params.add(predicate.values().get(0));
            }
            case LT -> {
                sql.append(column).append(" < ?");
                params.add(predicate.values().get(0));
            }
            case LTE -> {
                sql.append(column).append(" <= ?");
                params.add(predicate.values().get(0));
            }
            case IS_NULL -> sql.append(column).append(" IS NULL");
            case NOT_NULL -> sql.append(column).append(" IS NOT NULL");
            case IN -> appendIn(sql, params, column, predicate.values(), false);
            case NOT_IN -> appendIn(sql, params, column, predicate.values(), true);
            case LIKE -> {
                if (predicate.values().isEmpty()) {
                    sql.append("1 = 0");
                    return;
                }
                if (predicate.values().size() == 1) {
                    sql.append(column).append(" LIKE ?");
                    params.add(predicate.values().get(0));
                    return;
                }
                sql.append("(");
                for (int i = 0; i < predicate.values().size(); i++) {
                    if (i > 0) {
                        sql.append(" OR ");
                    }
                    sql.append(column).append(" LIKE ?");
                    params.add(predicate.values().get(i));
                }
                sql.append(")");
            }
            default -> throw new IllegalStateException("未处理的运算符: " + predicate.op());
        }
    }

    private static void appendIn(
            StringBuilder sql,
            List<Object> params,
            String column,
            List<Object> values,
            boolean negated
    ) {
        if (values.isEmpty()) {
            sql.append(negated ? "1 = 1" : "1 = 0");
            return;
        }
        sql.append(column).append(negated ? " NOT IN (" : " IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(values.get(i));
        }
        sql.append(")");
    }
}
