package dev.queryengine.spi;

import java.util.function.Function;

/**
 * 执行入口。一次 {@link #transaction} 对应一个 JDBC 连接和本地事务。
 */
public interface SqlExecutor {

    <T> T transaction(Function<SqlSession, T> work);
}
