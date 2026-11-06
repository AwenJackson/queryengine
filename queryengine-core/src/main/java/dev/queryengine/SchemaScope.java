package dev.queryengine;

/**
 * {@link QueryEngine#withSchema(String)} 返回的范围。关闭时恢复之前的线程 schema。
 */
public interface SchemaScope extends AutoCloseable {

    @Override
    void close();
}
