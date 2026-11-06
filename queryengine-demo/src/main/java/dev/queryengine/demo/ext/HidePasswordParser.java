package dev.queryengine.demo.ext;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.Method;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.plan.FieldParser;
import dev.queryengine.plan.SelectItem;
import dev.queryengine.plan.TableRequest;

/**
 * 继承 {@link FieldParser}，在默认解析之后拒绝读出 {@code password}。
 */
public class HidePasswordParser extends FieldParser {

    @Override
    protected TableRequest afterParse(TableRequest request, JsonNode body, Method method) {
        for (SelectItem column : request.columns()) {
            if ("password".equalsIgnoreCase(column.name()) || "password".equalsIgnoreCase(column.alias())) {
                throw new BadRequestException("列不可查询: password");
            }
        }
        return request;
    }
}
