package dev.queryengine.exec;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** 解析引用时可见的响应树：根对象，以及由外到内的数组元素栈。 */
public final class RefContext {

    private final ObjectNode root;
    private final Deque<ObjectNode> items;

    public RefContext(ObjectNode root, Deque<ObjectNode> items) {
        this.root = root;
        this.items = items;
    }

    public ObjectNode root() {
        return root;
    }

    public JsonScope findScope(String key) {
        var iterator = items.iterator();
        while (iterator.hasNext()) {
            ObjectNode item = iterator.next();
            if (JsonFields.has(item, key)) {
                return new JsonScope(item);
            }
        }
        if (JsonFields.has(root, key)) {
            return new JsonScope(root);
        }
        return null;
    }

    /** 栈底是最外层数组元素，栈顶是当前元素。 */
    public List<ObjectNode> bottomToTop() {
        List<ObjectNode> topToBottom = new ArrayList<>();
        items.forEach(topToBottom::add);
        Collections.reverse(topToBottom);
        return topToBottom;
    }

    public static Deque<ObjectNode> newStack() {
        return new ArrayDeque<>();
    }

    /** 只是为了给 findScope 一个非 JsonNode 的标记类型，避免和缺失值混淆。 */
    public record JsonScope(ObjectNode node) {
    }
}
