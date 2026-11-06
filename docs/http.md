# HTTP 入口

QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。三套入口共用同一个引擎、同一份按 schema 懒加载的 access 白名单，以及同一种 SQL 方言（MySQL 或达梦）。没有登录、没有 Session、没有过滤器里的用户态。表名大小写不敏感。

| 方式 | 路径 | 文档 |
| --- | --- | --- |
| GraphQL | `POST /graphql` | [graphql.md](graphql.md)。列参数只有自定义标量 `Long` 与 `String`，没有内置 `ID` / `Int`；变量里的主键写 `Long!` |
| RESTful | `/rest/{table}`、`/rest/{table}/{id}` | [rest.md](rest.md) |
| NestQL | `POST /get` `/head` `/post` `/put` `/delete`，别名 `/nestql/...` | 下文，协议见 [protocol.md](protocol.md) |

`queryengine.http.base-path` 默认为空。设成 `/api` 时三条路径都带上这个前缀，例如 `POST /api/graphql`、`GET /api/rest/User/1`、`POST /api/get`。不要在末尾再加斜杠。`queryengine.http.enabled=false` 可以关掉这些路由。

可选请求头 `X-QueryEngine-Schema` 只覆盖这一次调用的 schema。不读 Cookie，也不创建会话。

依赖：

```xml
<dependency>
    <groupId>io.github.awenjackson</groupId>
    <artifactId>queryengine-web</artifactId>
    <version>0.1.0</version>
</dependency>
```

同时配置数据源和 `queryengine.tables`，与 [embedding.md](embedding.md) 里的 starter 配置相同。

## NestQL

NestQL 是嵌套 JSON。全部是 `POST`，`Content-Type: application/json`。请求体就是库使用的那份 JSON。`/nestql/get` 与 `/get` 调用同一个方法，其余写操作同理。

| 路径 | 库方法 |
| --- | --- |
| `/get`、`/gets`、`/nestql/get`、`/nestql/gets` | `GET` |
| `/head`、`/heads`、`/nestql/head`、`/nestql/heads` | `HEAD` |
| `/post`、`/nestql/post` | `POST` |
| `/put`、`/nestql/put` | `PUT` |
| `/delete`、`/nestql/delete` | `DELETE` |
| `/access/register` | 登记一张表，立刻可查，并写入 `access` |
| `/access/unregister` | 停用一张表（`enabled = 0`） |
| `/access/list` | 返回当前内存里已启用的表 |

`/gets` 与 `/heads` 只是别名，权限和 SQL 与 `/get`、`/head` 相同。数组仍然写在请求体里的 `[]` 上，不靠路径区分。

## 本地马上跑

```bash
mvn -pl queryengine-boot -am spring-boot:run
```

默认激活 `mysql` profile，端口 8080。先导入 `scripts/schema-mysql.sql`。下面这条是 NestQL：

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "User": { "id": 38710 } }'
```

`mysql` profile 已经是默认值，导入脚本后直接启动即可。达梦改用 `scripts/schema-dameng.sql` 和 `dameng` profile。步骤见 [boot.md](boot.md)。

## 成功信封

业务键保持请求的结构，并在**同一层**加上 `code` 和 `message`。成功时 HTTP 状态是 200。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "User": { "id": 38710 } }'
```

```json
{
  "User": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" },
  "code": 200,
  "message": "success"
}
```

数组同样：

```bash
curl -s -X POST http://127.0.0.1:8080/gets \
  -H 'Content-Type: application/json' \
  -d '{ "[]": { "count": 2, "User": { "@column": "id,name", "@order": "id+" } } }'
```

`code` 和 `message` 是信封保留字段。不要用它们做表名，否则会被覆盖。

直接调用 `engine.get(...)` 的返回值仍然没有这两个字段。只有 HTTP 层添加。

## 错误码

HTTP 状态与 JSON 里的 `code` 使用同一组数字。失败响应只有 `code` 和 `message`，不带半成品业务对象。

| code | HTTP | 异常或场景 | message |
| --- | --- | --- | --- |
| 200 | 200 | 执行成功 | 固定 `success` |
| 400 | 400 | `BadRequestException`：结构、标识符、运算符、对象查询命中多行、写操作缺少主键、空请求体 | 异常原文 |
| 400 | 400 | 请求体不是合法 JSON（`HttpMessageNotReadableException`） | 固定 `请求不是合法 JSON` |
| 400 | 400 | `IllegalArgumentException`，例如 schema 请求头不是合法标识符 | 异常原文 |
| 403 | 403 | `AccessDeniedException`：表未注册，或自定义注册表拒绝了该 method | 异常原文，例如 `未注册或无权访问的表: Secret` |
| 500 | 500 | `QueryExecutionException`：JDBC 失败 | 异常原文。含参数化 SQL，不含绑定值 |
| 500 | 500 | 其他 `QueryEngineException` | 异常原文 |

不使用 401。未注册表是 403，不是“未登录”。

失败示例：

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

切换 schema：

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -H 'X-QueryEngine-Schema: app_b' \
  -d '{ "User": { "id": 1, "@column": "name" } }'
```
