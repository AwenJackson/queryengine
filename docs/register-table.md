# registerTable

未注册的表一律拒绝，引擎里没有默认公开库。

```java
engine.registerTable("User");
engine.registerTable("Profile", "app_a");
engine.registerTables("Moment", "Comment");
engine.unregisterTable("Comment");
```

也可以在构建时登记，效果相同：

```java
QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .registerTable("Profile", "app_a")
        .registerTables("User", "Moment")
        .build();
```

## 全权限

`register` / `registerTable` 授予全部 `Method`：`GET`、`HEAD`、`POST`、`PUT`、`DELETE`。不需要再按角色或方法补一次授权。

角色、登录态、数据范围由宿主在调用引擎之前处理。核心不创建 session。

登记后的表可以立刻读写：

```java
engine.registerTable("Todo");
engine.post("""
        { "Todo": { "title": "买牛奶" } }
        """);
```

自定义 `AccessRegistry` 时，`register(table, schema)` 仍然应当表示全部 method。更细的拒绝可以写在 `require` 里，引擎会把它变成 `AccessDeniedException`。

## 请求键和物理表名

请求里的键可以和数据库里的表名不同。`mapTable` 同样授予全部 method。

```java
engine.mapTable("User", "user");
engine.mapTable("Book_menu", "book_menu");
```

配置文件：

```yaml
queryengine:
  tables:
    - User
    - Book_menu
  table-names:
    User: user
    Book_menu: book_menu
```

没写 `table-names` 时，请求键就是物理表名。`table-schemas` 只钉 schema，不会把物理表名改回去。

登记名和请求里的表名**不区分大小写**，而且只占一条授权。注册 `User` 之后，请求写 `User`、`user` 或 `USER` 都是同一张表，不必再登记另外两种写法。MySQL 方言把表名折成小写再生成 SQL，因此会命中物理表 `user`。达梦方言把表名折成大写，命中 `"USER"`。列名仍按请求里的大小写引用，例如 `userId` 不会被折成 `userid`。

## 表名与 schema

表名和 schema 名只允许 ASCII 字母、数字、下划线，且不能以数字开头。这是为了让标识符可以安全地引用，而不是拼进 SQL 字面量。

`registerTable(String)` 不钉 schema，查询时用当时的运行时 schema。`registerTable(String, String)` 把这张表钉在指定 schema 上，之后的 `useSchema` 不会改它。

同名再次注册会覆盖上一次的授权和 schema。

## 从 access 表加载

每个 schema 有自己的 `access` 表。进程启动时**不**预加载，包括默认 schema。第一次对某个 schema 做查询（`get` / `head` / `post` / `put` / `delete`），或第一次对它做 `register` / `list`，才读取该 schema 下 `enabled = 1` 的行并缓存。之后同一 schema 走缓存。`reloadAccess()` 刷新当前 schema，`reloadAccess("demo1")` 只刷新那一份。

切换 schema（`useSchema`、`withSchema`、`execute` 的 schema 参数、请求头 `X-QueryEngine-Schema`）时，查询和 `/access/list` 用的是**同一份**当前 schema 缓存。`demo` 有 `Book_menu_tip`、`demo1` 没有时，把头切到 `demo1` 再查 tip 会得到 403，即使 `demo` 的名单里还有它。

行里的 `schema_name` 只表示这条授权查库时钉死的 schema，不表示「这条记录属于哪个 schema 的名单」。名单归属看这行存在于哪个 schema 的 `access` 表。`schema_name` 为空也不会变成全局授权。

```sql
CREATE TABLE `access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `alias` varchar(64) DEFAULT NULL,
  `schema_name` varchar(64) DEFAULT NULL,
  `enabled` int NOT NULL,
  PRIMARY KEY (`id`)
);
```

| 列 | 含义 |
| --- | --- |
| `name` | 请求里的逻辑表名。大小写不敏感，`User` 与 `user` 是同一行 |
| `alias` | 物理表名。空则与 `name` 相同。MySQL 仍会把表名折成小写 |
| `schema_name` | 钉死的 schema。空则跟随运行时 schema |
| `enabled` | `1` 启用，`0` 停用。启动时不加载停用行 |

达梦里这张表是 `"ACCESS"`，列名为大写双引号。

关掉数据库加载：`queryengine.access.enabled=false`。表名或所在 schema 可用 `queryengine.access.table`、`queryengine.access.schema` 修改，默认表名 `access`，schema 沿用 `queryengine.schema`。

## 运行时登记

`registerTable` / `mapTable` 写入**当前运行时 schema**（或方法参数 / HTTP 里显式给出的 schema）的 `access` 表，并更新该 schema 的内存名单。当前进程马上可以查询。`unregisterTable` 只停用那一个 schema 的行。

```java
engine.registerTable("Note");
engine.mapTable("Book_menu_tip", "book_menu_tip");
engine.unregisterTable("Note");
```

`unregisterTable` 把对应行的 `enabled` 改成 0。`reloadAccess()` 清空内存登记并重新读取数据库，用来核对持久化结果。

HTTP 同样没有登录态。`queryengine.http.base-path` 前缀对这些路径生效。成功时 `code` 为 200，登记结果在 `data` 里，而不是和查询结果摊在同一层。

```bash
curl -s -X POST http://127.0.0.1:8080/access/register \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Note", "alias": "note", "schema": "demo" }'
```

```json
{ "code": 200, "message": "success", "data": { "name": "Note", "alias": "note", "schema": "demo" } }
```

`alias` 省略时物理表名与 `name` 相同；`schema` 省略时跟随运行时 schema，响应里为 `null`。

```bash
curl -s -X POST http://127.0.0.1:8080/access/list
curl -s -X POST http://127.0.0.1:8080/access/unregister \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Note" }'
```

`/access/list` 的 `data` 是数组，元素同样是 `name`、`alias`、`schema`。`name` 大小写不敏感，用 `note` 注销也能去掉 `Note`。缺 `name` 时返回 `code` 400。

注册表是并发安全的。自定义 `AccessRegistry` 时，`register` 仍然应当表示全部 method。
