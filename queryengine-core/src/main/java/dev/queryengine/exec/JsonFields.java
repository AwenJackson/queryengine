package dev.queryengine.exec;

import com.fasterxml.jackson.databind.JsonNode;

/** 对象字段按大小写不敏感查找，供表名引用使用。列名仍走精确键。 */
final class JsonFields {

    private JsonFields() {
    }

    static boolean has(JsonNode node, String key) {
        return get(node, key) != null;
    }

    static JsonNode get(JsonNode node, String key) {
        if (node == null || !node.isObject() || key == null) {
            return null;
        }
        if (node.has(key)) {
            return node.get(key);
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (field.getKey().equalsIgnoreCase(key)) {
                return field.getValue();
            }
        }
        return null;
    }
}