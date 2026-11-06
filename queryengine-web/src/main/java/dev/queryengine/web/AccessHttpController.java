package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.QueryEngine;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.spi.TableGrant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登记、停用和查看当前已启用的表。没有登录态。
 * 成功时业务内容在 {@code data} 下，{@code code} 与 {@code message} 仍由统一信封给出。
 */
@RestController
@RequestMapping("${queryengine.http.base-path:}")
public class AccessHttpController {

    private final QueryEngine engine;
    private final ObjectMapper mapper;

    public AccessHttpController(QueryEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    @PostMapping(path = "/access/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> register(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String headerSchema
    ) {
        ObjectNode request = objectBody(body);
        String name = requiredText(request, "name");
        String alias = optionalText(request, "alias");
        String schema = firstSchema(optionalText(request, "schema"), headerSchema);
        if (alias == null) {
            if (schema == null) {
                engine.registerTable(name);
            } else {
                engine.registerTable(name, schema);
            }
            alias = name;
        } else if (schema == null) {
            engine.mapTable(name, alias);
        } else {
            engine.mapTable(name, alias, schema);
        }
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, grantNode(name, alias, schema)));
    }

    @PostMapping(path = "/access/unregister", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> unregister(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String headerSchema
    ) {
        ObjectNode request = objectBody(body);
        String name = requiredText(request, "name");
        String schema = firstSchema(optionalText(request, "schema"), headerSchema);
        if (schema == null) {
            engine.unregisterTable(name);
        } else {
            engine.unregisterTable(name, schema);
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("name", name);
        if (schema == null) {
            data.putNull("schema");
        } else {
            data.put("schema", schema);
        }
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, data));
    }

    @PostMapping(path = "/access/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> list(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String headerSchema
    ) {
        String schema = null;
        if (body != null && body.isObject()) {
            schema = optionalText((ObjectNode) body, "schema");
        }
        schema = firstSchema(schema, headerSchema);
        ArrayNode data = mapper.createArrayNode();
        for (TableGrant grant : engine.listAccess(schema)) {
            data.add(grantNode(grant.table(), grant.physicalName(), grant.schema()));
        }
        return ResponseEntity.ok(ResponseEnvelope.wrapped(mapper, data));
    }

    private static String firstSchema(String bodySchema, String headerSchema) {
        if (bodySchema != null && !bodySchema.isBlank()) {
            return bodySchema;
        }
        if (headerSchema != null && !headerSchema.isBlank()) {
            return headerSchema.trim();
        }
        return null;
    }

    private ObjectNode grantNode(String name, String alias, String schema) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", name);
        node.put("alias", alias);
        if (schema == null || schema.isBlank()) {
            node.putNull("schema");
        } else {
            node.put("schema", schema);
        }
        return node;
    }

    private static ObjectNode objectBody(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw new BadRequestException("请求必须是 JSON 对象");
        }
        return (ObjectNode) body;
    }

    private static String requiredText(ObjectNode body, String field) {
        String value = optionalText(body, field);
        if (value == null) {
            throw new BadRequestException(field + " 不能为空");
        }
        return value;
    }

    private static String optionalText(ObjectNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new BadRequestException(field + " 必须是字符串");
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
