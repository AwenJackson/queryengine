# 写入

POST 插入，PUT 更新，DELETE 删除。HTTP 对应 `POST /post`、`POST /put`、`POST /delete`，别名是 `/nestql/post`、`/nestql/put`、`/nestql/delete`。

写操作使用与查询相同的表名和 [引用赋值](./operators.md#ref)。下面的 `Todo` 需要先登记；演示库里已有的是 `User`、`Moment`、`Comment`，以及 `Type`、`Book`、`Book_menu`、`Book_menu_tip`。比较、`IN`、`LIKE`、`@column`、`@order` 在各 method 上的位置不同，见下文。一次请求仍然只有一个 method，并包在一个本地 JDBC 事务里。任一步失败，整次回滚。POST 还可以按嵌套一次插入多张表，见 [嵌套插入](#nest)。

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

每个元素是一行，规则与单行 POST / PUT / DELETE 相同。元素必须是对象。响应是数组，顺序与请求一致。元素内部还可以再写子表数组，见 [形态 B](#nest-embed)。

GET / HEAD 里的 `Todo[]: [ {...} ]` 会拒绝，查询数组必须是对象。PUT / DELETE 不接受查询形态的 `Todo[]: { ... }`，也不接受写在行内的子表数组。

## 嵌套插入 {#nest}

POST 可以在一次请求里插入一棵表。深度没有协议上限，和查询一样，只受数据库与调用栈限制。每一层用到的表都要先登记，子表未登记会失败并回滚。PUT / DELETE 不走这棵树。

不兼容变更：以前查询形态的 `表名[]: { ... }` 在 POST 上会直接拒绝。现在这种对象表示插入，不再拒绝。

有两种写法。响应都带回生成的主键和每行自己的 `count`。没写 `id` 时仍尝试用 JDBC 生成键回填。

### 形态 A：查询树 {#nest-tree}

对象的形状与 GET 的数组相同：`表名[]` 的值是对象，里面有主表和子级 `表名[]`。POST 只插入主表的一行，再按嵌套插入子表。响应把这一层收成只有一个元素的数组，元素里的键顺序与请求一致。

同一层第一张不依赖兄弟键的表是主表，规则与查询相同。子表用 `[]/父表/id` 读取刚插入的父行。父表如果写在响应根上，用 `/父表/id`。

`page`、`count`、`query` 是查询用的，插入形态里出现标量会拒绝。`tag` 仍然忽略。

```json
{
  "Comment[]": {
    "Comment": { "momentId": 1, "userId": 38710, "content": "hello" },
    "Type[]": {
      "Type": { "user_id": 38710, "type_name": "类型", "comment_id@": "[]/Comment/id" },
      "Book[]": {
        "Book": { "user_id": 38710, "book_name": "书", "book_desc": "d", "type_id@": "[]/Type/id" },
        "Book_menu[]": {
          "Book_menu": { "user_id": 38710, "menu_name": "章", "book_id@": "[]/Book/id" },
          "Book_menu_tip[]": {
            "Book_menu_tip": { "tip_name": "tip", "book_menu_id@": "[]/Book_menu/id" }
          }
        }
      }
    }
  }
}
```

```json
{
  "Comment[]": [
    {
      "Comment": { "momentId": 1, "userId": 38710, "content": "hello", "id": 100, "count": 1 },
      "Type[]": [
        {
          "Type": { "user_id": 38710, "type_name": "类型", "comment_id": 100, "id": 100, "count": 1 },
          "Book[]": [
            {
              "Book": {
                "user_id": 38710,
                "book_name": "书",
                "book_desc": "d",
                "type_id": 100,
                "id": 100,
                "count": 1
              },
              "Book_menu[]": [
                {
                  "Book_menu": { "user_id": 38710, "menu_name": "章", "book_id": 100, "id": 100, "count": 1 },
                  "Book_menu_tip[]": [
                    {
                      "Book_menu_tip": { "tip_name": "tip", "book_menu_id": 100, "id": 100, "count": 1 }
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

要一次插入多行父记录，用下面的形态 B，或批量数组。形态 A 的这一层只有一行主表。

### 形态 B：写在父行里 {#nest-embed}

子表数组放在父行对象内部。`子表[]` 的值是 JSON 数组，每个元素是一行：列和值，再加上它自己的子表数组。例如 `{ "type_name": "类型", "Book[]": [ ... ] }`。

```json
{
  "Comment": {
    "momentId": 1,
    "userId": 38710,
    "content": "hello",
    "Type[]": [
      {
        "user_id": 38710,
        "type_name": "类型",
        "Book[]": [
          {
            "user_id": 38710,
            "book_name": "书",
            "book_desc": "d",
            "Book_menu[]": [
              {
                "user_id": 38710,
                "menu_name": "章",
                "Book_menu_tip[]": [ { "tip_name": "tip" } ]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

父行如果在响应根上，子行里的 `/Comment/id` 就是刚插入的主键。写在批量元素里时，用 `[]/Comment/id`。响应把子表数组留在父行里，每个元素带上生成的 `id` 和 `count`。

批量 `Comment[]: [ { ... , "Type[]": [ ... ] }, ... ]` 会对每一行父记录再插它的子表。

父对象里的 `子表[]` 如果值是对象而不是数组，按形态 A 再插一层。

### 外键 {#nest-fk}

子行省略外键，并且这一层只有一个父表时，会把父行主键填进去。列名不猜测：

- 子行里已有 `user_id` 这类下划线列，父表 `Comment` → `comment_id`
- 子行里已有 `userId` 这类驼峰列，父表 `Moment` → `momentId`
- 父表名本身带下划线，例如 `Book_menu` → 只有 `book_menu_id`
- 两种候选都说得通，或同一行里两种写法同时出现 → 拒绝。请写上该列，或使用 `列名@`
- 同一层有多张父表，子行又没写外键 → 拒绝，不挑选其中一张

已经写了 `comment_id` 或 `comment_id@`（也包括显式 `null`）时，保持原值。列名按请求原文写入，不会在驼峰和下划线之间转换。

任一子行失败，整次请求回滚，已经插入的父行也不会留下。

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
