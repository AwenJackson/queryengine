package dev.queryengine.spi;

import dev.queryengine.Method;
import dev.queryengine.exception.AccessDeniedException;

import java.util.List;

/**
 * 按 schema 分开的表级授权。{@link #register} 必须授予全部 {@link Method}。
 * 更细的角色由宿主在调用引擎之前处理，核心没有会话。
 */
public interface AccessRegistry {

    /**
     * 注册请求键并授予全部 method。{@code physicalName} 是 SQL 表名。
     * {@code pinnedSchema} 为 null 时放进不限定 schema 的兜底名单，只在本次调用也没有 schema 时生效。
     * 非空时放进该 schema 的名单，并钉死 SQL schema。不会授权给其他 schema。
     */
    void register(String requestName, String physicalName, String pinnedSchema);

    /**
     * 放进 {@code catalogSchema} 的名单。{@code catalogSchema} 为空表示兜底名单。
     * {@code pinnedSchema} 为空表示 SQL 跟随本次调用的 schema。
     */
    void registerIn(String catalogSchema, String requestName, String physicalName, String pinnedSchema);

    /**
     * 注册表并授予全部 method。物理表名与请求键相同。{@code schema} 为 null 时跟随运行时 schema。
     */
    default void register(String table, String schema) {
        register(table, table, schema);
    }

    void unregister(String table);

    /** 只从指定 schema 的名单移除。schema 为空时移除兜底名单中的项。 */
    void unregister(String catalogSchema, String table);

    /** 清空全部内存登记。不写数据库。 */
    void clear();

    /** 只清空一个 schema 的内存名单。schema 为空时清空兜底名单。 */
    void clear(String catalogSchema);

    /** 当前内存里全部已启用的表。 */
    List<TableGrant> list();

    /** 某一个 schema 名单里的表。schema 为空时返回兜底名单。 */
    List<TableGrant> list(String catalogSchema);

    /**
     * @throws AccessDeniedException 未注册，或该 method 不在授权内
     */
    TableGrant require(String table, Method method);

    /**
     * 只检查本次调用 schema 的名单，与 {@link #list(String)} 同一份缓存。
     * {@code schema_name} 只影响命中之后的 SQL schema，不能拿别的 schema 的名单顶上。
     */
    TableGrant require(String table, Method method, String callSchema);
}
