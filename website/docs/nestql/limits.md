# 限制

只有 `page` 和 `count` 有软限制，而且可以配置。嵌套深度、对象个数、数组个数、SQL 条数和更新条数没有协议级的硬拒绝。

## page 与 count {#page}

| 配置 | 默认 | 行为 |
| --- | --- | --- |
| `defaultPageSize` | 10 | 数组没写 `count` 时使用。必须是正数 |
| `maxPageSize` | 100 | `count` 更大时夹到这个值，不报错。`0` 表示不再夹紧 |
| `maxPageIndex` | 0 | `0` 表示不限制页码。大于 0 时，更大的 `page` 夹到这个值，不报错 |

`count <= 0` 或 `page < 0` 是非法请求。嵌入时写在构建器上：

```java
QueryEngine engine = QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .defaultPageSize(10)
        .maxPageSize(100)
        .maxPageIndex(0)
        .queryTimeoutSeconds(0)
        .build();
```

`queryTimeoutSeconds` 传给 `Statement.setQueryTimeout`。`0` 表示不额外设置。它限制的是单条语句的数据库超时，不是协议层的深度上限。

对象查询不读 `page` / `count`，见 [查询](./query.md#object)。

## 没有硬上限的部分 {#depth}

12 层以上的 `Child[]` 可以正常返回。实际边界是数据库超时、连接池和 JVM 栈。

根上的标量 `tag`、`version`、`format`，以及数组上的标量 `query`，会被忽略，不因此多一条 SQL。它们如果是对象，则仍是表名。见 [查询](./query.md#meta)。

一次请求使用一个本地 JDBC 事务。
