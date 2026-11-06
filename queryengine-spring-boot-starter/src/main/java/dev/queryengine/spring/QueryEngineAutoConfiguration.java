package dev.queryengine.spring;

import dev.queryengine.QueryEngine;
import dev.queryengine.QueryEngineBuilder;
import dev.queryengine.authz.JdbcAccessStore;
import dev.queryengine.dialect.dameng.DamengDialect;
import dev.queryengine.dialect.mysql.MySqlDialect;
import dev.queryengine.plan.FieldParser;
import dev.queryengine.plan.SqlPlanner;
import dev.queryengine.spi.AccessRegistry;
import dev.queryengine.spi.Dialect;
import dev.queryengine.spi.TableNameMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 只注册 {@link QueryEngine} Bean。HTTP 入口在 {@code queryengine-web}，不在本模块。
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(QueryEngine.class)
@EnableConfigurationProperties(QueryEngineProperties.class)
public class QueryEngineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QueryEngineAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public QueryEngine queryEngine(
            DataSource dataSource,
            QueryEngineProperties properties,
            ObjectProvider<SqlDataSourceScriptDatabaseInitializer> scriptInitializer,
            ObjectProvider<Dialect> dialects,
            ObjectProvider<AccessRegistry> registries,
            ObjectProvider<FieldParser> parsers,
            ObjectProvider<SqlPlanner> planners,
            ObjectProvider<TableNameMapper> tableNames
    ) {
        scriptInitializer.ifAvailable(SqlDataSourceScriptDatabaseInitializer::initializeDatabase);
        Dialect dialect = dialects.getIfAvailable(() -> dialect(properties));
        if (properties.getAccess().isEnabled()) {
            String accessSchema = properties.getAccess().getSchema();
            if (accessSchema == null || accessSchema.isBlank()) {
                accessSchema = properties.getSchema();
            }
            try {
                return configured(dataSource, properties, dialect, registries, parsers, planners, tableNames)
                        .accessStore(new JdbcAccessStore(
                                dataSource,
                                dialect,
                                properties.getAccess().getTable(),
                                accessSchema
                        ))
                        .build();
            } catch (RuntimeException ex) {
                log.warn("未能从 access 表加载授权，改用配置兜底: {}", ex.getMessage());
            }
        }
        return configured(dataSource, properties, dialect, registries, parsers, planners, tableNames).build();
    }

    private static QueryEngineBuilder configured(
            DataSource dataSource,
            QueryEngineProperties properties,
            Dialect dialect,
            ObjectProvider<AccessRegistry> registries,
            ObjectProvider<FieldParser> parsers,
            ObjectProvider<SqlPlanner> planners,
            ObjectProvider<TableNameMapper> tableNames
    ) {
        QueryEngineBuilder builder = QueryEngine.builder()
                .dataSource(dataSource)
                .dialect(dialect)
                .defaultPageSize(properties.getDefaultPageSize())
                .maxPageSize(properties.getMaxPageSize())
                .maxPageIndex(properties.getMaxPageIndex())
                .queryTimeoutSeconds(properties.getQueryTimeoutSeconds())
                .idColumn(properties.getIdColumn());
        if (registries.getIfAvailable() != null) {
            builder.accessRegistry(registries.getIfAvailable());
        }
        if (parsers.getIfAvailable() != null) {
            builder.fieldParser(parsers.getIfAvailable());
        }
        if (planners.getIfAvailable() != null) {
            builder.sqlPlanner(planners.getIfAvailable());
        }
        if (tableNames.getIfAvailable() != null) {
            builder.tableNameMapper(tableNames.getIfAvailable());
        }
        if (properties.getSchema() != null && !properties.getSchema().isBlank()) {
            builder.schema(properties.getSchema());
        }
        java.util.Map<String, String> physicalNames = new java.util.LinkedHashMap<>();
        if (properties.getTables() != null) {
            for (String table : properties.getTables()) {
                physicalNames.put(table, table);
            }
        }
        if (properties.getTableNames() != null) {
            physicalNames.putAll(properties.getTableNames());
        }
        java.util.Map<String, String> schemas = properties.getTableSchemas() == null
                ? java.util.Map.of()
                : properties.getTableSchemas();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.addAll(physicalNames.keySet());
        names.addAll(schemas.keySet());
        for (String name : names) {
            builder.mapTable(name, physicalNames.getOrDefault(name, name), schemas.get(name));
        }
        return builder;
    }

    private static Dialect dialect(QueryEngineProperties properties) {
        return switch (properties.getDialect()) {
            case MYSQL -> new MySqlDialect();
            case DAMENG -> new DamengDialect(properties.isDamengUppercaseIdentifiers());
        };
    }
}
