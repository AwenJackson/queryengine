package dev.queryengine;

import java.util.Locale;

/**
 * 库内操作，由宿主映射到自己的入口。核心不提供 HTTP。
 */
public enum Method {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE;

    public static Method fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("method 不能为空");
        }
        try {
            return Method.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的 method: " + name);
        }
    }
}
