# 嵌入

核心没有 HTTP，失败抛 `QueryEngineException`。

```java
QueryEngine engine = QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .schema("demo")
        .defaultPageSize(10)
        .maxPageSize(100)
        .registerTables("User", "Moment", "Comment")
        .build();

JsonNode user = engine.get("""
        { "User": { "id": 38710 } }
        """);
```

Spring Boot 只想要 Bean 时依赖 starter，并设置：

```yaml
queryengine:
  dialect: mysql
  schema: demo
  access:
    enabled: true
    table: access
```

换成自己的 `Dialect`、`FieldParser`、`SqlPlanner`、`AccessRegistry` 或 `TableNameMapper` Bean 后，自动配置会用你的实现。见 [扩展与覆盖](./extend.md)。
