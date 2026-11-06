package dev.queryengine.plan;

import java.util.List;

public record Predicate(String column, Op op, List<Object> values) {

    public Predicate {
        values = List.copyOf(values);
    }

    public static Predicate eq(String column, Object value) {
        if (value == null) {
            return new Predicate(column, Op.IS_NULL, List.of());
        }
        return new Predicate(column, Op.EQ, List.of(value));
    }
}
