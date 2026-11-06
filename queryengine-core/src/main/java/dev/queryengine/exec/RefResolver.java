package dev.queryengine.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.plan.Identifiers;
import dev.queryengine.plan.JsonValues;
import dev.queryengine.plan.RefHit;

import java.util.ArrayList;
import java.util.List;

/**
 * 引用赋值路径。
 * <ul>
 *   <li>{@code /User/id} 从本次响应根开始</li>
 *   <li>{@code User/id} 从当前元素向外找同级键</li>
 *   <li>{@code []/User/id} 当前数组元素；连续 {@code []} 向外回退一层</li>
 * </ul>
 */
public final class RefResolver {

    public RefHit resolve(String path, RefContext context) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("引用路径为空");
        }
        List<String> parts = split(path);
        List<ObjectNode> stack = context.bottomToTop();
        int level = stack.size();
        JsonNode current;
        int index;
        if (parts.get(0).isEmpty()) {
            current = context.root();
            index = 1;
        } else if ("[]".equals(parts.get(0))) {
            current = null;
            index = 0;
        } else {
            RefContext.JsonScope scope = context.findScope(parts.get(0));
            if (scope == null) {
                return RefHit.missing();
            }
            current = scope.node();
            index = 0;
        }
        for (int i = index; i < parts.size(); i++) {
            String part = parts.get(i);
            if ("[]".equals(part)) {
                level--;
                if (level < 0 || level >= stack.size()) {
                    throw new BadRequestException("引用超出数组层级: " + path);
                }
                current = stack.get(level);
                continue;
            }
            if (!Identifiers.isValid(part)) {
                throw new BadRequestException("引用路径包含非法标识符: " + part);
            }
            if (current == null || current.isNull() || !current.isObject() || !JsonFields.has(current, part)) {
                return RefHit.missing();
            }
            current = JsonFields.get(current, part);
            if (current.isNull()) {
                if (i == parts.size() - 1) {
                    return RefHit.found(null);
                }
                return RefHit.missing();
            }
        }
        if (current == null || current.isNull()) {
            return current == null ? RefHit.missing() : RefHit.found(null);
        }
        if (!current.isValueNode()) {
            throw new BadRequestException("引用必须指向标量: " + path);
        }
        return RefHit.found(JsonValues.scalar(current));
    }

    private static List<String> split(String path) {
        String[] raw = path.split("/", -1);
        List<String> parts = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            if (i == 0 && raw[i].isEmpty()) {
                parts.add("");
                continue;
            }
            if (raw[i].isEmpty()) {
                throw new BadRequestException("引用路径格式不正确: " + path);
            }
            parts.add(raw[i]);
        }
        if (parts.isEmpty()) {
            throw new BadRequestException("引用路径为空");
        }
        return parts;
    }
}
