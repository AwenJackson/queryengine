# 运行时切换 schema

schema 用来限定表。MySQL 里它是 database，达梦里它是模式名（通常与用户名对应）。值为 `null` 时 SQL 不写模式前缀，走连接的当前库。

解析顺序：

1. 注册时钉死的 schema（`registerTable(table, schema)`）
2. 本次调用传入的 schema（`execute(method, request, schema)`）
3. 当前线程的 schema（`useSchema` / `withSchema`）
4. 构建时的默认 schema（`builder.schema(...)`）

## 线程切换

`useSchema` 只影响当前线程，适合一个请求线程服务一个租户。

```java
engine.useSchema("app_a");
JsonNode alpha = engine.get("""
        { "User": { "id": 1 } }
        """);

try (SchemaScope scope = engine.withSchema("app_b")) {
    JsonNode beta = engine.get("""
            { "User": { "id": 1 } }
            """);
}
// withSchema 结束后回到 app_a
engine.clearSchema();
```

`withSchema` 会在 `close()` 时恢复进入前的线程值。优先用它，避免线程池把上一个租户的 schema 留在 `ThreadLocal` 里。

## 单次调用覆盖

不想碰线程状态时，把 schema 作为本次参数：

```java
JsonNode beta = engine.execute(
        Method.GET,
        "{ \"User\": { \"id\": 1 } }",
        "app_b"
);
```

这次覆盖不影响 `currentSchema()`。已经钉死 schema 的表，只有出现在**当前这份名单**里时，SQL 才改走钉死的 schema。

## 和注册表的关系

每个 schema 有自己的 `access` 白名单，存在该 schema 的 `access` 表里。第一次查询或登记这个 schema 时才加载并缓存，启动时不预加载。

`registerTable("User")` 写进**当前** schema 的名单。切到另一个 schema 后，除非那边的 `access` 也有 `User`，否则查询会被拒绝。

`registerTable("Profile", "app_a")` 写入 `app_a.access`，并把 `schema_name` 记成 `app_a`。只有当前 schema 是 `app_a` 时才能查到它，SQL 打到 `app_a.Profile`。切到别的 schema 不会沿用这份授权。`schema_name` 为空同样不会变成所有 schema 共用。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -H 'X-QueryEngine-Schema: demo1' \
  -d '{ "Book_menu_tip": { "id": 1 } }'
```

`demo` 的名单里有 tip，`demo1` 没有，所以上面这条返回 `code` 403。

`reloadAccess("demo1")` 只丢掉 `demo1` 的缓存再读库。切换 schema 不会自动建表，也不会跨库做分布式事务。一次请求里的多条 SQL 共用一个本地事务。
