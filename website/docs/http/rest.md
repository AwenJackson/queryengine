# RESTful

RESTful 是按表的 CRUD。它和 NestQL、GraphQL 共用同一个引擎：表名走当前 schema 的 access 白名单，SQL 由同一种方言生成。没有登录。

`queryengine.http.base-path` 加在路径前面。下面按空前缀来写。设成 `/api` 时，主键读取是 `GET /api/rest/User/38710`。

比较、`IN`、`LIKE`、`@column` 和嵌套引用属于 [NestQL](../nestql/operators.md)。REST 的查询参数只有等值。

## 路径 {#paths}

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/rest/{table}` | 列表 |
| GET | `/rest/{table}/{id}` | 按主键列 `id` 读一行 |
| POST | `/rest/{table}` | 创建。请求体是列的 JSON 对象 |
| PUT | `/rest/{table}/{id}` | 更新。请求体是要改的列 |
| DELETE | `/rest/{table}/{id}` | 按主键删除 |

表名大小写不敏感：`/rest/user` 与 `/rest/User` 相同。未登记的表是 403。主键这一层固定使用列 `id`。

可选请求头 `X-QueryEngine-Schema` 只覆盖这一次调用的 schema。

成功时业务内容在 `data` 下，`code` 为 200，`message` 为 `success`。错误码与 [错误码](./errors.md) 相同，HTTP 状态与 `code` 一致。

## 查询参数 {#query}

`GET /rest/{table}` 的查询参数：

| 参数 | 作用 |
| --- | --- |
| `page` | 页码，从 0 开始。省略时是 0。必须是整数 |
| `count` | 页大小。省略时用引擎的 `defaultPageSize`（默认 10） |
| `size` | `count` 的别名。两者都写时采用 `count` |
| 其他参数 | 物理列上的等值过滤 |

过滤字段必须是合法标识符（ASCII 字母、数字、下划线，并且以字母或下划线开头）。列名保持参数里的拼写，不会做驼峰转换。看起来像整数的值（可选负号加数字）按 64 位整数绑定，其余按字符串绑定。

`page` / `count` 的软夹紧与 [NestQL](../nestql/limits.md) 相同。

```bash
curl -s 'http://127.0.0.1:8080/rest/User?name=Tommy&page=0&count=10'
```

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" }
  ]
}
```

列表里的元素是行本身，没有 NestQL 数组的 `{ "User": { ... } }` 包装。

按主键读取。没有这条记录时 `data` 为 `null`，HTTP 仍是 200：

```bash
curl -s http://127.0.0.1:8080/rest/User/38710
```

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" }
}
```

路径中的 id 同样按「像整数则绑定为整数，否则为字符串」处理。

## 写入 {#write}

请求体必须是 JSON 对象。`data` 是这一行的写入结果，其中 `count` 是影响行数。

```bash
curl -s -X POST http://127.0.0.1:8080/rest/User \
  -H 'Content-Type: application/json' \
  -d '{ "id": 9, "name": "Ada" }'
```

```bash
curl -s -X PUT http://127.0.0.1:8080/rest/User/9 \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Ada Lovelace" }'
```

```bash
curl -s -X DELETE http://127.0.0.1:8080/rest/User/9
```

PUT 把路径中的 id 写入主键条件。请求体里如果带了 `id`，必须与路径一致，否则是 400，`message` 为 `路径中的 id 与请求体不一致`。

POST 省略 `id` 时，与 NestQL 一样尝试回填数据库生成的键。字段规则与 [NestQL 写入](../nestql/write.md) 的单行 POST / PUT / DELETE 相同。REST 不提供 `表名[]` 批量体；要一次写多行，用 NestQL 的 [批量写入](../nestql/write.md#batch)。

空请求体、非对象请求体、以及 `page` / `count` / `size` 不是整数，都是 400。
