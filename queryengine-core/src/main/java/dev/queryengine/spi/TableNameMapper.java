package dev.queryengine.spi;

/**
 * 请求里的逻辑表名到物理表名。返回 {@code null} 表示沿用登记时的名字。
 * 表名大小写仍由 {@link Dialect#canonicalTable} 处理，这里不要自己拼 SQL。
 */
@FunctionalInterface
public interface TableNameMapper {

    String physicalName(String logicalName);
}
