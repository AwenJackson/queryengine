package dev.queryengine.spi;

import java.util.List;

/**
 * SQL 方言。只负责标识符引用和分页语法，值一律走占位符。
 */
public interface Dialect {

    String name();

    /** 引用数据目录中的标识符（表、列、schema）。列名保持请求中的大小写。 */
    String quote(String identifier);

    /**
     * 表名在进 SQL 前的规范形式。默认不改写。
     * MySQL 实现折成小写，达梦在 {@link #quote} 里折成大写，因此 User / user / USER 指向同一张物理表。
     */
    default String canonicalTable(String table) {
        return table;
    }

    /** 引用结果别名，保留调用方写下的大小写。 */
    String quoteAlias(String alias);

    /**
     * 在已有 SELECT 后追加分页。实现必须把 limit 与 offset 作为占位符追加到 {@code params}。
     */
    void appendPage(StringBuilder sql, List<Object> params, int limit, int offset);
}
