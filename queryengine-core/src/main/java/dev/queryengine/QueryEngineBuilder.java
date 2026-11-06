package dev.queryengine;

import dev.queryengine.authz.AccessStore;
import dev.queryengine.authz.DefaultAccessRegistry;
import dev.queryengine.exec.JdbcSqlExecutor;
import dev.queryengine.exec.RequestExecutor;
import dev.queryengine.exec.SchemaSelector;
import dev.queryengine.plan.FieldParser;
import dev.queryengine.plan.Identifiers;
import dev.queryengine.plan.PagePolicy;
import dev.queryengine.plan.SqlPlanner;
import dev.queryengine.spi.AccessRegistry;
import dev.queryengine.spi.Dialect;
import dev.queryengine.spi.SqlExecutor;
import dev.queryengine.spi.TableNameMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

public final class QueryEngineBuilder {

    private DataSource dataSource;
    private Dialect dialect;
    private AccessRegistry registry;
    private AccessStore accessStore;
    private SqlExecutor sqlExecutor;
    private FieldParser fieldParser;
    private SqlPlanner sqlPlanner;
    private TableNameMapper tableNameMapper;
    private String schema;
    private int defaultPageSize = 10;
    private int maxPageSize = 100;
    private int maxPageIndex = 0;
    private int queryTimeoutSeconds = 0;
    private String idColumn = "id";
    private final List<Pending> pending = new ArrayList<>();

    QueryEngineBuilder() {
    }

    public QueryEngineBuilder dataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public QueryEngineBuilder dialect(Dialect dialect) {
        this.dialect = dialect;
        return this;
    }

    /** 替换默认注册表。自定义实现的 {@code register} 仍应表示全部 method。 */
    public QueryEngineBuilder accessRegistry(AccessRegistry registry) {
        this.registry = registry;
        return this;
    }

    /** 启动时从该存储加载已启用的表，并且之后的 {@code registerTable} 会写回存储。 */
    public QueryEngineBuilder accessStore(AccessStore accessStore) {
        this.accessStore = accessStore;
        return this;
    }

    /** 替换 JDBC 执行器。测试或非 JDBC 宿主可以使用自己的实现。 */
    public QueryEngineBuilder sqlExecutor(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
        return this;
    }

    /** 替换 NestQL 字段解析。传入 {@link FieldParser} 的子类即可改条件或列。 */
    public QueryEngineBuilder fieldParser(FieldParser fieldParser) {
        this.fieldParser = fieldParser;
        return this;
    }

    /** 替换 SQL 渲染。传入 {@link SqlPlanner} 的子类即可改语句，值仍须走占位符。 */
    public QueryEngineBuilder sqlPlanner(SqlPlanner sqlPlanner) {
        this.sqlPlanner = sqlPlanner;
        return this;
    }

    /** 逻辑表名到物理表名。方言仍会做大小写折叠。 */
    public QueryEngineBuilder tableNameMapper(TableNameMapper tableNameMapper) {
        this.tableNameMapper = tableNameMapper;
        return this;
    }

    public QueryEngineBuilder schema(String schema) {
        this.schema = schema;
        return this;
    }

    public QueryEngineBuilder defaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
        return this;
    }

    /** 0 表示不夹紧 count。大于 0 时，请求里更大的 count 会被降到该值，而不是报错。 */
    public QueryEngineBuilder maxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
        return this;
    }

    /** 0 表示不夹紧 page。大于 0 时，更大的页码会被降到该值，而不是报错。 */
    public QueryEngineBuilder maxPageIndex(int maxPageIndex) {
        this.maxPageIndex = maxPageIndex;
        return this;
    }

    /** 0 表示不额外设置 {@code Statement.setQueryTimeout}。 */
    public QueryEngineBuilder queryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        return this;
    }

    public QueryEngineBuilder idColumn(String idColumn) {
        this.idColumn = idColumn;
        return this;
    }

    public QueryEngineBuilder registerTable(String table) {
        replace(table, null);
        return this;
    }

    public QueryEngineBuilder registerTable(String table, String schema) {
        replace(table, schema);
        return this;
    }

    public QueryEngineBuilder registerTables(String... tables) {
        if (tables == null) {
            throw new IllegalArgumentException("tables 不能为空");
        }
        for (String table : tables) {
            replace(table, null);
        }
        return this;
    }

    /** 请求键映射到另一张物理表，并授予全部 method。 */
    public QueryEngineBuilder mapTable(String requestName, String physicalName) {
        return mapTable(requestName, physicalName, null);
    }

    /** 请求键映射到物理表，并钉死 schema。 */
    public QueryEngineBuilder mapTable(String requestName, String physicalName, String schema) {
        pending.removeIf(item -> item.table().equals(requestName));
        pending.add(new Pending(requestName, physicalName, schema));
        return this;
    }

    public QueryEngine build() {
        if (dataSource == null && sqlExecutor == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("dialect 不能为空");
        }
        if (queryTimeoutSeconds < 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds 不能为负");
        }
        Identifiers.checkProgramming(idColumn, "主键列");
        AccessRegistry access = registry == null ? new DefaultAccessRegistry() : registry;
        for (Pending item : pending) {
            String physical = item.physicalName();
            if (physical == null && tableNameMapper != null) {
                physical = tableNameMapper.physicalName(item.table());
            }
            if (physical == null) {
                physical = item.table();
            }
            String catalog = item.schema() != null ? item.schema() : schema;
            access.registerIn(catalog, item.table(), physical, item.schema());
        }
        SqlExecutor executor = sqlExecutor != null
                ? sqlExecutor
                : new JdbcSqlExecutor(dataSource, queryTimeoutSeconds);
        SqlPlanner planner = sqlPlanner != null ? sqlPlanner : new SqlPlanner(dialect);
        RequestExecutor requests = new RequestExecutor(
                access,
                planner,
                executor,
                new PagePolicy(defaultPageSize, maxPageSize, maxPageIndex),
                idColumn,
                fieldParser
        );
        return new QueryEngine(access, accessStore, new SchemaSelector(schema), requests, dialect.name());
    }

    private void replace(String table, String schema) {
        pending.removeIf(item -> item.table().equals(table));
        pending.add(new Pending(table, null, schema));
    }

    private record Pending(String table, String physicalName, String schema) {
    }
}
