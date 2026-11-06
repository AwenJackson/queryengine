# NestQL

NestQL 是嵌套 JSON。库方法是 `get` / `head` / `post` / `put` / `delete`。HTTP 对应 `POST /get` 等，`/nestql/get` 是别名。

一次调用只有一个 method。条件之间是 AND。对象查询命中两行及以上会失败，列表用 `[]`。

```json
{ "User": { "id": 38710 } }
```

```json
{
  "[]": {
    "page": 0,
    "count": 10,
    "User": { "@column": "id,name", "@order": "id+" }
  }
}
```

| 写法 | 含义 |
| --- | --- |
| `"id": 1` | 等值，`null` 是 `IS NULL` |
| `"sex!": 0` | 不等于 |
| `"id{}": [1, 2]` | `IN` |
| `"name$": "%Tom%"` | `LIKE`，不会自动补 `%` |
| `"userId@": "/User/id"` | 引用本次响应里的值 |

`@column` 只接受列名和 `列:别名`。`@order` 用 `列+` / `列-`。不做 JOIN，关联是按引用再查。PUT / DELETE 必须带主键。

库方法的成功响应没有 `code`。HTTP 在同一层加上 `code` 和 `message`。没有查询深度硬上限。`page` / `count` 只做软夹紧。

NestQL 的完整运算符表见仓库 `docs/protocol.md`。
