# 错误码

没有 401。未登记的表是 403。HTTP 状态与 JSON 里的 `code` 使用同一组数字。失败响应只有 `code` 和 `message`，不带半成品业务对象。

| code | HTTP | 何时 | message |
| --- | --- | --- | --- |
| 200 | 200 | 执行成功 | 固定 `success` |
| 400 | 400 | 结构、标识符、运算符、对象查询命中多行、写操作缺少主键、空请求体 | 异常原文 |
| 400 | 400 | 请求体不是合法 JSON | 固定 `请求不是合法 JSON` |
| 400 | 400 | schema 请求头不是合法标识符，或其他 `IllegalArgumentException` | 异常原文 |
| 403 | 403 | 表未登记，或自定义 `AccessRegistry` 拒绝了该 method | 异常原文，例如 `未注册或无权访问的表: Secret` |
| 500 | 500 | JDBC 或语句失败 | 异常原文。含参数化 SQL，不含绑定值 |
| 500 | 500 | 其他引擎异常 | 异常原文 |

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "Secret": { "id": 1 } }'
```

```json
{ "code": 403, "message": "未注册或无权访问的表: Secret" }
```

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "User": { "@column": "count(id)" } }'
```

响应 HTTP 400，`code` 为 400，`message` 说明 `@column` 不支持函数。

三种入口的成功体外壳不同：

- NestQL 把 `code` / `message` 和业务键放在同一层
- RESTful 把业务内容放在 `data`
- GraphQL 使用 `data` / `errors`，并在顶层 `extensions.code` 与每条错误的 `extensions.code` 带同一个数字。整次调用没有成功数据时，HTTP 状态也用这个数字；已经返回了部分 `data` 时 HTTP 仍是 200

可选请求头 `X-QueryEngine-Schema` 只覆盖这一次调用。头的值必须是合法标识符。
