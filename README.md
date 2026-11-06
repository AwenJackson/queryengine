# QueryEngine

可嵌入的查询引擎。QueryEngine 支持通过 **GraphQL**、**RESTful**、**NestQL** 接入。三套 HTTP 入口共用同一个引擎、同一份 access 白名单和同一种 SQL 方言。没有登录，没有 Session。

NestQL（Nested Query Language，嵌套查询语言）是引擎自己的 JSON 协议：对象、`[]` 数组、`@column`、`@order`、`key@`。形状形似 APIJSON，实现不依赖、不包含 APIJSON。对外产品名是 NestQL。

核心库没有登录会话。直接调用 `QueryEngine` 时走 NestQL，失败仍抛异常。HTTP 由 `queryengine-web` 提供。

Maven 坐标 `io.github.awenjackson`，版本 `0.1.0`。依赖片段、BOM 和发布步骤见 [docs/embedding.md](docs/embedding.md)、[docs/releasing.md](docs/releasing.md)。扩展解析器、SQL 和授权见 [docs/extending.md](docs/extending.md)。文档站在 `website/`，`npm run docs:dev` 预览。

## 模块

| 模块 | 作用 |
| --- | --- |
| `queryengine-core` | 解析、注册表、参数化 SQL、JDBC 执行。不依赖 Spring，也不监听端口 |
| `queryengine-dialect-mysql` | MySQL 方言（反引号、`LIMIT ? OFFSET ?`） |
| `queryengine-dialect-dameng` | 达梦方言（双引号、`OFFSET/FETCH`） |
| `queryengine-spring-boot-starter` | 可选。只注册 `QueryEngine` Bean |
| `queryengine-web` | GraphQL `POST /graphql`、RESTful `/rest/{table}`、NestQL `POST /get`（别名 `/nestql/get`） |
| `queryengine-boot` | 可直接启动的示例进程。默认激活 mysql profile，端口 8080 |
| `queryengine-demo` | 嵌入示例和契约测试，不是 Web 应用 |

## 环境

- JDK 21
- Maven 3.8+
- 可选：Spring Boot 3.5.3（只用 starter 时）

## 二十行嵌入

宿主已经有 `DataSource`（HikariCP 或其他连接池）时：

```java
DataSource dataSource = existingPool;

QueryEngine engine = QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .schema("demo")
        .defaultPageSize(10)
        .maxPageSize(100)
        .registerTables("User", "Moment", "Comment")
        .build();

JsonNode user = engine.get("""
        { "User": { "id": 38710 } }
        """);

try (SchemaScope scope = engine.withSchema("tenant_b")) {
    JsonNode page = engine.get("""
            { "[]": { "page": 0, "count": 10, "User": { "@order": "id-" } } }
            """);
}
```

对应的示例类是 `dev.queryengine.demo.EmbeddingExample`。库调用失败时抛 `QueryEngineException` 子类。走 NestQL 的 HTTP 入口时，`queryengine-web` 在同一层加上 `code` 与 `message`。成功时 HTTP 状态与 `code` 都是 200。RESTful 把业务内容放在 `data` 下。GraphQL 使用 `data` / `errors`，并在 `extensions.code` 里带同一个数字。

```json
{ "User": { "id": 38710, "name": "Tommy" }, "code": 200, "message": "success" }
```

| code | HTTP | 何时 |
| --- | --- | --- |
| 200 | 200 | 成功。`message` 固定为 `success` |
| 400 | 400 | 非法 JSON、校验失败、`BadRequestException` |
| 403 | 403 | 表未注册或 method 不允许，`AccessDeniedException` |
| 500 | 500 | SQL 或引擎内部失败，`QueryExecutionException` 及其他 `QueryEngineException` |

没有 401，因为不要求登录。三种入口的路径和示例见 [docs/http.md](docs/http.md)。

## 马上启动

默认激活 `mysql` profile。先导入脚本再启动：

```bash
mysql -uroot -p < scripts/schema-mysql.sql
mvn -pl queryengine-boot -am spring-boot:run
```

进程监听 `http://127.0.0.1:8080`。账号默认 `root` / `root`，可用 `MYSQL_USER`、`MYSQL_PASSWORD` 覆盖。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "User": { "id": 38710 } }'
```

这是 NestQL。`/nestql/get` 与 `/get` 相同。期望看到 Tommy，以及 `"code": 200`、`"message": "success"`。

```bash
curl -s http://127.0.0.1:8080/rest/User/38710
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{ "query": "{ User(id: 38710) { id name tag } }" }'
```

达梦脚本是 `scripts/schema-dameng.sql`，标识符为大写双引号，不能把 MySQL 脚本直接拿去执行。说明见 [docs/boot.md](docs/boot.md)。不连 MySQL 时，去掉 `spring.profiles.active` 后主配置里的内存 H2 会执行 `classpath:schema.sql`。

## 运行测试

```bash
mvn test
```

契约测试使用 **H2 的 MySQL 兼容模式**，SQL 仍由 `MySqlDialect` 生成（反引号和 `LIMIT/OFFSET`）。当前环境没有 Docker 时，`MySqlContainerTest` 会跳过。

装好 Docker 后，同一条黄金请求会再打一次真实 MySQL：

```bash
mvn -pl queryengine-demo -Dtest=MySqlContainerTest test
```

达梦没有随仓库提供。方言的 SQL 生成由单元测试覆盖；连上达梦服务器的步骤见 [docs/dialects.md](docs/dialects.md)。

## 文档

- [接入指南](docs/embedding.md)
- [马上启动](docs/boot.md)
- [HTTP：GraphQL、RESTful、NestQL](docs/http.md)
- [NestQL](docs/protocol.md)
- [RESTful](docs/rest.md)
- [GraphQL](docs/graphql.md)
- [registerTable 与全权限](docs/register-table.md)
- [运行时切换 schema](docs/dynamic-schema.md)
- [MySQL 与达梦](docs/dialects.md)
- [可复制示例](docs/examples.md)
- [扩展与覆盖](docs/extending.md)
- [发布到 Maven Central](docs/releasing.md)
