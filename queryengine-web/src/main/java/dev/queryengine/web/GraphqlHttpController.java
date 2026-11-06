package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.QueryEngine;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.exception.QueryEngineException;
import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

/**
 * GraphQL。{@code POST /graphql}，body 是 {@code { "query", "variables" }}。没有登录。
 */
@RestController
@RequestMapping("${queryengine.http.base-path:}")
public class GraphqlHttpController {

    private final ObjectMapper mapper;
    private final AccessGraphQL schemas;

    public GraphqlHttpController(
            QueryEngine engine,
            ObjectMapper mapper,
            DataSource dataSource,
            ObjectProvider<GraphQLSchemaCustomizer> schemaCustomizers
    ) {
        this.mapper = mapper;
        this.schemas = new AccessGraphQL(engine, mapper, dataSource, schemaCustomizers.getIfAvailable());
    }

    @PostMapping(path = "/graphql", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> graphql(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = QueryHttpController.SCHEMA_HEADER, required = false) String schema
    ) {
        if (body == null || body.isNull() || !body.isObject()) {
            return error(ErrorCodes.BAD_REQUEST, "请求必须是 JSON 对象");
        }
        JsonNode queryNode = body.get("query");
        if (queryNode == null || !queryNode.isTextual() || queryNode.asText().isBlank()) {
            return error(ErrorCodes.BAD_REQUEST, "query 不能为空");
        }
        if (body.has("variables") && !body.get("variables").isNull() && !body.get("variables").isObject()) {
            return error(ErrorCodes.BAD_REQUEST, "variables 必须是 JSON 对象");
        }
        String operation = null;
        if (body.has("operationName") && body.get("operationName").isTextual()) {
            operation = body.get("operationName").asText();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = body.has("variables") && body.get("variables").isObject()
                ? mapper.convertValue(body.get("variables"), Map.class)
                : Map.of();
        String callSchema = schema == null || schema.isBlank() ? null : schema.trim();
        try {
            GraphQL graphQL = schemas.graphQL(callSchema);
            ExecutionInput input = ExecutionInput.newExecutionInput()
                    .query(queryNode.asText())
                    .operationName(operation)
                    .variables(variables)
                    .graphQLContext(Map.of(AccessGraphQL.SCHEMA_KEY, callSchema == null ? "" : callSchema))
                    .build();
            return result(graphQL.execute(input));
        } catch (AccessDeniedException ex) {
            return error(ErrorCodes.FORBIDDEN, ex.getMessage());
        } catch (BadRequestException | IllegalArgumentException ex) {
            return error(ErrorCodes.BAD_REQUEST, ex.getMessage());
        } catch (QueryEngineException ex) {
            return error(ErrorCodes.INTERNAL, ex.getMessage());
        }
    }

    private ResponseEntity<JsonNode> result(ExecutionResult result) {
        ObjectNode body = mapper.valueToTree(result.toSpecification());
        int code = ErrorCodes.OK;
        String message = ErrorCodes.OK_MESSAGE;
        if (!result.getErrors().isEmpty()) {
            int errorCode = result.getErrors().stream().mapToInt(this::codeOf).max().orElse(ErrorCodes.INTERNAL);
            if (!hasData(result.getData())) {
                code = errorCode;
            }
            message = result.getErrors().get(0).getMessage();
            if (body.has("errors") && body.get("errors").isArray()) {
                int index = 0;
                for (JsonNode error : body.get("errors")) {
                    if (error instanceof ObjectNode object && index < result.getErrors().size()) {
                        object.putObject("extensions").put("code", codeOf(result.getErrors().get(index)));
                    }
                    index++;
                }
            }
        }
        GraphqlResponses.extensions(body.putObject("extensions"), code, message);
        return ResponseEntity.status(code).body(body);
    }

    private ResponseEntity<JsonNode> error(int code, String message) {
        return ResponseEntity.status(code).body(GraphqlResponses.error(mapper, code, message));
    }

    private int codeOf(GraphQLError error) {
        Throwable throwable = null;
        if (error instanceof graphql.ExceptionWhileDataFetching fetching) {
            throwable = fetching.getException();
        } else if (error instanceof GraphqlErrorException exception) {
            throwable = exception;
        }
        while (throwable != null) {
            if (throwable instanceof AccessDeniedException) {
                return ErrorCodes.FORBIDDEN;
            }
            if (throwable instanceof BadRequestException || throwable instanceof IllegalArgumentException) {
                return ErrorCodes.BAD_REQUEST;
            }
            if (throwable instanceof QueryEngineException) {
                return ErrorCodes.INTERNAL;
            }
            throwable = throwable.getCause();
        }
        ErrorClassification type = error.getErrorType();
        if (type == ErrorType.InvalidSyntax || type == ErrorType.ValidationError) {
            return ErrorCodes.BAD_REQUEST;
        }
        return ErrorCodes.INTERNAL;
    }

    private static boolean hasData(Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            return data != null;
        }
        for (Object value : map.values()) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }
}
