# 写入

POST 插入，PUT 更新，DELETE 删除。HTTP 对应 `POST /post`、`POST /put`、`POST /delete`，别名是 `/nestql/post`、`/nestql/put`、`/nestql/delete`。

写操作使用与查询相同的表名和 [引用赋值](./operators.md#ref)。下面的 `Todo` 需要先登记；演示库里已有的是 `User`、`Moment`、`Comment`。比较、`IN`、`LIKE`、`@column`、`@order` 在各 method 上的位置不同，见下文。一次请求仍然只有一个 method，并包在一个本地 JDBC 事务里。任一步失败，整次回滚。

响应带 `count`，表示这条语句的影响行数。GET 的对象和数组不加这个字段。

## 主键 {#id}

默认主键列是 `id`。嵌入引擎时可以用 `idColumn(...)` 换成别的合法标识符，见 [嵌入](../guide/embedding.md)。下面按默认的 `id` 来写。

PUT 和 DELETE 的 WHERE 里必须出现这个主键列，否则拒绝。`id`、`id{}`、`id>`、`id@` 都算。POST 不要求带主键。

## POST {#post}

字段按请求写成 INSERT。只接受等值字段，不接受比较、`IN`、`LIKE` 或其他运算符。也不接受 `@column`、`@order`。没有任何字段时会拒绝。

没写 `id` 时，尝试用 JDBC 生成键回填 `id`。请求里已经带了 `id` 就保持原值。

```bash
curl -s -X POST http://127.0.0.1:8080/post \
  -H 'Content-Type: application/json' \
  -d '{ "Todo": { "title": "买牛奶", "done": 0 } }'
```

```json
{
  "Todo": { "title": "买牛奶", "done": 0, "id": 1, "count": 1 },
  "code": 200,
  "message": "success"
}
```

同一张表的字段名不能重复。

## PUT {#put}

等值形式的主键列放进 WHERE，其他等值字段放进 SET。带运算符的字段一律放进 WHERE，因此 `"id{}": [1, 2]` 可以一次更新多行。主键列本身不会出现在 SET 里。

至少要有一个 SET 字段，并且 WHERE 里要有主键列。

```json
{ "Todo": { "id": 1, "title": "改" } }
```

```json
{
  "Todo": { "title": "改", "id": 1, "count": 1 },
  "code": 200,
  "message": "success"
}
```

```json
{ "Todo": { "id{}": [1, 2], "done": 1 } }
```

响应回显 SET 字段。等值条件（包括等值主键）也会回显。`id{}` 这类非等值条件只用于 WHERE，不会按列表展开到响应里。`count` 是影响行数。

## DELETE {#delete}

字段全部是 WHERE，规则与 GET 的条件相同，并且必须包含主键列。

```json
{ "Todo": { "id": 1 } }
```

```json
{
  "Todo": { "id": 1, "count": 1 },
  "code": 200,
  "message": "success"
}
```

`"id{}": [1, 2]` 按 `IN` 删除多行。`count` 仍是影响行数。

## 批量 {#batch}

批量写入的值是数组，键是 `表名[]`：

```json
{
  "Todo[]": [
    { "title": "一" },
    { "title": "二" }
  ]
}
```

```json
{
  "Todo[]": [
    { "title": "一", "id": 1, "count": 1 },
    { "title": "二", "id": 2, "count": 1 }
  ],
  "code": 200,
  "message": "success"
}
```

每个元素是一行，规则与单行 POST / PUT / DELETE 相同。元素必须是对象。响应是数组，顺序与请求一致。

查询形态不能用于写入：

```json
{ "Todo[]": { "Todo": { "id": 1 } } }
```

POST、PUT、DELETE 遇到这种对象会拒绝。反过来，GET / HEAD 里的 `Todo[]: [ {...} ]` 也会拒绝，查询数组必须是对象。

## 引用 {#ref}

`列名@` 在写入里同样可用。POST 把它当作插入值；PUT 对主键列当作 WHERE，对其他列当作 SET；DELETE 当作 WHERE。

```json
{
  "User": { "id": 38710 },
  "Todo": { "userId@": "/User/id", "title": "跟用户走" }
}
```

这是 POST 时的插入。`userId` 取本次响应里 `User.id`。同级键会先按引用执行。

引用不存在时写入失败并回滚，留下的是空结果而不是插进一行 `null`。读路径的空结果见 [查询](./query.md)。
