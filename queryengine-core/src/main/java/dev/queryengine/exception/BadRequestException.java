package dev.queryengine.exception;

/** 请求结构、标识符或操作符无法执行。 */
public class BadRequestException extends QueryEngineException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
