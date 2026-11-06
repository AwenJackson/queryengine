# QueryEngine

QueryEngine 支持通过 **GraphQL**、**RESTful**、**NestQL** 接入。三套入口共用同一个引擎、同一份按 schema 懒加载的 access 白名单，以及同一种 SQL 方言。

NestQL（Nested Query Language，嵌套查询语言）是引擎自己的 JSON 协议。形状形似 APIJSON，实现不依赖、不包含 APIJSON。对外产品名是 NestQL。

没有登录，没有 Session。Maven 坐标是 `io.github.awenjackson`，当前版本 `0.1.0`。

## 三条路

| 方式 | 入口 |
| --- | --- |
| NestQL | `POST /get`，别名 `POST /nestql/get` |
| RESTful | `GET /rest/User/38710` |
| GraphQL | `POST /graphql`，主键变量写 `Long!`，不要写 `ID!` 或 `Int!` |

下一步看 [快速开始](./guide/quick-start.md)。
