package dev.queryengine.exec;

import dev.queryengine.exception.BadRequestException;
import dev.queryengine.plan.Identifiers;

/**
 * 嵌套插入时，由父表名和子行已有列名推断外键列。
 * 父表名带下划线时只有 {@code book_menu_id} 这一种写法；
 * 否则在 {@code comment_id} 与 {@code commentId} 之间按子行的列名风格选择。
 * 两种都说得通时拒绝，避免写错列。
 */
final class ChildForeignKey {

    private ChildForeignKey() {
    }

    static boolean assigned(String parentTable, Iterable<String> keys) {
        String snake = snakeId(parentTable);
        String camel = camelId(parentTable);
        for (String key : keys) {
            String column = bare(key);
            if (column == null) {
                continue;
            }
            if (column.equals(snake) || (camel != null && column.equals(camel))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 要补上的外键列；子行已经写了候选列时返回 null
     */
    static String choose(String childTable, String parentTable, Iterable<String> keys) {
        if (assigned(parentTable, keys)) {
            return null;
        }
        String snake = snakeId(parentTable);
        String camel = camelId(parentTable);
        if (camel == null) {
            return snake;
        }
        return switch (style(keys)) {
            case SNAKE -> snake;
            case CAMEL -> camel;
            case NONE, MIXED -> throw new BadRequestException(
                    "无法确定 " + childTable + " 相对父表 " + parentTable
                            + " 的外键列，候选为 " + snake + "、" + camel
                            + "。请显式写出该列，或使用引用赋值"
            );
        };
    }

    static String snakeId(String table) {
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < table.length(); i++) {
            char c = table.charAt(i);
            if (c == '_') {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
                prev = '_';
                continue;
            }
            if (Character.isUpperCase(c) && !sb.isEmpty() && prev != '_' && !Character.isUpperCase(prev)) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
            prev = c;
        }
        if (sb.isEmpty() || !Identifiers.isValid(sb + "_id")) {
            throw new BadRequestException("无法从父表推断外键列: " + table);
        }
        sb.append("_id");
        return sb.toString();
    }

    static String camelId(String table) {
        if (table == null || table.isEmpty() || table.indexOf('_') >= 0) {
            return null;
        }
        String column = Character.toLowerCase(table.charAt(0)) + table.substring(1) + "Id";
        if (!Identifiers.isValid(column)) {
            return null;
        }
        return column;
    }

    private static Style style(Iterable<String> keys) {
        boolean snake = false;
        boolean camel = false;
        for (String key : keys) {
            String column = bare(key);
            if (column == null) {
                continue;
            }
            if (column.indexOf('_') >= 0) {
                snake = true;
            } else if (internalUpper(column)) {
                camel = true;
            }
        }
        if (snake && camel) {
            return Style.MIXED;
        }
        if (snake) {
            return Style.SNAKE;
        }
        if (camel) {
            return Style.CAMEL;
        }
        return Style.NONE;
    }

    private static boolean internalUpper(String column) {
        for (int i = 1; i < column.length(); i++) {
            if (Character.isUpperCase(column.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    static String bare(String key) {
        if (key == null || key.isEmpty() || key.startsWith("@")) {
            return null;
        }
        String name = key;
        if (name.endsWith("@")) {
            name = name.substring(0, name.length() - 1);
        } else if (name.endsWith("!{}")) {
            name = name.substring(0, name.length() - 3);
        } else if (name.endsWith("{}")) {
            name = name.substring(0, name.length() - 2);
        } else if (name.endsWith(">=") || name.endsWith("<=")) {
            name = name.substring(0, name.length() - 2);
        } else if (name.endsWith("$") || name.endsWith(">") || name.endsWith("<") || name.endsWith("!")) {
            name = name.substring(0, name.length() - 1);
        }
        if (!Identifiers.isValid(name)) {
            return null;
        }
        return name;
    }

    private enum Style {
        SNAKE, CAMEL, NONE, MIXED
    }
}
