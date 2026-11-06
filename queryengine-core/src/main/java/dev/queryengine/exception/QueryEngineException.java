package dev.queryengine.exception;

/**
 * 引擎失败的根类型。没有统一错误码，宿主按异常类型自行映射。
 */
public class QueryEngineException extends RuntimeException {

    public QueryEngineException(String message) {
        super(message);
    }

    public QueryEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
