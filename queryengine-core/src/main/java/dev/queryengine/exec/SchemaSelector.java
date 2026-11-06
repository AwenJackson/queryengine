package dev.queryengine.exec;

import dev.queryengine.SchemaScope;
import dev.queryengine.plan.Identifiers;

/**
 * 调用级 schema 覆盖优先于线程 schema，线程 schema 优先于构建时的默认值。
 * 表注册时钉死的 schema 不在这里处理。
 */
public final class SchemaSelector {

    private final String defaultSchema;
    private final ThreadLocal<String> threadSchema = new ThreadLocal<>();

    public SchemaSelector(String defaultSchema) {
        if (defaultSchema != null) {
            Identifiers.checkProgramming(defaultSchema, "schema");
        }
        this.defaultSchema = defaultSchema;
    }

    public String current() {
        String thread = threadSchema.get();
        return thread != null ? thread : defaultSchema;
    }

    public String resolveCall(String override) {
        if (override != null) {
            Identifiers.checkProgramming(override, "schema");
            return override;
        }
        return current();
    }

    public void use(String schema) {
        if (schema == null) {
            threadSchema.remove();
            return;
        }
        Identifiers.checkProgramming(schema, "schema");
        threadSchema.set(schema);
    }

    public void clear() {
        threadSchema.remove();
    }

    public SchemaScope withSchema(String schema) {
        Identifiers.checkProgramming(schema, "schema");
        String previous = threadSchema.get();
        threadSchema.set(schema);
        return () -> {
            if (previous == null) {
                threadSchema.remove();
            } else {
                threadSchema.set(previous);
            }
        };
    }
}
