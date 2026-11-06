package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.exception.QueryEngineException;
import dev.queryengine.exception.QueryExecutionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        QueryHttpController.class,
        AccessHttpController.class,
        RestHttpController.class,
        GraphqlHttpController.class
})
public class QueryEngineWebExceptionHandler {

    private final ObjectMapper mapper;

    public QueryEngineWebExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<JsonNode> badRequest(BadRequestException exception, HttpServletRequest request) {
        return status(ErrorCodes.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonNode> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return status(ErrorCodes.BAD_REQUEST, "请求不是合法 JSON", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<JsonNode> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return status(ErrorCodes.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<JsonNode> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return status(ErrorCodes.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(QueryExecutionException.class)
    public ResponseEntity<JsonNode> execution(QueryExecutionException exception, HttpServletRequest request) {
        return status(ErrorCodes.INTERNAL, exception.getMessage(), request);
    }

    @ExceptionHandler(QueryEngineException.class)
    public ResponseEntity<JsonNode> engine(QueryEngineException exception, HttpServletRequest request) {
        return status(ErrorCodes.INTERNAL, exception.getMessage(), request);
    }

    private ResponseEntity<JsonNode> status(int code, String message, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && (uri.equals("/graphql") || uri.endsWith("/graphql"))) {
            return ResponseEntity.status(code).body(GraphqlResponses.error(mapper, code, message));
        }
        return ResponseEntity.status(code).body(ResponseEnvelope.failure(mapper, code, message));
    }
}
