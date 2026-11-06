# 嵌套示例

演示库里 Tommy 的 id 是 `38710`。下面的响应对应该库：`user`、`moment`、`comment` 已登记为 `User`、`Moment`、`Comment`。`userId`、`momentId` 保持请求和物理列里的大小写。条件见 [运算符](./operators.md)，数组见 [查询](./query.md)。

## 同一次取出用户和关联行 {#user}

`Moment` 与 `Comment` 用 `/User/id` 读取本次响应根上的用户 id。`@order` 把行序固定为 id 升序。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{
    "User": { "id": 38710 },
    "Moment[]": {
      "Moment": { "userId@": "/User/id", "@order": "id+" }
    },
    "Comment[]": {
      "Comment": { "userId@": "/User/id", "@order": "id+" }
    }
  }'
```

```json
{
  "User": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" },
  "Moment[]": [
    { "Moment": { "id": 1, "userId": 38710, "content": "hello" } },
    { "Moment": { "id": 2, "userId": 38710, "content": "world" } }
  ],
  "Comment[]": [
    { "Comment": { "id": 13, "momentId": 1, "userId": 38710, "content": "aoisa" } }
  ],
  "code": 200,
  "message": "success"
}
```

- `User` 是对象查询，按主键取一行
- `Moment[]` 的主表是 `Moment`，`userId` 等于 `38710`，所以有 `hello` 和 `world`
- `Comment[]` 按评论自己的 `userId` 过滤。Tommy 写过的评论是 `aoisa`。空结果会是 `[]`，对象查询没有行则是 `null`

`Moment[]` 写在 `User` 后面也没关系。引用会让 `User` 先执行，响应键仍按请求顺序排列。直接调用 `engine.get` 时没有 `code` 和 `message`。

## 按每一行再查 {#per-row}

数组元素内部用 `[]/User/id` 表示「当前这一行的 User.id」。评论再挂到当前动态上，用 `[]/Moment/id`。

```json
{
  "[]": {
    "count": 5,
    "page": 0,
    "User": {
      "id{}": [38710, 82001],
      "@column": "id,name",
      "@order": "id+"
    },
    "Moment[]": {
      "count": 5,
      "Moment": {
        "userId@": "[]/User/id",
        "@column": "id,content",
        "@order": "id+"
      },
      "Comment[]": {
        "count": 5,
        "Comment": {
          "momentId@": "[]/Moment/id",
          "@column": "id,content",
          "@order": "id+"
        }
      }
    }
  }
}
```

```json
{
  "[]": [
    {
      "User": { "id": 38710, "name": "Tommy" },
      "Moment[]": [
        {
          "Moment": { "id": 1, "content": "hello" },
          "Comment[]": [
            { "Comment": { "id": 10, "content": "nice" } },
            { "Comment": { "id": 11, "content": "good" } },
            { "Comment": { "id": 13, "content": "aoisa" } }
          ]
        },
        {
          "Moment": { "id": 2, "content": "world" },
          "Comment[]": [
            { "Comment": { "id": 12, "content": "ok" } }
          ]
        }
      ]
    },
    {
      "User": { "id": 82001, "name": "Tom" },
      "Moment[]": [
        {
          "Moment": { "id": 3, "content": "other" },
          "Comment[]": []
        }
      ]
    }
  ],
  "code": 200,
  "message": "success"
}
```

主表是 `User`（`id{}` 选出 Tommy 和 Tom）。每个用户再查自己的 `Moment`，每条动态再查自己的 `Comment`。Tom 的动态 `other` 没有评论，所以内层是空数组。

`count` 与表对象同级。`@column` 与 `@order` 写在表对象里面。

## 引用路径 {#paths}

| 路径 | 含义 |
| --- | --- |
| `/User/id` | 从本次响应根开始 |
| `User/id` | 从当前元素向外找同级键 |
| `[]/User/id` | 最近一层数组元素 |
| `[]/[]/User/id` | 再向外回退一层 |

引用还没算出来时，读操作得到 `null` 或空数组，写操作回滚。规则见 [运算符](./operators.md#ref) 和 [写入](./write.md#ref)。
