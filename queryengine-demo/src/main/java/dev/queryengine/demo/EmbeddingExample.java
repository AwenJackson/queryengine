package dev.queryengine.demo;

import dev.queryengine.QueryEngine;
import dev.queryengine.dialect.mysql.MySqlDialect;

import javax.sql.DataSource;

/**
 * 宿主侧大约二十行就能嵌进现有进程。这里没有 HTTP，也没有会话。
 */
public final class EmbeddingExample {

    private EmbeddingExample() {
    }

    public static QueryEngine create(DataSource dataSource) {
        return QueryEngine.builder()
                .dataSource(dataSource)
                .dialect(new MySqlDialect())
                .schema("demo")
                .defaultPageSize(10)
                .maxPageSize(100)
                .queryTimeoutSeconds(5)
                .registerTables("User", "Moment", "Comment", "Book", "Book_menu", "Book_menu_tip", "Type", "Node", "Todo")
                .build();
    }
}
