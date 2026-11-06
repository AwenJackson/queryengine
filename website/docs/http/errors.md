# 错误码

没有 401。HTTP 状态与 JSON 里的 `code` 相同。

| code | 何时 |
| --- | --- |
| 200 | 成功。`message` 固定 `success` |
| 400 | JSON 不合法、校验失败、对象查询命中多行、写操作缺主键 |
| 403 | 表未注册，或自定义 `AccessRegistry` 拒绝了该 method |
| 500 | SQL 或引擎内部失败 |

NestQL 的成功响应把 `code` / `message` 和业务键放在同一层。RESTful 把业务内容放在 `data`。GraphQL 使用 `data` / `errors`，并在 `extensions.code` 带同一个数字。整次 GraphQL 没有成功数据时，HTTP 状态也用这个数字。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "Secret": { "id": 1 } }'
```

```json
{ "code": 403, "message": "未注册或无权访问的表: Secret" }
```
