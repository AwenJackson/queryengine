package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ResponseEnvelope {

    private static volatile ResponseCustomizer customizer = body -> body;

    private ResponseEnvelope() {
    }

    static void bind(ResponseCustomizer next) {
        customizer = next == null ? body -> body : next;
    }

    static ObjectNode success(ObjectMapper mapper, JsonNode data) {
        ObjectNode body = data != null && data.isObject()
                ? ((ObjectNode) data).deepCopy()
                : mapper.createObjectNode();
        body.put("code", ErrorCodes.OK);
        body.put("message", ErrorCodes.OK_MESSAGE);
        return apply(body);
    }

    /** 管理接口用：业务内容放在 {@code data} 下，不与查询结果混在同一层。 */
    static ObjectNode wrapped(ObjectMapper mapper, JsonNode data) {
        ObjectNode body = mapper.createObjectNode();
        body.put("code", ErrorCodes.OK);
        body.put("message", ErrorCodes.OK_MESSAGE);
        body.set("data", data == null ? mapper.nullNode() : data);
        return apply(body);
    }

    static ObjectNode failure(ObjectMapper mapper, int code, String message) {
        ObjectNode body = mapper.createObjectNode();
        body.put("code", code);
        body.put("message", message == null || message.isBlank() ? "请求失败" : message);
        return apply(body);
    }

    private static ObjectNode apply(ObjectNode body) {
        ObjectNode custom = customizer.customize(body);
        return custom == null ? body : custom;
    }
}
