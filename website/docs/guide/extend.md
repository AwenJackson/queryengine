# 扩展与覆盖

扩展点是 QueryEngine 自己的类，不是其他项目的解析器拷贝。

## 继承

`FieldParser` 解析 NestQL。覆盖 `afterParse`：

```java
public class HidePasswordParser extends FieldParser {
    @Override
    protected TableRequest afterParse(TableRequest request, JsonNode body, Method method) {
        for (SelectItem column : request.columns()) {
            if ("password".equalsIgnoreCase(column.name())) {
                throw new BadRequestException("列不可查询: password");
            }
        }
        return request;
    }
}
```

`SqlPlanner` 渲染参数化 SQL。覆盖 `select` 时请保留参数列表，不要把值拼进语句。`dialect` 字段是 `protected`。

`DefaultAccessRegistry` 管白名单。覆盖 `require` 可以拒绝某个 method。

完整例子在 `queryengine-demo` 的 `dev.queryengine.demo.ext`。

```java
QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .fieldParser(new HidePasswordParser())
        .sqlPlanner(new CommentedSqlPlanner(new MySqlDialect()))
        .accessRegistry(new NoDeleteRegistry())
        .tableNameMapper(name -> name.toLowerCase(Locale.ROOT))
        .registerTable("User")
        .build();
```

`TableNameMapper` 只决定物理表名。MySQL 仍会把表名折成小写，达梦在引用时折成大写。

## Spring

声明即生效，不用改自动配置：

```java
@Bean
FieldParser fieldParser() {
    return new HidePasswordParser();
}

@Bean
ResponseCustomizer stamp() {
    return body -> {
        body.put("engine", "queryengine");
        return body;
    };
}

@Bean
GraphQLSchemaCustomizer schemaCustomizer() {
    return (schema, callSchema) -> {
        // access 名单生成之后、build 之前
    };
}
```

`ResponseCustomizer` 看到的是已经写好 `code` / `message` 的信封。`GraphQLSchemaCustomizer` 不要把列参数改成 `ID` 或 `Int`：运行时只注册了 `Long` 和 `String`。
