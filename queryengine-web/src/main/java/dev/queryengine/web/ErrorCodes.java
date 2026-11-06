package dev.queryengine.web;

/**
 * HTTP 响应里的稳定业务码。数值与 HTTP 状态一致。没有登录，因此不使用 401。
 */
public final class ErrorCodes {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int INTERNAL = 500;

    public static final String OK_MESSAGE = "success";

    private ErrorCodes() {
    }
}
