package dev.queryengine.spi;

import dev.queryengine.Method;

import java.util.Set;

/**
 * 一张已注册表的授权。{@code table} 是请求里的键，{@code physicalName} 是 SQL 里的表。
 * {@code schema} 为空表示跟随运行时 schema，而不是钉死在某个库。
 */
public record TableGrant(String table, String physicalName, String schema, Set<Method> methods) {

    public TableGrant {
        if (physicalName == null || physicalName.isBlank()) {
            physicalName = table;
        }
        methods = Set.copyOf(methods);
    }

    public boolean allows(Method method) {
        return methods.contains(method);
    }
}
