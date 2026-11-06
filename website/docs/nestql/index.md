# NestQL

NestQL（Nested Query Language，嵌套查询语言）是 QueryEngine 的 JSON 协议。请求和响应都是 JSON 对象：表名作键，条件、列和引用写在对应的对象里。一次调用只有一个 method，由库方法或 HTTP 路径决定。

库方法是 `get` / `head` / `post` / `put` / `delete`，也可以 `execute(Method, request)`。HTTP 全部是 `POST`，`Content-Type: application/json`，请求体就是交给库的那份 JSON。

| HTTP 路径 | 库方法 |
| --- | --- |
| `/get`、`/gets`、`/nestql/get`、`/nestql/gets` | GET |
| `/head`、`/heads`、`/nestql/head`、`/nestql/heads` | HEAD |
| `/post`、`/nestql/post` | POST |
| `/put`、`/nestql/put` | PUT |
| `/delete`、`/nestql/delete` | DELETE |

`/gets` 与 `/heads` 是别名，权限和 SQL 与 `/get`、`/head` 相同。数组写在请求体的 `[]` 上，不靠路径区分。`queryengine.http.base-path` 加在这些路径前面。设成 `/api` 时，入口是 `POST /api/get`。前缀末尾不要再加斜杠。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "User": { "id": 38710 } }'
```

```json
{
  "User": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" },
  "code": 200,
  "message": "success"
}
```

库方法成功时直接返回业务对象。HTTP 在同一层加上 `code` 与 `message`，成功时是 `200` 和 `success`。失败时库方法抛异常；HTTP 只返回 `code` 与 `message`，不带半成品业务对象。`code` 和 `message` 是信封保留字段，不要用来做表名。

可选请求头 `X-QueryEngine-Schema` 只覆盖这一次调用的 schema。不读 Cookie，也不创建会话。

## 名字

表名大小写不敏感。`User`、`user`、`USER` 指向同一张已登记的表，只登记一次。MySQL 把物理表名折成小写，达梦折成大写。

列名是物理列名，保持请求里的拼写。引擎不会把驼峰改成下划线，也不会把下划线改成驼峰。`userId` 与 `user_id` 是两列。

表名、列名、别名、排序列和引用路径里的标识符只允许 ASCII 字母、数字和下划线，并且以字母或下划线开头。

未登记的表直接拒绝。`registerTable` 授予这张表的全部 method。没有登录、角色或会话。

## 请求里有什么

根对象的每个键是一张表、一个数组，或一批写入：

| 键 | 值 | 作用 |
| --- | --- | --- |
| `"User"` | `{ ... }` | 单表对象。查询最多一行；写入一行 |
| `"[]"` 或 `"User[]"` | `{ "page", "count", "User": { ... } }` | 查询数组。元素是 `{ "User": { ...列 } }`。POST 上同形对象是插入树，见 [写入](./write.md#nest) |
| `"Todo[]"` | `[ { ... }, { ... } ]` | 批量 POST / PUT / DELETE。元素里可以再写子表数组 |

条件、`@column`、`@order` 和 `列名@` 写在表对象内部，不写在根上。同一张表上的条件是 AND。关联按引用再查，每次都是单表 SQL。

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

## 本篇

| 页面 | 内容 |
| --- | --- |
| [运算符](./operators.md) | 比较、`IN`、区间、`LIKE`、`null`、`@column`、`@order`、`key@` |
| [查询](./query.md) | GET / HEAD、对象与数组、分页、响应形状 |
| [写入](./write.md) | POST / PUT / DELETE、主键、批量、嵌套插入、`count` |
| [嵌套示例](./example.md) | 一次取出用户和关联行 |
| [限制](./limits.md) | `page` / `count` 软限制，以及没有硬性深度上限 |
| [协议差异](./differences.md) | 相对形似 JSON 嵌套协议时，本引擎的约定 |

另外两条入口是 [RESTful](../http/rest.md) 和 [GraphQL](../http/graphql.md)。错误码见 [错误码](../http/errors.md)。
