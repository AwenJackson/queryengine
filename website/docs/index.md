# QueryEngine

QueryEngine 支持通过 **GraphQL**、**RESTful**、**NestQL** 接入。三套入口共用同一个引擎、同一份按 schema 懒加载的 access 白名单，以及同一种 SQL 方言。

NestQL（Nested Query Language，嵌套查询语言）是 QueryEngine 自己的 JSON 协议。请求是嵌套对象：表名作键，条件、`@column`、`@order` 和 `key@` 写在对象里。对外产品名是 NestQL。

没有登录，没有 Session。Maven 坐标是 `io.github.awenjackson`，当前版本 `0.1.0`。

## 三条路

| 方式 | 入口 |
| --- | --- |
| NestQL | [`POST /get`](./nestql/index.md)，别名 `POST /nestql/get` |
| RESTful | [`GET /rest/User/38710`](./http/rest.md) |
| GraphQL | [`POST /graphql`](./http/graphql.md)。列参数是自定义标量 `Long` 与 `String` |

运算符、分页、引用和写入写在 [NestQL 语法](./nestql/operators.md)。下一步看 [快速开始](./guide/quick-start.md)。
