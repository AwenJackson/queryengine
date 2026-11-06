package dev.queryengine.authz;

import java.util.List;

/** 按 schema 读写各自的 {@code access} 表。实现不得把值拼进 SQL。 */
public interface AccessStore {

    /** {@code schema} 为空时使用存储上配置的默认 schema；默认也空则不加模式前缀。 */
    List<AccessRecord> loadEnabled(String schema);

    void save(String schema, AccessRecord record);

    void disable(String schema, String name);
}
