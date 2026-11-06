package dev.queryengine.authz;

import dev.queryengine.Method;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.plan.Identifiers;
import dev.queryengine.spi.AccessRegistry;
import dev.queryengine.spi.TableGrant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个 schema 一份名单。表名大小写不敏感，同名只保留一条。
 */
public class DefaultAccessRegistry implements AccessRegistry {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, TableGrant>> catalogs = new ConcurrentHashMap<>();

    @Override
    public void register(String requestName, String physicalName, String pinnedSchema) {
        registerIn(pinnedSchema, requestName, physicalName, pinnedSchema);
    }

    @Override
    public void registerIn(String catalogSchema, String requestName, String physicalName, String pinnedSchema) {
        Identifiers.checkProgramming(requestName, "表名");
        Identifiers.checkProgramming(physicalName, "物理表名");
        if (pinnedSchema != null) {
            Identifiers.checkProgramming(pinnedSchema, "schema");
        }
        if (catalogSchema != null && !catalogSchema.isBlank()) {
            Identifiers.checkProgramming(catalogSchema, "schema");
        }
        catalogs.computeIfAbsent(schemaKey(catalogSchema), key -> new ConcurrentHashMap<>())
                .put(fold(requestName), new TableGrant(requestName, physicalName, pinnedSchema, EnumSet.allOf(Method.class)));
    }

    @Override
    public void unregister(String table) {
        if (table == null) {
            throw new IllegalArgumentException("表名不能为空");
        }
        String folded = fold(table);
        for (ConcurrentHashMap<String, TableGrant> catalog : catalogs.values()) {
            catalog.remove(folded);
        }
    }

    @Override
    public void unregister(String catalogSchema, String table) {
        if (table == null) {
            throw new IllegalArgumentException("表名不能为空");
        }
        ConcurrentHashMap<String, TableGrant> catalog = catalogs.get(schemaKey(catalogSchema));
        if (catalog != null) {
            catalog.remove(fold(table));
        }
    }

    @Override
    public void clear() {
        catalogs.clear();
    }

    @Override
    public void clear(String catalogSchema) {
        catalogs.remove(schemaKey(catalogSchema));
    }

    @Override
    public List<TableGrant> list() {
        List<TableGrant> all = new ArrayList<>();
        for (ConcurrentHashMap<String, TableGrant> catalog : catalogs.values()) {
            all.addAll(catalog.values());
        }
        all.sort(Comparator.comparing(grant -> fold(grant.table())));
        return List.copyOf(all);
    }

    @Override
    public List<TableGrant> list(String catalogSchema) {
        ConcurrentHashMap<String, TableGrant> catalog = catalogs.get(schemaKey(catalogSchema));
        if (catalog == null) {
            return List.of();
        }
        return catalog.values().stream()
                .sorted(Comparator.comparing(grant -> fold(grant.table())))
                .toList();
    }

    @Override
    public TableGrant require(String table, Method method) {
        return require(table, method, null);
    }

    @Override
    public TableGrant require(String table, Method method, String callSchema) {
        TableGrant grant = find(callSchema, table);
        if (!allows(grant, method)) {
            throw denied(table);
        }
        return grant;
    }

    private TableGrant find(String schema, String table) {
        if (table == null) {
            return null;
        }
        ConcurrentHashMap<String, TableGrant> catalog = catalogs.get(schemaKey(schema));
        if (catalog == null) {
            return null;
        }
        return catalog.get(fold(table));
    }

    private static boolean allows(TableGrant grant, Method method) {
        return grant != null && grant.allows(method);
    }

    private static AccessDeniedException denied(String table) {
        return new AccessDeniedException("未注册或无权访问的表: " + table);
    }

    static String schemaKey(String schema) {
        return schema == null || schema.isBlank() ? "" : schema.toLowerCase(Locale.ROOT);
    }

    private static String fold(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
