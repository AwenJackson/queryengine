# GraphQL

QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。本文是 GraphQL MVP。schema 在第一次查询某个 schema 时，按该 schema 的 access 名单懒生成，列来自物理表的结果集元数据。没有登录，也不依赖 APIJSON。

```bash
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{ "query": "{ User(id: 38710) { id name tag } }" }'
```

```json
{
  "data": { "User": { "id": 38710, "name": "Tommy", "tag": "student" } },
  "extensions": { "code": 200, "message": "success" }
}
```

请求体是 `{ "query": "...", "variables": {}, "operationName": null }`。字段名使用 access 里登记的拼写，例如 `User` 而不是 `user`。`X-QueryEngine-Schema` 只覆盖这一次调用，并据此选择那一份名单。`queryengine.http.base-path` 会加在 `/graphql` 前面。

## 查询

每张已注册表有两个根字段：

- `User(...)` 返回一行。条件是列名等值参数，例如 `id`、`name`。命中两行及以上时是 400，与 NestQL 的对象查询相同。没有行时该字段为 `null`。
- `UserList(page, count, ...等值条件)` 返回数组。`page` 从 0 开始。`page` 和 `count` 是分页参数，不能同时当作同名列的过滤。

选中的字段会变成 `@column`。

**标量与变量。** 列参数目前只有两种类型：整数列是自定义标量 `Long`，其余列是 `String`。QueryEngine 的 GraphQL schema **没有**注册规范里的内置标量 `ID`、`Int`、`Float`、`Boolean` 来描述这些列。变量声明必须和字段参数的类型一致。主键和整型过滤写 `Long` 或 `Long!`，不要写 `ID!` 或 `Int!`：

- `query ($uid: ID!) { User(id: $uid) { id } }` → `Unknown type 'ID'`
- `query ($uid: Int!) { User(id: $uid) { id } }` → `Variable type mismatch`，参数实际类型是 `Long`
- `query ($uid: Long!) { User(id: $uid) { id } }` 可以通过校验

查询里直接写数字字面量（`User(id: 38710)`）不需要声明变量类型。分页参数 `page`、`count` 是内置 `Int`，不是 `Long`。关联参数 `on` 是 `String!`。

```bash
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query ($uid: Long!) { User(id: $uid) { id name Moment(on: \"userId\") { id content } } }","variables":{"uid":38710}}'
```

```bash
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ UserList(name: \"Tommy\", count: 5) { id name } }"}'
```

## 嵌套

子字段名如果是另一张已注册表，并且带必填参数 `on`（子表里指向父行 `id` 的列），就再查一次列表，不是 JOIN。

```bash
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ User(id: 38710) { id name Moment(on: \"userId\") { id content } } }"}'
```

`Moment` 也可以再写 `page` 和 `count`。父行没有 `id` 时，关联结果是空数组。

## 变更

每张表有三个 mutation。返回对象上的 `affected` 对应该次写入的影响行数。未在本次写入里出现的列不会被回读。

```graphql
mutation {
  createUser(id: 9, name: "Ada") { id name affected }
  updateUser(id: 9, name: "Ada Lovelace") { name affected }
  deleteUser(id: 9) { id affected }
}
```

`update` 和 `delete` 的 `id` 必填。

## 错误

失败时 body 里有 GraphQL `errors`。每条错误的 `extensions.code`，以及顶层 `extensions.code`，使用和 NestQL 相同的数字：400 语法或校验，403 无权，500 SQL。整次调用没有成功数据时，HTTP 状态与这个数字相同；已经返回了部分 `data` 时 HTTP 仍是 200。

未出现在 access 名单里的表不会进入 schema，查询它是 GraphQL 校验错误，`code` 为 400。

`{ __schema { queryType { fields { name } } } }` 可以看当前 schema 已经生成的根字段。登记新表之后，下一次请求会重新生成。
