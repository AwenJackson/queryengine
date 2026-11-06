package dev.queryengine.exception;

/** JDBC 或事务失败。消息里包含参数化 SQL 文本，不包含绑定值。 */
public class QueryExecutionException extends QueryEngineException {

    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
