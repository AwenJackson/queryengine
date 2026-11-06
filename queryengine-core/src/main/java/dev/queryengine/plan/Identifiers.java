package dev.queryengine.plan;

import dev.queryengine.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * 标识符只允许 ASCII 字母、数字和下划线。值不能靠标识符通道进 SQL。
 */
public final class Identifiers {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private Identifiers() {
    }

    public static boolean isValid(String name) {
        return name != null && PATTERN.matcher(name).matches();
    }

    public static void checkProgramming(String name, String what) {
        if (!isValid(name)) {
            throw new IllegalArgumentException(what + " 不是合法标识符: " + name);
        }
    }

    public static void checkRequest(String name, String what) {
        if (!isValid(name)) {
            throw new BadRequestException(what + " 不是合法标识符: " + name);
        }
    }
}
