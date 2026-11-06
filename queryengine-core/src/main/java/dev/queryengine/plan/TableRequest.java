package dev.queryengine.plan;

import java.util.List;

/**
 * 一张表上已经解析完的请求。{@code unresolved} 表示引用路径没有落到已有数据。
 */
public record TableRequest(
        List<SelectItem> columns,
        List<OrderBy> orderBy,
        List<Predicate> where,
        List<Assignment> assignments,
        boolean unresolved
) {

    public TableRequest {
        columns = List.copyOf(columns);
        orderBy = List.copyOf(orderBy);
        where = List.copyOf(where);
        assignments = List.copyOf(assignments);
    }
}
