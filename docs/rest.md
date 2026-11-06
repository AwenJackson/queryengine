# RESTful

QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。本文是 RESTful CRUD。它和 NestQL、GraphQL 共用同一个引擎：表名走当前 schema 的 access 白名单，SQL 由现有方言生成。没有登录。

`queryengine.http.base-path` 会加在路径前面。下面按空前缀来写。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/rest/{table}` | 列表。查询参数 `page`（从 0 开始）、`count` 或 `size`，其余参数是等值过滤 |
| GET | `/rest/{table}/{id}` | 按主键读一行。没有这条记录时 `data` 为 `null`，HTTP 仍是 200 |
| POST | `/rest/{table}` | 创建。请求体是列的 JSON 对象 |
| PUT | `/rest/{table}/{id}` | 更新。请求体是要改的列，路径里的 id 写入主键条件 |
| DELETE | `/rest/{table}/{id}` | 删除 |

表名大小写不敏感：`/rest/user` 与 `/rest/User` 相同。未注册的表是 403。过滤字段必须是合法标识符。看起来像整数的过滤值和路径 id 会按整数绑定，其余按字符串。

可选请求头 `X-QueryEngine-Schema` 只覆盖这一次调用的 schema。

成功响应把业务内容放在 `data` 下：

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

列表：

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

写入沿用引擎的结果。`count` 是影响行数：

```bash
curl -s -X POST http://127.0.0.1:8080/rest/User \
  -H 'Content-Type: application/json' \
  -d '{ "id": 9, "name": "Ada" }'

curl -s -X PUT http://127.0.0.1:8080/rest/User/9 \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Ada Lovelace" }'

curl -s -X DELETE http://127.0.0.1:8080/rest/User/9
```

错误码与 [http.md](http.md) 相同：400 非法请求，403 表未注册，500 SQL 失败。HTTP 状态与 `code` 一致。
