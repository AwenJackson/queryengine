package dev.queryengine.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.Method;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.exception.QueryExecutionException;
import dev.queryengine.plan.Assignment;
import dev.queryengine.plan.FieldParser;
import dev.queryengine.plan.Op;
import dev.queryengine.plan.JsonValues;
import dev.queryengine.plan.PagePolicy;
import dev.queryengine.plan.PlannedSql;
import dev.queryengine.plan.Predicate;
import dev.queryengine.plan.RefPaths;
import dev.queryengine.plan.SingleTableQuery;
import dev.queryengine.plan.SqlPlanner;
import dev.queryengine.plan.TableRequest;
import dev.queryengine.spi.AccessRegistry;
import dev.queryengine.spi.InsertResult;
import dev.queryengine.spi.SqlExecutor;
import dev.queryengine.spi.SqlSession;
import dev.queryengine.spi.TableGrant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按请求树执行单表语句。嵌套只受调用栈限制，没有协议级深度或条数拒绝。
 * POST 的查询形态数组按插入树执行；PUT / DELETE 仍拒绝这种对象。
 */
public final class RequestExecutor {

    private static final Set<String> ROOT_META = Set.of("tag", "version", "format");
    private static final Set<String> ARRAY_META = Set.of("page", "count", "query", "tag");

    private final AccessRegistry registry;
    private final SqlPlanner planner;
    private final SqlExecutor sqlExecutor;
    private final PagePolicy pagePolicy;
    private final String idColumn;
    private final ObjectMapper mapper = new ObjectMapper();
    private final FieldParser fields;
    private final RefResolver refs = new RefResolver();

    public RequestExecutor(
            AccessRegistry registry,
            SqlPlanner planner,
            SqlExecutor sqlExecutor,
            PagePolicy pagePolicy,
            String idColumn
    ) {
        this(registry, planner, sqlExecutor, pagePolicy, idColumn, new FieldParser());
    }

    public RequestExecutor(
            AccessRegistry registry,
            SqlPlanner planner,
            SqlExecutor sqlExecutor,
            PagePolicy pagePolicy,
            String idColumn,
            FieldParser fieldParser
    ) {
        this.registry = registry;
        this.planner = planner;
        this.sqlExecutor = sqlExecutor;
        this.pagePolicy = pagePolicy;
        this.idColumn = idColumn;
        this.fields = fieldParser == null ? new FieldParser() : fieldParser;
    }

    public JsonNode execute(Method method, JsonNode request, String callSchema) {
        return sqlExecutor.transaction(session -> run(method, request, callSchema, session));
    }

    private ObjectNode run(Method method, JsonNode request, String callSchema, SqlSession session) {
        ObjectNode root = mapper.createObjectNode();
        List<Child> original = childrenOf(request, ROOT_META);
        for (Child child : order(original)) {
            ObjectNode holder = child.kind() == Kind.TABLE ? root : null;
            root.set(child.key(), executeChild(
                    method, child, root, RefContext.newStack(), callSchema, session, null, holder, child.key()
            ));
        }
        return reorder(root, original);
    }

    private JsonNode executeChild(
            Method method,
            Child child,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session
    ) {
        return executeChild(method, child, root, items, callSchema, session, null, null, null);
    }

    private JsonNode executeChild(
            Method method,
            Child child,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session,
            ParentScope parent,
            ObjectNode holder,
            String holderKey
    ) {
        boolean write = isWrite(method);
        return switch (child.kind()) {
            case TABLE -> write
                    ? writeTable(method, child.key(), child.spec(), root, items, callSchema, session, parent, holder, holderKey)
                    : readTable(method, child.key(), child.spec(), root, items, callSchema, session);
            case ARRAY -> {
                if (write && method != Method.POST) {
                    throw new BadRequestException("写操作不接受查询形态的 " + child.key());
                }
                yield write
                        ? writeArray(method, child.key(), child.spec(), root, items, callSchema, session, parent)
                        : readArray(method, child.key(), child.spec(), root, items, callSchema, session);
            }
            case BATCH -> {
                if (!write) {
                    throw new BadRequestException(child.key() + " 在查询中必须是对象");
                }
                yield writeBatch(method, child, root, items, callSchema, session, parent);
            }
        };
    }

    private JsonNode readTable(
            Method method,
            String table,
            JsonNode spec,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session
    ) {
        TableGrant grant = registry.require(table, method, callSchema);
        TableRequest parsed = fields.parse(spec, method, path -> refs.resolve(path, new RefContext(root, items)), idColumn);
        if (method == Method.HEAD) {
            long count = parsed.unresolved() ? 0L : countOf(schemaOf(grant, callSchema), grant.physicalName(), parsed, session);
            ObjectNode node = mapper.createObjectNode();
            node.put("count", count);
            return node;
        }
        if (parsed.unresolved()) {
            return NullNode.getInstance();
        }
        List<Map<String, Object>> rows = select(schemaOf(grant, callSchema), grant.physicalName(), parsed, 2, 0, session);
        if (rows.isEmpty()) {
            return NullNode.getInstance();
        }
        if (rows.size() > 1) {
            throw new BadRequestException("对象查询命中至少 2 行，请改用 [] 数组: " + table);
        }
        return toRow(rows.get(0));
    }

    private JsonNode readArray(
            Method method,
            String key,
            JsonNode spec,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session
    ) {
        List<Child> original = childrenOf(spec, ARRAY_META);
        List<Child> sorted = order(original);
        Child main = sorted.stream()
                .filter(child -> child.kind() == Kind.TABLE)
                .findFirst()
                .orElseThrow(() -> new BadRequestException(key + " 缺少主表"));
        TableGrant grant = registry.require(main.key(), method, callSchema);
        TableRequest parsed = fields.parse(
                main.spec(),
                method,
                path -> refs.resolve(path, new RefContext(root, items)),
                idColumn
        );
        if (method == Method.HEAD) {
            long count = parsed.unresolved()
                    ? 0L
                    : countOf(schemaOf(grant, callSchema), grant.physicalName(), parsed, session);
            ObjectNode node = mapper.createObjectNode();
            node.put("count", count);
            return node;
        }
        if (parsed.unresolved()) {
            return mapper.createArrayNode();
        }
        int page = pagePolicy.page(optionalInt(spec, "page"));
        int count = pagePolicy.count(optionalInt(spec, "count"));
        long offsetLong = (long) page * count;
        if (offsetLong > Integer.MAX_VALUE) {
            throw new BadRequestException("page 与 count 的偏移超出范围");
        }
        List<Map<String, Object>> rows = select(
                schemaOf(grant, callSchema),
                grant.physicalName(),
                parsed,
                count,
                (int) offsetLong,
                session
        );
        ArrayNode array = mapper.createArrayNode();
        for (Map<String, Object> row : rows) {
            ObjectNode item = mapper.createObjectNode();
            item.set(main.key(), toRow(row));
            items.addFirst(item);
            try {
                for (Child child : sorted) {
                    if (child.key().equals(main.key())) {
                        continue;
                    }
                    item.set(child.key(), executeChild(method, child, root, items, callSchema, session));
                }
            } finally {
                items.removeFirst();
            }
            array.add(reorder(item, original));
        }
        return array;
    }

    private ObjectNode writeTable(
            Method method,
            String table,
            JsonNode spec,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session,
            ParentScope parent,
            ObjectNode holder,
            String holderKey
    ) {
        ColumnBody body = splitColumns(spec);
        if (method != Method.POST && !body.nested().isEmpty()) {
            throw new BadRequestException("只有 POST 支持嵌套写入: " + body.nested().get(0).key());
        }
        if (method == Method.POST) {
            applyParentKey(table, body.columns(), parent);
        }
        TableGrant grant = registry.require(table, method, callSchema);
        TableRequest parsed = fields.parse(
                body.columns(),
                method,
                path -> refs.resolve(path, new RefContext(root, items)),
                idColumn
        );
        if (parsed.unresolved()) {
            throw new BadRequestException("引用赋值没有解析到值: " + table);
        }
        String schema = schemaOf(grant, callSchema);
        String physical = grant.physicalName();
        ObjectNode out = switch (method) {
            case POST -> doInsert(schema, physical, parsed, session);
            case PUT -> doUpdate(schema, physical, parsed, session);
            case DELETE -> doDelete(schema, physical, parsed, session);
            default -> throw new IllegalStateException("不是写操作: " + method);
        };
        if (holder != null && holderKey != null) {
            holder.set(holderKey, out);
        }
        if (body.nested().isEmpty()) {
            return out;
        }
        ObjectNode frame = mapper.createObjectNode();
        frame.set(table, out);
        items.addFirst(frame);
        try {
            ParentScope childParent = new ParentScope(table, idOf(out), true);
            for (Child child : body.nested()) {
                out.set(child.key(), executeChild(
                        method, child, root, items, callSchema, session, childParent, null, null
                ));
            }
        } finally {
            items.removeFirst();
        }
        return out;
    }

    private JsonNode writeArray(
            Method method,
            String key,
            JsonNode spec,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session,
            ParentScope parent
    ) {
        rejectInsertMeta(key, spec);
        List<Child> original = childrenOf(spec, ARRAY_META);
        List<Child> sorted = order(original);
        Child main = sorted.stream()
                .filter(child -> child.kind() == Kind.TABLE)
                .findFirst()
                .orElseThrow(() -> new BadRequestException(key + " 缺少主表"));
        long tables = sorted.stream().filter(child -> child.kind() == Kind.TABLE).count();
        ObjectNode row = writeTable(method, main.key(), main.spec(), root, items, callSchema, session, parent, null, null);
        ObjectNode item = mapper.createObjectNode();
        item.set(main.key(), row);
        ParentScope childParent = new ParentScope(main.key(), idOf(row), tables == 1);
        ArrayNode array = mapper.createArrayNode();
        items.addFirst(item);
        try {
            for (Child child : sorted) {
                if (child.key().equals(main.key())) {
                    continue;
                }
                item.set(child.key(), executeChild(
                        method, child, root, items, callSchema, session, childParent, null, null
                ));
            }
        } finally {
            items.removeFirst();
        }
        array.add(reorder(item, original));
        return array;
    }

    private ArrayNode writeBatch(
            Method method,
            Child child,
            ObjectNode root,
            Deque<ObjectNode> items,
            String callSchema,
            SqlSession session,
            ParentScope parent
    ) {
        String table = batchTable(child.key());
        registry.require(table, method, callSchema);
        ArrayNode array = mapper.createArrayNode();
        for (JsonNode element : child.spec()) {
            if (!element.isObject()) {
                throw new BadRequestException("批量写入的元素必须是对象");
            }
            array.add(writeTable(method, table, element, root, items, callSchema, session, parent, null, null));
        }
        return array;
    }

    private void applyParentKey(String childTable, ObjectNode columns, ParentScope parent) {
        if (parent == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        columns.fieldNames().forEachRemaining(keys::add);
        if (!parent.sole()) {
            if (ChildForeignKey.assigned(parent.table(), keys)) {
                return;
            }
            throw new BadRequestException("嵌套写入 " + childTable + " 有多个父表，请显式写出外键列或使用引用赋值");
        }
        String column = ChildForeignKey.choose(childTable, parent.table(), keys);
        if (column == null) {
            return;
        }
        if (parent.id() == null) {
            throw new BadRequestException("无法回填外键 " + column + "，父行没有主键 " + idColumn);
        }
        columns.set(column, mapper.valueToTree(parent.id()));
    }

    private Object idOf(ObjectNode row) {
        JsonNode node = row.get(idColumn);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        return JsonValues.scalar(node);
    }

    private ColumnBody splitColumns(JsonNode spec) {
        if (spec == null || !spec.isObject()) {
            throw new BadRequestException("表请求必须是 JSON 对象");
        }
        ObjectNode columns = mapper.createObjectNode();
        List<Child> nested = new ArrayList<>();
        spec.fields().forEachRemaining(field -> {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (isNestedKey(key)) {
                if (value.isArray()) {
                    nested.add(new Child(Kind.BATCH, key, value));
                    return;
                }
                if (value.isObject()) {
                    nested.add(new Child(Kind.ARRAY, key, value));
                    return;
                }
                throw new BadRequestException("嵌套写入 " + key + " 必须是数组或对象");
            }
            columns.set(key, value);
        });
        return new ColumnBody(columns, nested);
    }

    private static boolean isNestedKey(String key) {
        return "[]".equals(key) || (key.endsWith("[]") && key.length() > 2);
    }

    private static void rejectInsertMeta(String key, JsonNode spec) {
        for (String meta : List.of("page", "count", "query")) {
            JsonNode node = spec.get(meta);
            if (node != null && !node.isObject() && !node.isArray()) {
                throw new BadRequestException("插入形态的 " + key + " 不接受 " + meta);
            }
        }
    }

    private ObjectNode doInsert(String schema, String table, TableRequest parsed, SqlSession session) {
        PlannedSql sql = planner.insert(query(schema, table, parsed, null, 0));
        InsertResult result = session.insert(sql.sql(), sql.parameters());
        ObjectNode out = mapper.createObjectNode();
        boolean hasId = false;
        for (Assignment assignment : parsed.assignments()) {
            out.set(assignment.column(), mapper.valueToTree(assignment.value()));
            if (assignment.column().equals(idColumn)) {
                hasId = true;
            }
        }
        if (!hasId && result.generatedKey() != null) {
            out.set(idColumn, mapper.valueToTree(result.generatedKey()));
        }
        out.put("count", result.affected());
        return out;
    }

    private ObjectNode doUpdate(String schema, String table, TableRequest parsed, SqlSession session) {
        requireId(parsed);
        PlannedSql sql = planner.update(query(schema, table, parsed, null, 0));
        int affected = session.update(sql.sql(), sql.parameters());
        ObjectNode out = echo(parsed);
        out.put("count", affected);
        return out;
    }

    private ObjectNode doDelete(String schema, String table, TableRequest parsed, SqlSession session) {
        requireId(parsed);
        PlannedSql sql = planner.delete(query(schema, table, parsed, null, 0));
        int affected = session.update(sql.sql(), sql.parameters());
        ObjectNode out = echo(parsed);
        out.put("count", affected);
        return out;
    }

    private void requireId(TableRequest parsed) {
        boolean present = parsed.where().stream().anyMatch(predicate -> idColumn.equals(predicate.column()));
        if (!present) {
            throw new BadRequestException("写操作必须包含主键条件 " + idColumn);
        }
    }

    private ObjectNode echo(TableRequest parsed) {
        ObjectNode out = mapper.createObjectNode();
        for (Assignment assignment : parsed.assignments()) {
            out.set(assignment.column(), mapper.valueToTree(assignment.value()));
        }
        for (Predicate predicate : parsed.where()) {
            if (predicate.op() == Op.EQ && !out.has(predicate.column())) {
                out.set(predicate.column(), mapper.valueToTree(predicate.values().get(0)));
            }
        }
        return out;
    }

    private List<Map<String, Object>> select(
            String schema,
            String table,
            TableRequest parsed,
            int limit,
            int offset,
            SqlSession session
    ) {
        PlannedSql sql = planner.select(query(schema, table, parsed, limit, offset));
        return session.query(sql.sql(), sql.parameters());
    }

    private long countOf(String schema, String table, TableRequest parsed, SqlSession session) {
        PlannedSql sql = planner.count(query(schema, table, parsed, null, 0));
        List<Map<String, Object>> rows = session.query(sql.sql(), sql.parameters());
        if (rows.isEmpty() || rows.get(0).isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).values().iterator().next();
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new QueryExecutionException("无法读取计数结果", new IllegalStateException(String.valueOf(value)));
    }

    private static SingleTableQuery query(String schema, String table, TableRequest parsed, Integer limit, int offset) {
        return new SingleTableQuery(
                schema,
                table,
                parsed.columns(),
                parsed.where(),
                parsed.orderBy(),
                limit,
                offset,
                parsed.assignments()
        );
    }

    private static String schemaOf(TableGrant grant, String callSchema) {
        return grant.schema() != null ? grant.schema() : callSchema;
    }

    private ObjectNode toRow(Map<String, Object> row) {
        ObjectNode node = mapper.createObjectNode();
        row.forEach((key, value) -> node.set(key, mapper.valueToTree(value)));
        return node;
    }

    private ObjectNode reorder(ObjectNode source, List<Child> order) {
        ObjectNode out = mapper.createObjectNode();
        for (Child child : order) {
            if (source.has(child.key())) {
                out.set(child.key(), source.get(child.key()));
            }
        }
        return out;
    }

    private static boolean isWrite(Method method) {
        return method == Method.POST || method == Method.PUT || method == Method.DELETE;
    }

    private static String batchTable(String key) {
        if (!key.endsWith("[]") || key.length() <= 2) {
            throw new BadRequestException("批量写入需要表名，例如 Todo[]");
        }
        return key.substring(0, key.length() - 2);
    }

    private static Integer optionalInt(JsonNode spec, String field) {
        JsonNode node = spec.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new BadRequestException(field + " 必须是整数");
        }
        return node.intValue();
    }

    private static List<Child> childrenOf(JsonNode spec, Set<String> scalarMeta) {
        List<Child> children = new ArrayList<>();
        spec.fields().forEachRemaining(field -> {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (key.startsWith("@")) {
                throw new BadRequestException("不支持的关键词: " + key);
            }
            if (key.endsWith("[]") && value.isArray()) {
                children.add(new Child(Kind.BATCH, key, value));
                return;
            }
            if ((key.equals("[]") || key.endsWith("[]")) && value.isObject()) {
                children.add(new Child(Kind.ARRAY, key, value));
                return;
            }
            if (value.isObject()) {
                children.add(new Child(Kind.TABLE, key, value));
                return;
            }
            if (scalarMeta.contains(key)) {
                return;
            }
            throw new BadRequestException("无法识别的字段: " + key);
        });
        return children;
    }

    private static List<Child> order(List<Child> children) {
        Map<String, Child> byKey = new LinkedHashMap<>();
        for (Child child : children) {
            if (byKey.put(child.key(), child) != null) {
                throw new BadRequestException("重复的键: " + child.key());
            }
        }
        Set<String> siblings = byKey.keySet();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (String key : siblings) {
            indegree.put(key, 0);
            dependents.put(key, new ArrayList<>());
        }
        for (Child child : children) {
            for (String dependency : dependencies(child.spec(), siblings)) {
                if (dependency.equals(child.key()) || !siblings.contains(dependency)) {
                    continue;
                }
                indegree.merge(child.key(), 1, Integer::sum);
                dependents.get(dependency).add(child.key());
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (String key : siblings) {
            if (indegree.get(key) == 0) {
                ready.add(key);
            }
        }
        List<Child> sorted = new ArrayList<>();
        while (!ready.isEmpty()) {
            String key = ready.removeFirst();
            sorted.add(byKey.get(key));
            for (String dependent : dependents.get(key)) {
                int next = indegree.merge(dependent, -1, Integer::sum);
                if (next == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (sorted.size() != byKey.size()) {
            throw new BadRequestException("引用赋值存在循环依赖");
        }
        return sorted;
    }

    private static String matchSibling(java.util.Set<String> siblings, String hop) {
        if (hop == null) {
            return null;
        }
        for (String sibling : siblings) {
            if (sibling.equalsIgnoreCase(hop)) {
                return sibling;
            }
        }
        return null;
    }

    private static Set<String> dependencies(JsonNode spec, Set<String> siblings) {
        Set<String> deps = new LinkedHashSet<>();
        walk(spec, siblings, deps);
        return deps;
    }

    private static void walk(JsonNode node, Set<String> siblings, Set<String> deps) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (key.endsWith("@") && !key.startsWith("@") && value.isTextual()) {
                    String hop = RefPaths.firstHop(value.asText());
                    String matched = matchSibling(siblings, hop);
                    if (matched != null) {
                        deps.add(matched);
                    }
                }
                walk(value, siblings, deps);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> walk(item, siblings, deps));
        }
    }

    private enum Kind {
        TABLE, ARRAY, BATCH
    }

    private record Child(Kind kind, String key, JsonNode spec) {
    }

    /** 嵌套子行可见的父表。{@code sole} 为 false 时有多张父表，不能猜测外键列。 */
    private record ParentScope(String table, Object id, boolean sole) {
    }

    private record ColumnBody(ObjectNode columns, List<Child> nested) {
    }
}
