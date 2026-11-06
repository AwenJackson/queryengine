package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.queryengine.Method;
import dev.queryengine.QueryEngine;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.plan.Identifiers;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * RESTful CRUD。表名走 access 白名单，SQL 仍由 QueryEngine 生成。没有登录。
 */
@RestController
@RequestMapping("${queryengine.http.base-path:}/rest")
public class RestHttpController {

    private final QueryEngine engine;
    private final ObjectMapper mapper;

    public RestHttpController(QueryEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    @GetMapping(path = "/{table}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> list(
            @PathVariable String table,
            @RequestParam Map<String, String> query,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String schema
    ) {
        int page = intParam(query, "page", 0);
        Integer count = query.containsKey("count") ? intParam(query, "count", 0)
                : query.containsKey("size") ? intParam(query, "size", 0) : null;
        ObjectNode spec = mapper.createObjectNode();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            String key = entry.getKey();
            if (key.equals("page") || key.equals("count") || key.equals("size")) {
                continue;
            }
            Identifiers.checkRequest(key, "过滤字段");
            spec.set(key, typed(entry.getValue()));
        }
        ObjectNode root = mapper.createObjectNode();
        ObjectNode array = root.putObject("[]");
        array.put("page", page);
        if (count != null) {
            array.put("count", count);
        }
        array.set(table, spec);
        JsonNode result = engine.execute(Method.GET, root, schemaOf(schema));
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, unwrapList(result, table)));
    }

    @GetMapping(path = "/{table}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> get(
            @PathVariable String table,
            @PathVariable String id,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String schema
    ) {
        ObjectNode root = mapper.createObjectNode();
        root.set(table, idSpec(id));
        JsonNode result = engine.execute(Method.GET, root, schemaOf(schema));
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, result.get(table)));
    }

    @PostMapping(path = "/{table}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> create(
            @PathVariable String table,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String schema
    ) {
        ObjectNode root = mapper.createObjectNode();
        root.set(table, objectBody(body));
        JsonNode result = engine.execute(Method.POST, root, schemaOf(schema));
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, result.get(table)));
    }

    @PutMapping(path = "/{table}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> update(
            @PathVariable String table,
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String schema
    ) {
        ObjectNode spec = objectBody(body).deepCopy();
        if (spec.has("id") && !spec.get("id").isNull() && !sameId(spec.get("id"), id)) {
            throw new BadRequestException("路径中的 id 与请求体不一致");
        }
        spec.set("id", typed(id));
        ObjectNode root = mapper.createObjectNode();
        root.set(table, spec);
        JsonNode result = engine.execute(Method.PUT, root, schemaOf(schema));
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, result.get(table)));
    }

    @DeleteMapping(path = "/{table}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> delete(
            @PathVariable String table,
            @PathVariable String id,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String schema
    ) {
        ObjectNode root = mapper.createObjectNode();
        root.set(table, idSpec(id));
        JsonNode result = engine.execute(Method.DELETE, root, schemaOf(schema));
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, result.get(table)));
    }

    private ObjectNode idSpec(String id) {
        ObjectNode spec = mapper.createObjectNode();
        spec.set("id", typed(id));
        return spec;
    }

    private JsonNode unwrapList(JsonNode result, String table) {
        JsonNode rows = result.get("[]");
        var data = mapper.createArrayNode();
        if (rows != null && rows.isArray()) {
            for (JsonNode item : rows) {
                JsonNode row = item.get(table);
                data.add(row == null ? mapper.nullNode() : row);
            }
        }
        return data;
    }

    private ObjectNode objectBody(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new BadRequestException("请求不能为空");
        }
        if (!body.isObject()) {
            throw new BadRequestException("请求体必须是 JSON 对象");
        }
        return (ObjectNode) body;
    }

    private static int intParam(Map<String, String> query, String name, int fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException(name + " 必须是整数");
        }
    }

    private JsonNode typed(String raw) {
        if (raw == null) {
            return mapper.nullNode();
        }
        String text = raw.trim();
        if (text.matches("-?\\d+")) {
            try {
                return mapper.getNodeFactory().numberNode(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return mapper.getNodeFactory().textNode(raw);
            }
        }
        return mapper.getNodeFactory().textNode(raw);
    }

    private static boolean sameId(JsonNode bodyId, String pathId) {
        return bodyId.asText().equals(pathId) || bodyId.asText().equals(String.valueOf(pathId).trim());
    }

    private static String schemaOf(String schema) {
        return schema == null || schema.isBlank() ? null : schema.trim();
    }
}
