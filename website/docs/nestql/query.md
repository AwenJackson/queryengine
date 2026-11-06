# 查询

GET 返回行，HEAD 返回行数。两者都使用 [运算符](./operators.md)。HTTP 对应 `POST /get` 与 `POST /head`（含 `/gets`、`/heads` 和 `/nestql/...` 别名）。

整次请求在一个本地 JDBC 事务里执行。

## 对象查询 {#object}

表名对应一个对象时，最多返回一行。没有行，或引用没有解析到值时，结果是 `null`。命中两行及以上会失败，提示改用数组。

```json
{ "User": { "id": 38710, "@column": "id,name" } }
```

```json
{
  "User": { "id": 38710, "name": "Tommy" },
  "code": 200,
  "message": "success"
}
```

库方法的返回值没有 `code` 和 `message`。对象查询不额外加 `count`。

对象查询固定按最多两行探测，不使用 `page` / `count`。要分页就用数组。

## 数组 {#array}

`[]` 与 `User[]` 的查询方式相同。响应键用请求里的名字。元素是 `{ "表名": { ...列 } }`，不是裸的列对象。

```json
{
  "User[]": {
    "page": 0,
    "count": 2,
    "User": {
      "@column": "id,name",
      "@order": "id+"
    }
  }
}
```

```json
{
  "User[]": [
    { "User": { "id": 10001, "name": "Blank" } },
    { "User": { "id": 38710, "name": "Tommy" } }
  ],
  "code": 200,
  "message": "success"
}
```

`page` 和 `count` 与表对象同级，写在数组对象上。写进表对象里会变成那一列的等值条件，同时数组仍走默认分页：

```json
{ "[]": { "User": { "page": 0 } } }
```

上面这条是 `User.page = 0`，不是第 0 页。

数组里第一张不依赖兄弟键的表是主表，按分页查询一次。其余对象表和嵌套数组对主表的每一行再查，不是 JOIN。

```json
{
  "[]": {
    "count": 10,
    "Moment": { "userId@": "User/id", "@column": "id,content" },
    "User": { "id": 82001, "@column": "id,name" }
  }
}
```

```json
{
  "[]": [
    {
      "Moment": { "id": 3, "content": "other" },
      "User": { "id": 82001, "name": "Tom" }
    }
  ],
  "code": 200,
  "message": "success"
}
```

`Moment` 引用同级 `User`，所以先查 `User`。即使 `Moment` 写在前面，主表仍是 `User`。每个元素里再按该行的 `User.id` 查 `Moment`。响应键的顺序与请求一致：先 `Moment`，后 `User`。

这里的 `Moment` 是对象，只能有一行。Tom（`82001`）只有一条动态。Tommy 有两条，同一位置要写成 `Moment[]`，否则对象查询会因为命中多行而失败。完整嵌套见 [示例](./example.md#per-row)。

同一层可以继续嵌套，例如在 `Moment` 里再放 `Comment[]`。层数没有协议上限，见 [限制](./limits.md)。

GET 的数组不额外加 `count`。当前页有多少行，数组就有多少个元素。

## 分页 {#page}

`page` 从 0 开始，偏移是 `page * count`。

| 字段 | 缺省 | 非法 |
| --- | --- | --- |
| `page` | `0` | 负数，或不是整数 |
| `count` | `defaultPageSize`，默认 10 | `<= 0`，或不是整数 |

`count` 大于 `maxPageSize`（默认 100）时夹到上限。`page` 大于 `maxPageIndex`（默认 0，表示不限制）时夹到上限。两者都是夹紧，不是报错。配置方式见 [限制](./limits.md)。

`page * count` 超出整数偏移范围时，请求失败。

## HEAD {#head}

HEAD 返回总行数，忽略 `page`、`count`、`@column`、`@order`。条件仍然生效。

演示库里 `sex = 0` 的用户有 3 行：

```json
{ "User": { "sex": 0 } }
```

```json
{ "User": { "count": 3 }, "code": 200, "message": "success" }
```

数组的 HEAD 只统计主表，响应是对象而不是数组。下面 `Moment` 依赖 `User`，主表是 `User`，`sex = 0` 在演示库里是 3 行：

```json
{ "[]": { "User": { "sex": 0 }, "Moment": { "userId@": "User/id" } } }
```

```json
{ "[]": { "count": 3 }, "code": 200, "message": "success" }
```

`page` 和 `count` 同样被忽略。演示库里 `User` 共 4 行，`{ "[]": { "count": 1, "User": {} } }` 的 HEAD 仍是 `{ "[]": { "count": 4 } }`。

主表引用没有解析到值时，HEAD 的 `count` 是 `0`。

数组上的数字 `query` 会被忽略。GET 返回当前页，HEAD 返回总行数，没有按 `query` 组合这两种结果的写法。

## 响应 {#response}

HTTP 成功时，业务键与 `code`、`message` 在同一层。直接调用 `engine.get` / `engine.head` 时没有这两个字段。

值的形状：

- 整型列统一成 64 位整数
- 日期、时间和时间戳转成字符串
- 二进制转成 Base64 字符串
- 引用缺失或没有行：对象为 `null`，数组为 `[]`

多个根键按请求顺序出现在响应里。执行顺序另按引用拓扑排序，以便后面的键仍能被前面的 `key@` 读到。

## 忽略的键 {#meta}

这些键如果不是对象，会被跳过，不当作表：

| 位置 | 键 |
| --- | --- |
| 根 | 字符串或其他标量形式的 `tag`、`version`、`format` |
| 数组对象 | 标量形式的 `query`、`tag`。`page` 和 `count` 单独作为分页读取 |

它们如果是对象，仍然是表名。例如根上的 `"tag": { "id": 1 }` 会查询名为 `tag` 的表。

根上其他无法识别的标量，例如 `"debug": true`，会拒绝。
