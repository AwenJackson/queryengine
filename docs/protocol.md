# NestQL

QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。本文是 **NestQL**（Nested Query Language，嵌套查询语言）：库方法 `get` / `head` / `post` / `put` / `delete`，以及 HTTP `POST /get`、`/head`、`/post`、`/put`、`/delete`。`/nestql/get` 等路径是别名，请求体相同。

请求和响应都是 JSON 对象，键的位置与请求对应。这种形状形似 APIJSON，产品名是 NestQL，不依赖、不包含 APIJSON。库方法成功时**不**包一层 `code` / `message`。失败抛异常，不返回错误码对象。HTTP 成功时在同一层加上 `code` 与 `message`。

另外两种入口见 [rest.md](rest.md) 和 [graphql.md](graphql.md)。

一次调用只有一个 method，由 `engine.get` / `head` / `post` / `put` / `delete` 或 `execute(Method, ...)` 决定。

## 子集

### 读

```json
{ "User": { "id": 38710 } }
```

```json
{
  "[]": {
    "page": 0,
    "count": 10,
    "User": {
      "@column": "id,name:userName",
      "@order": "id-,name+"
    }
  }
}
```

`User[]` 与匿名 `[]` 相同，只是响应键用请求里的名字。数组元素是 `{ "User": { ...列 } }`。

`page` 从 0 开始，偏移是 `page * count`。没写 `count` 时用 `defaultPageSize`（默认 10）。

`HEAD` 返回总行数，忽略 `page`、`count`、`@column`、`@order`：

```json
{ "User": { "count": 3 } }
```

数组的 `HEAD` 只统计主表：`{ "[]": { "count": 4 } }`。

### 条件

条件之间是 AND。

| 写法 | SQL |
| --- | --- |
| `"id": 1` | `id = ?` |
| `"tag": null` | `tag IS NULL` |
| `"sex!": 0` | `sex <> ?` |
| `"sex!": null` | `sex IS NOT NULL` |
| `"id>": 1` `"id>="` `"id<"` `"id<="` | 对应比较 |
| `"id{}": [1, 2]` 或 `"id{}": "1,2"` | `id IN (?, ?)` |
| `"id!{}": [1, 2]` | `id NOT IN (?, ?)` |
| `"id{}": ">=10,<=20"` | `id >= ? AND id <= ?` |
| `"name$": "%Tom%"` | `name LIKE ?` |
| `"name$": ["%a%", "%b%"]` | `name LIKE ? OR name LIKE ?` |

`@column` 只接受列名和 `列:别名`。`@order` 只接受 `列`、`列+`（升序）、`列-`（降序），多项用逗号分隔。

空的 `IN` 匹配 0 行。空的 `NOT IN` 匹配全部行。

### 引用赋值 `key@`

```json
{
  "User": { "id": 38710 },
  "Moment[]": {
    "Moment": { "userId@": "/User/id" }
  }
}
```

| 路径 | 含义 |
| --- | --- |
| `/User/id` | 从本次响应根开始 |
| `User/id` | 从当前数组元素向外找同级键 |
| `[]/User/id` | 最近一层数组元素 |
| `[]/[]/User/id` | 再向外回退一层 |

同级键即使写在后面，也会按引用先执行。响应里的键顺序仍与请求一致。

数组里第一张不依赖兄弟键的表是主表，按 `page` / `count` 查询。其余对象表和嵌套数组按每一行再查，不是 JOIN。

引用不存在时，读操作得到 `null`（对象）或 `[]`（数组）。写操作失败并回滚。

### 写

主键列默认 `id`，可用 `idColumn(...)` 修改。

```json
{ "Todo": { "title": "买牛奶", "done": 0 } }
```

POST 把字段写成 INSERT。没写 `id` 时，尝试用 JDBC 生成键回填 `id`。

```json
{ "Todo": { "id": 1, "title": "改" } }
```

PUT 把 `id`（以及带运算符的字段）当作 WHERE，其他字段当作 SET。必须带主键条件，且不会修改主键本身。

```json
{ "Todo": { "id": 1 } }
```

DELETE 的字段都是 WHERE，同样必须有主键。`"id{}": [1, 2]` 可以一次更新或删除多行。

批量写入：

```json
{ "Todo[]": [ { "title": "一" }, { "title": "二" } ] }
```

写操作的响应带 `count`（影响行数）。GET 的对象或数组不额外加 `count`。

对象查询（不是 `[]`）若命中 2 行及以上，抛 `BadRequestException`。要列表就用数组。

## 软限制

只有 `page` 和 `count` 有软限制，且可以配置：

- `count` 大于 `maxPageSize`（默认 100）时夹到上限，不报错
- `page` 大于 `maxPageIndex`（默认 0，表示不限制）时夹到上限，不报错
- `maxPageSize(0)` 关闭 count 夹紧
- `count <= 0` 或 `page < 0` 视为非法请求

没有查询深度、对象个数、数组个数、SQL 条数、更新条数的硬拒绝。12 层以上的 `Child[]` 可以正常返回。实际边界是数据库超时、连接池和 JVM 栈，不是协议常量。`queryTimeoutSeconds` 会传给 `Statement.setQueryTimeout`，0 表示不额外设置。

根上的字符串 `tag`、`version`、`format`，以及数组上的数字 `query`，会被忽略。它们如果是对象，则仍当作表名。

## 自有扩展（相对 APIJSON 形似协议）

1. 库方法的成功响应不带 `code` / `msg`，失败抛异常。HTTP 模块另加顶层 `code` 与 `message`（成功为 200 / `success`），字段名是 `message` 而不是 `msg`。不提供登录，因此没有 401。
2. 无登录、角色、会话。`registerTable` 即全部 method。
3. 未注册表直接拒绝。
4. 不设深度、对象数、数组数、SQL 条数、更新条数硬上限。`page` / `count` 只夹紧，不因为“太深”失败。
5. 不做 JOIN。同级关联是按引用再查。
6. 对象查询命中多行会失败，而不是只取第一行。
7. `@column` 不允许函数、子查询、表达式。
8. `name$` 不会自动补 `%`。
9. `@order` 不接受 `DESC` 原文，只接受 `+` / `-`。
10. 不支持 `@raw`、`@group`、`@having`、正则 `~`、远程函数、存储过程、脚本条件。
11. 数组上的 `query` 数字被忽略。GET 返回当前页，HEAD 返回总行数，没有 query=0/1/2 组合语义。
12. PUT / DELETE 必须带主键条件。
13. 一次请求一个本地 JDBC 事务。不支持多数据源事务。
14. 引用目标缺失时，读路径得到空结果，写路径回滚。
15. 标识符只允许 ASCII 字母、数字、下划线。表名大小写不敏感，只登记一次；MySQL 物理表名折成小写，达梦折成大写。列名大小写保持请求原文。
16. 整型列统一成 64 位整数；日期时间转成字符串。
17. 达梦默认把物理标识符转成大写；别名保持请求中的大小写。
18. 执行顺序按引用拓扑排序，响应键顺序与请求一致。
19. 批量写入使用 `表名[]: [ {...} ]`。查询形态的 `表名[]: { ... }` 不能用于 POST/PUT/DELETE。

不在本版实现、也不要依赖其存在的能力：完整 JOIN 矩阵、`@raw`、存储过程、远程函数、多数据源事务、驼峰 format 转换。
