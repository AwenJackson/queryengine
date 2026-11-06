# GraphQL

`POST /graphql`，body 是 `{ "query", "variables", "operationName" }`。字段名用 access 里登记的拼写，例如 `User`。

## 标量

列参数只有两种类型：

- 整数列：自定义标量 `Long`
- 其他列：`String`

**没有** GraphQL 规范里的内置标量 `ID`、`Int`、`Float`、`Boolean` 用来描述这些列。变量类型必须和参数一致。

| 声明 | 结果 |
| --- | --- |
| `$uid: ID!` | `Unknown type 'ID'` |
| `$uid: Int!` | `VariableTypeMismatch`，参数实际是 `Long` |
| `$uid: Long!` | 可以通过校验 |

查询里直接写 `User(id: 38710)` 不用声明变量类型。分页参数 `page`、`count` 才是内置 `Int`。关联参数 `on` 是 `String!`。

## 查询

```bash
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query ($uid: Long!) { User(id: $uid) { id name Moment(on: \"userId\") { id content } } }","variables":{"uid":38710}}'
```

`User` 返回一行，`UserList` 返回数组。子字段如果是另一张已注册表，并且写了 `on`，会再查一次，不是 JOIN。

## 变更

```graphql
mutation {
  createUser(id: 9, name: "Ada") { id name affected }
  updateUser(id: 9, name: "Ada Lovelace") { name affected }
  deleteUser(id: 9) { id affected }
}
```

`update` / `delete` 的 `id` 必填。`affected` 是这次影响行数。

schema 按当前 schema 的 access 名单懒生成。未登记的表不会出现，查询它是校验错误，`code` 为 400。
