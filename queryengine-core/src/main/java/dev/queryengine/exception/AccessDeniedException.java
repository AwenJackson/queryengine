package dev.queryengine.exception;

/** 表未注册，或当前 method 不在授权集合内。 */
public class AccessDeniedException extends QueryEngineException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
