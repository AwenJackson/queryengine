# RESTful

路径受 `queryengine.http.base-path` 影响。表名大小写不敏感，并走当前 schema 的 access 白名单。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/rest/{table}` | 列表。`page` 从 0 开始，`count` 或 `size` 为页大小，其余参数是等值过滤 |
| GET | `/rest/{table}/{id}` | 按主键。没有记录时 `data` 为 `null`，HTTP 仍是 200 |
| POST | `/rest/{table}` | 创建 |
| PUT | `/rest/{table}/{id}` | 更新。路径 id 与请求体 id 不一致时是 400 |
| DELETE | `/rest/{table}/{id}` | 删除 |

```bash
curl -s 'http://127.0.0.1:8080/rest/User?name=Tommy&count=10'
curl -s http://127.0.0.1:8080/rest/User/38710
```

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" }
}
```

写入结果里的 `count` 是影响行数。可选头 `X-QueryEngine-Schema` 只覆盖这一次调用。
