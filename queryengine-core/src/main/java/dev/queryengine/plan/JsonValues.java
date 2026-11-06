package dev.queryengine.plan;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class JsonValues {

    private JsonValues() {
    }

    public static Object scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        throw new BadRequestException("条件值必须是标量");
    }

    public static Object scalarToken(String token) {
        String text = token.trim();
        if (text.isEmpty()) {
            throw new BadRequestException("条件值为空");
        }
        if (text.matches("-?\\d+")) {
            return Long.parseLong(text);
        }
        if (text.matches("-?\\d+\\.\\d+")) {
            return new BigDecimal(text);
        }
        return text;
    }

    public static List<Object> array(JsonNode node) {
        if (!node.isArray()) {
            throw new BadRequestException("这里需要 JSON 数组");
        }
        List<Object> values = new ArrayList<>();
        node.forEach(item -> {
            Object value = scalar(item);
            if (value == null) {
                throw new BadRequestException("IN / LIKE 列表不能包含 null");
            }
            values.add(value);
        });
        return values;
    }
}
