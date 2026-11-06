# GraphQL

`POST /graphql`。`queryengine.http.base-path` 会加在路径前面，例如 `/api/graphql`。可选请求头 `X-QueryEngine-Schema` 只覆盖这一次调用，并据此选择那一份 access 名单。

schema 在第一次查询某个 schema 时，按该 schema 的 access 名单懒生成。列来自物理表的结果集元数据，使用物理列名，不会做驼峰转换。没有登录。

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

## 请求 {#request}

请求体是 JSON 对象：

| 字段 | 要求 |
| --- | --- |
| `query` | 必填，GraphQL 文档字符串 |
| `variables` | 省略或 JSON 对象 |
| `operationName` | 省略，或文档里某个操作的名字 |

```json
{
  "query": "query ($uid: Long!) { User(id: $uid) { id name } }",
  "variables": { "uid": 38710 },
  "operationName": null
}
```

根字段名是 access 里登记的拼写，例如 `User`。GraphQL 按这个字段名匹配。

## 标量 {#scalars}

列参数只有两种类型：

- 整数列（`BIGINT`、`INTEGER`、`SMALLINT`、`TINYINT`）：自定义标量 `Long`
- 其他列：`String`

这些列参数不使用规范里的内置标量 `ID`、`Int`、`Float`、`Boolean`。变量类型必须和参数一致。

| 声明 | 结果 |
| --- | --- |
| `$uid: ID!` | `Unknown type 'ID'` |
| `$uid: Int!` | `Variable type mismatch`，参数实际类型是 `Long` |
| `$uid: Long!` | 可以通过校验 |

查询里直接写数字字面量（`User(id: 38710)`）不需要声明变量类型。分页参数 `page`、`count` 是内置 `Int`。关联参数 `on` 是 `String!`。

## 查询字段 {#query}

每张已登记的表有两个根字段：

| 字段 | 返回 | 参数 |
| --- | --- | --- |
| `User(...)` | 一行，或 `null` | 各列的等值条件 |
| `UserList(page, count, ...)` | 数组 | 同上，另加分页 |

若另一张已登记的表刚好叫 `UserList`，列表字段改名为 `UserRows`。

列参数是等值过滤，对应 NestQL 里的 `"列": 值`。比较、`IN`、`LIKE` 不在 GraphQL 参数里，那些写在 [NestQL](../nestql/operators.md)。命中两行及以上时是 400，与 NestQL 的对象查询相同。没有行时该字段为 `null`。

`page` 从 0 开始。`page` 和 `count` 是分页参数，名字正好是 `page` 或 `count` 的列不会同时成为过滤参数。列表省略 `count` 时使用引擎的 `defaultPageSize`（默认 10）。`page` 省略时为 0。软限制与 [NestQL 分页](../nestql/limits.md) 相同。

选择集里的列会变成 `@column`。没选列、只选了关联字段时，父查询会补上 `id`，供关联使用。

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

## 嵌套 {#nested}

子字段名如果是另一张已登记的表，并且带了必填参数 `on`，就再查一次列表。`on` 是子表里指向父行 `id` 的物理列名，必须是合法标识符。这是按该列等值再查，不是 JOIN。

```graphql
{
  User(id: 38710) {
    id
    name
    Moment(on: "userId", page: 0, count: 10) {
      id
      content
    }
  }
}
```

关联上的 `page`、`count` 可省略。省略 `count` 时这一层用 100，省略 `page` 时用 0。父行没有 `id` 时，关联结果是空数组。

与父表同名的字段，或与某个物理列同名的表，不会再生成关联字段，以免覆盖列。

## 变更 {#mutation}

每张表有三个 mutation。参数名是物理列名。返回对象上的 `affected` 对应该次写入的 `count`。选择集里没出现的列不会回读。表本身已有名为 `affected` 的列时，使用那个列，不再另加影响行数。

```graphql
mutation {
  createUser(id: 9, name: "Ada") { id name affected }
  updateUser(id: 9, name: "Ada Lovelace") { name affected }
  deleteUser(id: 9) { id affected }
}
```

| 字段 | 参数 | 主键 |
| --- | --- | --- |
| `createUser` | 各列，均可省略到只剩要插入的字段 | 可省略，由数据库生成 |
| `updateUser` | 要修改的列 | `id` 必填，类型与该列一致；表上没有 `id` 列时为 `Long!` |
| `deleteUser` | 只有 `id` | `id` 必填 |

`update` / `delete` 缺少 `id` 会失败。GraphQL 这一层的主键列固定是 `id`，与库构建器里的 `idColumn(...)` 相互独立。

## 错误与 schema {#errors}

失败时 body 里有 GraphQL `errors`。每条错误的 `extensions.code`，以及顶层 `extensions.code`，使用和 NestQL 相同的数字：400 语法或校验，403 无权，500 SQL。整次调用没有成功数据时，HTTP 状态与这个数字相同；已经返回了部分 `data` 时 HTTP 仍是 200。说明见 [错误码](./errors.md)。

未出现在 access 名单里的表不会进入 schema。查询它是 GraphQL 校验错误，`code` 为 400。

```graphql
{ __schema { queryType { fields { name } } } }
```

可以看当前 schema 已经生成的根字段。登记新表之后，下一次请求会按新的名单重新生成。名单为空时，查询根上只有占位字段 `_empty`。
