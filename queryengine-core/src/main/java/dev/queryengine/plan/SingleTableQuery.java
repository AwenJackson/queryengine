package dev.queryengine.plan;

import java.util.List;

public record SingleTableQuery(
        String schema,
        String table,
        List<SelectItem> columns,
        List<Predicate> where,
        List<OrderBy> orderBy,
        Integer limit,
        int offset,
        List<Assignment> assignments
) {

    public SingleTableQuery {
        columns = List.copyOf(columns);
        where = List.copyOf(where);
        orderBy = List.copyOf(orderBy);
        assignments = List.copyOf(assignments);
        if (offset < 0) {
            throw new IllegalArgumentException("offset 不能为负");
        }
    }
}
