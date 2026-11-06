package dev.queryengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.queryengine.authz.AccessRecord;
import dev.queryengine.authz.AccessStore;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.exec.RequestExecutor;
import dev.queryengine.exec.SchemaSelector;
import dev.queryengine.spi.AccessRegistry;
import dev.queryengine.spi.TableGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 宿主嵌入的查询入口。库调用不创建会话，失败时抛出
 * {@link dev.queryengine.exception.QueryEngineException} 及其子类。
 * HTTP 与统一的 {@code code}/{@code message} 封装在 {@code queryengine-web}。
 */
public final class QueryEngine {

    private static final Logger log = LoggerFactory.getLogger(QueryEngine.class);

    private final AccessRegistry registry;
    private final AccessStore accessStore;
    private final SchemaSelector schemas;
    private final RequestExecutor executor;
    private final String dialectName;
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.util.Set<String> loadedSchemas = java.util.concurrent.ConcurrentHashMap.newKeySet();

    QueryEngine(
            AccessRegistry registry,
            AccessStore accessStore,
            SchemaSelector schemas,
            RequestExecutor executor,
            String dialectName
    ) {
        this.registry = registry;
        this.accessStore = accessStore;
        this.schemas = schemas;
        this.executor = executor;
        this.dialectName = dialectName;
    }

    public static QueryEngineBuilder builder() {
        return new QueryEngineBuilder();
    }

    public JsonNode execute(Method method, JsonNode request) {
        return execute(method, request, null);
    }

    /**
     * @param schema 本次调用的 schema，覆盖线程值和默认值；已钉死 schema 的表不受影响。null 表示不覆盖。
     */
    public JsonNode execute(Method method, JsonNode request, String schema) {
        if (method == null) {
            throw new IllegalArgumentException("method 不能为空");
        }
        if (request == null || request.isNull() || !request.isObject()) {
            throw new BadRequestException("请求必须是 JSON 对象");
        }
        String callSchema = schemas.resolveCall(schema);
        ensureLoaded(callSchema);
        return executor.execute(method, request, callSchema);
    }

    public JsonNode execute(Method method, String json) {
        return execute(method, read(json), null);
    }

    public JsonNode execute(Method method, String json, String schema) {
        return execute(method, read(json), schema);
    }

    public JsonNode get(String json) {
        return execute(Method.GET, json);
    }

    public JsonNode get(JsonNode request) {
        return execute(Method.GET, request);
    }

    public JsonNode head(String json) {
        return execute(Method.HEAD, json);
    }

    public JsonNode head(JsonNode request) {
        return execute(Method.HEAD, request);
    }

    public JsonNode post(String json) {
        return execute(Method.POST, json);
    }

    public JsonNode post(JsonNode request) {
        return execute(Method.POST, request);
    }

    public JsonNode put(String json) {
        return execute(Method.PUT, json);
    }

    public JsonNode put(JsonNode request) {
        return execute(Method.PUT, request);
    }

    public JsonNode delete(String json) {
        return execute(Method.DELETE, json);
    }

    public JsonNode delete(JsonNode request) {
        return execute(Method.DELETE, request);
    }

    /** 注册到当前 schema 的名单，并写入该 schema 的 access 表。立刻可查。 */
    public void registerTable(String table) {
        registerInto(currentSchema(), table, table, null);
    }

    /** 注册到指定 schema 的名单并钉死 SQL schema，同时写入该 schema 的 access 表。 */
    public void registerTable(String table, String schema) {
        registerInto(schema, table, table, schema);
    }

    /**
     * 请求键用 {@code requestName}，SQL 用 {@code physicalName}。授予全部 method。
     * 例如 {@code mapTable("User", "user")} 让 {@code {"User":{}}} 查询物理表 {@code user}。
     */
    public void mapTable(String requestName, String physicalName) {
        registerInto(currentSchema(), requestName, physicalName, null);
    }

    /** 写入指定 schema 的 access 表，并钉死 SQL schema。 */
    public void mapTable(String requestName, String physicalName, String schema) {
        registerInto(schema, requestName, physicalName, schema);
    }

    public void registerTables(String... tables) {
        if (tables == null) {
            throw new IllegalArgumentException("tables 不能为空");
        }
        for (String table : tables) {
            registerTable(table);
        }
    }

    public void unregisterTable(String table) {
        unregisterTable(table, currentSchema());
    }

    /** 停用指定 schema 名单里的表，并把该 schema 的 access 行设为 enabled=0。 */
    public void unregisterTable(String table, String schema) {
        ensureLoaded(schema);
        registry.unregister(schema, table);
        if (accessStore != null) {
            accessStore.disable(schema, table);
        }
    }

    /** 刷新当前 schema 的名单。 */
    public void reloadAccess() {
        reloadAccess(currentSchema());
    }

    /** 丢掉该 schema 的缓存，下次使用前从它自己的 access 表重新加载。 */
    public void reloadAccess(String schema) {
        loadedSchemas.remove(schemaKey(schema));
        registry.clear(schema);
        ensureLoaded(schema);
    }

    /** 当前 schema 已启用的表。第一次调用会加载并缓存。 */
    public java.util.List<TableGrant> listAccess() {
        return listAccess(currentSchema());
    }

    /** 指定 schema 已启用的表。第一次调用会加载并缓存。 */
    public java.util.List<TableGrant> listAccess(String schema) {
        String target = schema == null || schema.isBlank() ? currentSchema() : schema;
        ensureLoaded(target);
        return registry.list(target);
    }

    public AccessRegistry accessRegistry() {
        return registry;
    }

    public String dialectName() {
        return dialectName;
    }

    /** 当前线程生效的默认 schema，不含单表钉死值。 */
    public String currentSchema() {
        return schemas.current();
    }

    /** 设置当前线程的 schema。null 表示清除线程值，回退到构建时的默认 schema。 */
    public void useSchema(String schema) {
        schemas.use(schema);
    }

    public void clearSchema() {
        schemas.clear();
    }

    /** 在当前线程临时切换 schema，关闭时恢复进入前的线程值。 */
    public SchemaScope withSchema(String schema) {
        return schemas.withSchema(schema);
    }

    private void registerInto(String catalogSchema, String name, String physical, String pinnedSchema) {
        ensureLoaded(catalogSchema);
        registry.registerIn(catalogSchema, name, physical, pinnedSchema);
        if (accessStore != null) {
            accessStore.save(catalogSchema, new AccessRecord(name, physical, pinnedSchema, true));
        }
    }

    private void ensureLoaded(String schema) {
        if (accessStore == null) {
            return;
        }
        String key = schemaKey(schema);
        if (!loadedSchemas.add(key)) {
            return;
        }
        try {
            for (AccessRecord record : accessStore.loadEnabled(schema)) {
                String physical = record.alias() == null ? record.name() : record.alias();
                registry.registerIn(schema, record.name(), physical, record.schema());
            }
        } catch (RuntimeException ex) {
            log.warn("未能加载 schema {} 的 access 表，该 schema 暂无数据库授权: {}", schema, ex.getMessage());
        }
    }

    private static String schemaKey(String schema) {
        return schema == null || schema.isBlank() ? "" : schema.toLowerCase(java.util.Locale.ROOT);
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            throw new BadRequestException("请求不能为空");
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("请求不是合法 JSON", ex);
        }
    }
}
