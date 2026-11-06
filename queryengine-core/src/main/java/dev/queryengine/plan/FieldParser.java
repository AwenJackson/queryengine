package dev.queryengine.plan;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.Method;
import dev.queryengine.exception.BadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把单表 JSON 对象解析成条件、赋值、列和排序。不生成 SQL。
 */
public class FieldParser {

    public TableRequest parse(JsonNode body, Method method, ReferenceLookup refs, String idColumn) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("表请求必须是 JSON 对象");
        }
        Identifiers.checkProgramming(idColumn, "主键列");
        List<SelectItem> columns = new ArrayList<>();
        List<OrderBy> orderBy = new ArrayList<>();
        List<Predicate> where = new ArrayList<>();
        List<Assignment> assignments = new ArrayList<>();
        boolean unresolved = false;
        boolean write = isWrite(method);
        Set<String> assigned = new HashSet<>();

        var fields = body.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (key.startsWith("@")) {
                if (write) {
                    throw new BadRequestException("写操作不支持功能符: " + key);
                }
                if ("@column".equals(key)) {
                    columns = parseColumns(value);
                } else if ("@order".equals(key)) {
                    orderBy = parseOrder(value);
                } else {
                    throw new BadRequestException("不支持的功能符: " + key);
                }
                continue;
            }
            if (key.endsWith("@")) {
                String column = key.substring(0, key.length() - 1);
                Identifiers.checkRequest(column, "列名");
                if (!value.isTextual()) {
                    throw new BadRequestException("引用赋值的值必须是路径字符串: " + key);
                }
                RefHit hit = refs.lookup(value.asText());
                if (!hit.found()) {
                    unresolved = true;
                    continue;
                }
                placeValue(method, idColumn, column, hit.value(), where, assignments, assigned);
                continue;
            }
            KeyOp op = KeyOp.parse(key);
            Identifiers.checkRequest(op.column(), "列名");
            if (method == Method.POST && op.kind() != Kind.EQ) {
                throw new BadRequestException("插入只接受字段值，不支持条件运算符: " + key);
            }
            if (isCondition(method, idColumn, op)) {
                where.addAll(predicates(op, value));
            } else {
                addAssignment(assignments, assigned, op.column(), JsonValues.scalar(value));
            }
        }
        return afterParse(new TableRequest(columns, orderBy, where, assignments, unresolved), body, method);
    }

    /** 子类在默认解析完成之后改条件、列或排序。不要在这里拼 SQL。 */
    protected TableRequest afterParse(TableRequest request, JsonNode body, Method method) {
        return request;
    }

    private static boolean isWrite(Method method) {
        return method == Method.POST || method == Method.PUT || method == Method.DELETE;
    }

    private static boolean isCondition(Method method, String idColumn, KeyOp op) {
        return switch (method) {
            case GET, HEAD, DELETE -> true;
            case POST -> false;
            case PUT -> op.kind() != Kind.EQ || op.column().equals(idColumn);
        };
    }

    private static void placeValue(
            Method method,
            String idColumn,
            String column,
            Object value,
            List<Predicate> where,
            List<Assignment> assignments,
            Set<String> assigned
    ) {
        boolean condition = switch (method) {
            case GET, HEAD, DELETE -> true;
            case POST -> false;
            case PUT -> column.equals(idColumn);
        };
        if (condition) {
            where.add(Predicate.eq(column, value));
        } else {
            addAssignment(assignments, assigned, column, value);
        }
    }

    private static void addAssignment(List<Assignment> assignments, Set<String> assigned, String column, Object value) {
        if (!assigned.add(column)) {
            throw new BadRequestException("重复的字段: " + column);
        }
        assignments.add(new Assignment(column, value));
    }

    private static List<Predicate> predicates(KeyOp op, JsonNode value) {
        return switch (op.kind()) {
            case EQ -> List.of(Predicate.eq(op.column(), JsonValues.scalar(value)));
            case NE -> {
                if (value == null || value.isNull()) {
                    yield List.of(new Predicate(op.column(), Op.NOT_NULL, List.of()));
                }
                yield List.of(new Predicate(op.column(), Op.NE, List.of(JsonValues.scalar(value))));
            }
            case GT -> List.of(compare(op.column(), Op.GT, value));
            case GTE -> List.of(compare(op.column(), Op.GTE, value));
            case LT -> List.of(compare(op.column(), Op.LT, value));
            case LTE -> List.of(compare(op.column(), Op.LTE, value));
            case IN -> inPredicates(op.column(), Op.IN, value);
            case NOT_IN -> inPredicates(op.column(), Op.NOT_IN, value);
            case LIKE -> List.of(new Predicate(op.column(), Op.LIKE, likeValues(value)));
        };
    }

    private static Predicate compare(String column, Op op, JsonNode value) {
        if (value != null && value.isArray()) {
            throw new BadRequestException("比较运算符不接受数组: " + column);
        }
        Object scalar = JsonValues.scalar(value);
        if (scalar == null) {
            throw new BadRequestException("比较运算符不接受 null: " + column);
        }
        return new Predicate(column, op, List.of(scalar));
    }

    private static List<Predicate> inPredicates(String column, Op op, JsonNode value) {
        if (value != null && value.isArray()) {
            return List.of(new Predicate(column, op, JsonValues.array(value)));
        }
        if (value != null && value.isTextual()) {
            String text = value.asText();
            if (isRange(text)) {
                if (op != Op.IN) {
                    throw new BadRequestException("区间写法只适用于 {} : " + column);
                }
                return range(column, text);
            }
            if (text.contains(",")) {
                List<Object> values = new ArrayList<>();
                for (String part : text.split(",")) {
                    values.add(JsonValues.scalarToken(part));
                }
                return List.of(new Predicate(column, op, values));
            }
        }
        Object scalar = JsonValues.scalar(value);
        if (scalar == null) {
            throw new BadRequestException("IN 不接受 null: " + column);
        }
        return List.of(new Predicate(column, op, List.of(scalar)));
    }

    private static boolean isRange(String text) {
        for (String part : text.split(",")) {
            String token = part.trim();
            if (token.startsWith(">=") || token.startsWith("<=") || token.startsWith(">") || token.startsWith("<")) {
                return true;
            }
        }
        return false;
    }

    private static List<Predicate> range(String column, String text) {
        List<Predicate> predicates = new ArrayList<>();
        for (String part : text.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            Op op;
            String raw;
            if (token.startsWith(">=")) {
                op = Op.GTE;
                raw = token.substring(2);
            } else if (token.startsWith("<=")) {
                op = Op.LTE;
                raw = token.substring(2);
            } else if (token.startsWith(">")) {
                op = Op.GT;
                raw = token.substring(1);
            } else if (token.startsWith("<")) {
                op = Op.LT;
                raw = token.substring(1);
            } else {
                throw new BadRequestException("无法解析区间: " + text);
            }
            predicates.add(new Predicate(column, op, List.of(JsonValues.scalarToken(raw))));
        }
        if (predicates.isEmpty()) {
            throw new BadRequestException("区间条件为空");
        }
        return predicates;
    }

    private static List<Object> likeValues(JsonNode value) {
        if (value != null && value.isArray()) {
            List<Object> values = JsonValues.array(value);
            for (Object item : values) {
                if (!(item instanceof String)) {
                    throw new BadRequestException("LIKE 的值必须是字符串");
                }
            }
            return values;
        }
        Object scalar = JsonValues.scalar(value);
        if (!(scalar instanceof String)) {
            throw new BadRequestException("LIKE 的值必须是字符串");
        }
        return List.of(scalar);
    }

    private static List<SelectItem> parseColumns(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BadRequestException("@column 必须是非空字符串");
        }
        List<SelectItem> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String part : value.asText().split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new BadRequestException("@column 包含空列");
            }
            if (token.indexOf('(') >= 0 || token.indexOf(')') >= 0) {
                throw new BadRequestException("@column 不支持函数或表达式: " + token);
            }
            String name;
            String alias;
            int colon = token.indexOf(':');
            if (colon < 0) {
                name = token;
                alias = null;
            } else {
                name = token.substring(0, colon).trim();
                alias = token.substring(colon + 1).trim();
                if (alias.isEmpty()) {
                    throw new BadRequestException("@column 别名为空: " + token);
                }
                Identifiers.checkRequest(alias, "别名");
            }
            Identifiers.checkRequest(name, "列名");
            String output = alias == null ? name : alias;
            if (!names.add(output)) {
                throw new BadRequestException("重复的输出列: " + output);
            }
            items.add(new SelectItem(name, alias));
        }
        return items;
    }

    private static List<OrderBy> parseOrder(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BadRequestException("@order 必须是非空字符串");
        }
        List<OrderBy> order = new ArrayList<>();
        for (String part : value.asText().split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new BadRequestException("@order 包含空排序项");
            }
            boolean descending = false;
            String name = token;
            if (token.endsWith("-")) {
                descending = true;
                name = token.substring(0, token.length() - 1).trim();
            } else if (token.endsWith("+")) {
                name = token.substring(0, token.length() - 1).trim();
            }
            Identifiers.checkRequest(name, "排序列");
            order.add(new OrderBy(name, descending));
        }
        return order;
    }

    private enum Kind {
        EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, LIKE
    }

    private record KeyOp(String column, Kind kind) {

        static KeyOp parse(String key) {
            if (key.endsWith("!{}")) {
                return new KeyOp(key.substring(0, key.length() - 3), Kind.NOT_IN);
            }
            if (key.endsWith("{}")) {
                return new KeyOp(key.substring(0, key.length() - 2), Kind.IN);
            }
            if (key.endsWith("$")) {
                return new KeyOp(key.substring(0, key.length() - 1), Kind.LIKE);
            }
            if (key.endsWith(">=")) {
                return new KeyOp(key.substring(0, key.length() - 2), Kind.GTE);
            }
            if (key.endsWith("<=")) {
                return new KeyOp(key.substring(0, key.length() - 2), Kind.LTE);
            }
            if (key.endsWith(">")) {
                return new KeyOp(key.substring(0, key.length() - 1), Kind.GT);
            }
            if (key.endsWith("<")) {
                return new KeyOp(key.substring(0, key.length() - 1), Kind.LT);
            }
            if (key.endsWith("!")) {
                return new KeyOp(key.substring(0, key.length() - 1), Kind.NE);
            }
            return new KeyOp(key, Kind.EQ);
        }
    }
}
