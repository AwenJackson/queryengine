package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.queryengine.Method;
import dev.queryengine.QueryEngine;
import dev.queryengine.exception.BadRequestException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NestQL：嵌套 JSON 查询。{@code /get} 与 {@code /nestql/get} 是同一入口，{@code /gets}、{@code /heads} 只是别名。
 * 不读取登录态。可选请求头 {@code X-QueryEngine-Schema} 只覆盖这一次调用的 schema。
 */
@RestController
@RequestMapping("${queryengine.http.base-path:}")
public class QueryHttpController {

    static final String SCHEMA_HEADER = "X-QueryEngine-Schema";

    private final QueryEngine engine;
    private final ObjectMapper mapper;

    public QueryHttpController(QueryEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    @PostMapping(path = {"/get", "/gets", "/nestql/get", "/nestql/gets"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> get(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = SCHEMA_HEADER, required = false) String schema
    ) {
        return execute(Method.GET, body, schema);
    }

    @PostMapping(path = {"/head", "/heads", "/nestql/head", "/nestql/heads"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> head(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = SCHEMA_HEADER, required = false) String schema
    ) {
        return execute(Method.HEAD, body, schema);
    }

    @PostMapping(path = {"/post", "/nestql/post"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> post(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = SCHEMA_HEADER, required = false) String schema
    ) {
        return execute(Method.POST, body, schema);
    }

    @PostMapping(path = {"/put", "/nestql/put"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> put(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = SCHEMA_HEADER, required = false) String schema
    ) {
        return execute(Method.PUT, body, schema);
    }

    @PostMapping(path = {"/delete", "/nestql/delete"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> delete(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = SCHEMA_HEADER, required = false) String schema
    ) {
        return execute(Method.DELETE, body, schema);
    }

    private ResponseEntity<JsonNode> execute(Method method, JsonNode body, String schema) {
        if (body == null || body.isNull()) {
            throw new BadRequestException("请求不能为空");
        }
        String override = schema == null || schema.isBlank() ? null : schema.trim();
        JsonNode data = engine.execute(method, body, override);
        return ResponseEntity.ok(ResponseEnvelope.success(mapper, data));
    }
}
