package dev.queryengine.authz;

/**
 * {@code access} 表中的一行。{@code alias} 或 {@code schema} 为空表示沿用逻辑名和运行时 schema。
 */
public record AccessRecord(String name, String alias, String schema, boolean enabled) {
}
