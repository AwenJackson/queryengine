package dev.queryengine.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "queryengine")
public class QueryEngineProperties {

    private DialectKind dialect = DialectKind.MYSQL;
    private String schema;
    private int defaultPageSize = 10;
    private int maxPageSize = 100;
    private int maxPageIndex = 0;
    private int queryTimeoutSeconds = 0;
    private String idColumn = "id";
    private boolean damengUppercaseIdentifiers = true;
    private List<String> tables = new ArrayList<>();
    private Map<String, String> tableSchemas = new LinkedHashMap<>();
    /** 请求键到物理表名，例如 User → user。仅作 access 表之外的兜底。 */
    private Map<String, String> tableNames = new LinkedHashMap<>();
    private Access access = new Access();

    public DialectKind getDialect() {
        return dialect;
    }

    public void setDialect(DialectKind dialect) {
        this.dialect = dialect;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxPageIndex() {
        return maxPageIndex;
    }

    public void setMaxPageIndex(int maxPageIndex) {
        this.maxPageIndex = maxPageIndex;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    public boolean isDamengUppercaseIdentifiers() {
        return damengUppercaseIdentifiers;
    }

    public void setDamengUppercaseIdentifiers(boolean damengUppercaseIdentifiers) {
        this.damengUppercaseIdentifiers = damengUppercaseIdentifiers;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public Map<String, String> getTableSchemas() {
        return tableSchemas;
    }

    public void setTableSchemas(Map<String, String> tableSchemas) {
        this.tableSchemas = tableSchemas;
    }

    public Map<String, String> getTableNames() {
        return tableNames;
    }

    public void setTableNames(Map<String, String> tableNames) {
        this.tableNames = tableNames;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access == null ? new Access() : access;
    }

    /** 启动时从数据库加载的授权表。 */
    public static class Access {
        private boolean enabled = true;
        private String table = "access";
        /** 空则沿用 queryengine.schema。 */
        private String schema;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }
}
