package dev.queryengine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class GraphqlResponses {

    private GraphqlResponses() {
    }

    static ObjectNode error(ObjectMapper mapper, int code, String message) {
        ObjectNode body = mapper.createObjectNode();
        body.putNull("data");
        ObjectNode error = body.putArray("errors").addObject();
        error.put("message", message == null || message.isBlank() ? "请求失败" : message);
        error.putObject("extensions").put("code", code);
        extensions(body.putObject("extensions"), code, message);
        return body;
    }

    static void extensions(ObjectNode node, int code, String message) {
        node.put("code", code);
        node.put("message", message == null || message.isBlank() ? "请求失败" : message);
    }
}
