# 嵌套示例

Tommy 的 id 是 `38710`。一次取出用户和相关行。`userId` 保持请求里的大小写，物理表名由方言折叠。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{
    "User": { "id": 38710 },
    "Moment[]": { "Moment": { "userId@": "/User/id" } },
    "Comment[]": { "Comment": { "userId@": "/User/id" } }
  }'
```

```json
{
  "User": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" },
  "Moment[]": [
    { "Moment": { "id": 1, "userId": 38710, "content": "hello" } }
  ],
  "Comment[]": [],
  "code": 200,
  "message": "success"
}
```

引用路径：

| 路径 | 含义 |
| --- | --- |
| `/User/id` | 从本次响应根开始 |
| `User/id` | 从当前元素向外找同级键 |
| `[]/User/id` | 最近一层数组元素 |

引用还没算出来时，读操作得到 `null` 或空数组，写操作回滚。
