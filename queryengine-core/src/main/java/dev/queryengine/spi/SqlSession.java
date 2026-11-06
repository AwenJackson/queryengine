package dev.queryengine.spi;

import java.util.List;
import java.util.Map;

/**
 * 一次请求事务内的语句执行器。实现不得把参数拼进 SQL。
 */
public interface SqlSession {

    List<Map<String, Object>> query(String sql, List<Object> parameters);

    int update(String sql, List<Object> parameters);

    InsertResult insert(String sql, List<Object> parameters);
}
