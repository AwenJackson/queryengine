# 扩展与覆盖

QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。扩展点是本仓库自己的类型，不复制其他引擎的类。

## 可继承的基类

| 基类 | 作用 | 怎么接上 |
| --- | --- | --- |
| `FieldParser` | NestQL 字段解析。覆盖 `afterParse` 或 `parse` | `builder.fieldParser(...)`，或 Spring `@Bean FieldParser` |
| `SqlPlanner` | 参数化 SQL。覆盖 `select` / `insert` / `update` / `delete`。`dialect` 是 `protected` | `builder.sqlPlanner(...)`，或 `@Bean SqlPlanner` |
| `DefaultAccessRegistry` | access 白名单。覆盖 `require` | `builder.accessRegistry(...)`，或 `@Bean AccessRegistry` |
| `Dialect` | 标识符引用、表名折叠、分页语法 | `builder.dialect(...)`，或 `@Bean Dialect`（优先于 `queryengine.dialect`） |

示例在 `queryengine-demo` 的 `dev.queryengine.demo.ext`：

- `HidePasswordParser` 拒绝读出 `password`
- `CommentedSqlPlanner` 在 SELECT 前加注释，参数仍是占位符
- `NoDeleteRegistry` 拒绝 DELETE

```java
QueryEngine engine = QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .fieldParser(new HidePasswordParser())
        .sqlPlanner(new CommentedSqlPlanner(new MySqlDialect()))
        .accessRegistry(new NoDeleteRegistry())
        .tableNameMapper(logical -> logical.toLowerCase(Locale.ROOT))
        .registerTable("User")
        .build();
```

`TableNameMapper` 只改物理表名。MySQL 方言还会把表名折成小写，达梦在 `quote` 里折成大写。

## Spring Bean

`queryengine-spring-boot-starter` 在缺少用户 Bean 时才创建 `QueryEngine`。下面这些类型只要声明成 `@Bean`，就会被用上：

- `Dialect`
- `AccessRegistry`
- `FieldParser`
- `SqlPlanner`
- `TableNameMapper`

HTTP 模块另外认：

- `ResponseCustomizer`：信封写完之后再改 `ObjectNode`。不要删掉 `code` / `message`。
- `GraphQLSchemaCustomizer`：access 名单生成 schema 之后、`build()` 之前。列参数仍是 `Long` 和 `String`。

```java
@Bean
ResponseCustomizer stamp() {
    return body -> {
        body.put("engine", "queryengine");
        return body;
    };
}
```
