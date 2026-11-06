# 接入指南

QueryEngine 可以嵌在宿主进程里，也可以通过 `queryengine-web` 暴露 HTTP。核心模块 `queryengine-core` 只依赖 Jackson 和 SLF4J API，不启动 Web 服务器，不读取 HTTP 会话。库方法失败时抛异常，不自己写 `code`。HTTP 模块才会加上统一的 `code` / `message`，见 [http.md](http.md)。

## Maven

坐标是 `io.github.awenjackson`，当前版本 `0.1.0`。用 BOM 对齐各模块版本：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.awenjackson</groupId>
            <artifactId>queryengine-bom</artifactId>
            <version>0.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<dependency>
    <groupId>io.github.awenjackson</groupId>
    <artifactId>queryengine-dialect-mysql</artifactId>
</dependency>
```

达梦宿主把 `queryengine-dialect-mysql` 换成 `queryengine-dialect-dameng`，并另加达梦 JDBC 驱动。两者可以同时依赖，由 `Dialect` 实例决定实际 SQL。

只要 Bean、不要端口时，依赖 `queryengine-spring-boot-starter`。starter 只注册 `QueryEngine`，不声明 Controller。

要 HTTP 入口时再加 `queryengine-web`（它已经带上 starter 和 `spring-boot-starter-web`）：

```xml
<dependency>
    <groupId>io.github.awenjackson</groupId>
    <artifactId>queryengine-web</artifactId>
</dependency>
```

## 最小嵌入

```java
QueryEngine engine = QueryEngine.builder()
        .dataSource(dataSource)
        .dialect(new MySqlDialect())
        .schema("demo")
        .defaultPageSize(10)
        .maxPageSize(100)
        .queryTimeoutSeconds(5)
        .registerTables("User", "Moment", "Comment")
        .build();

JsonNode body = engine.get("""
        { "User": { "id": 38710 } }
        """);
```

`DataSource` 由宿主提供。引擎不创建连接池。一次 `get` / `head` / `post` / `put` / `delete` 取一个连接，包在一个本地事务里：成功提交，任意异常回滚。

## 自己映射 HTTP

不用 `queryengine-web` 时，库内方法仍只是枚举。宿主可以自己包一层；官方路由和错误码以 [http.md](http.md) 为准。

```java
Method method = Method.fromName(request.getMethod());
try {
    JsonNode data = engine.execute(method, request.getBody());
    return Response.ok(data);
} catch (AccessDeniedException ex) {
    return Response.status(403, ex.getMessage());
} catch (BadRequestException ex) {
    return Response.status(400, ex.getMessage());
} catch (QueryEngineException ex) {
    return Response.status(500, ex.getMessage());
}
```

异常类型：

| 类型 | 含义 |
| --- | --- |
| `BadRequestException` | JSON 不合法、标识符非法、对象查询命中多行、写操作缺少主键 |
| `AccessDeniedException` | 表没有注册，或自定义注册表拒绝了这个 method |
| `QueryExecutionException` | JDBC 失败。消息含参数化 SQL，不含绑定值 |

`queryengine-web` 使用同一组数字作为 JSON 里的 `code`，并且让 HTTP 状态与之相同。目录见 [http.md](http.md)。上面的手写映射适合不引入该模块的宿主。

## 可选的 Spring Boot 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo
    username: app
    password: secret

queryengine:
  dialect: mysql          # 或 dameng
  schema: demo
  default-page-size: 10
  max-page-size: 100      # 0 表示不夹紧 count
  max-page-index: 0       # 0 表示不夹紧 page
  query-timeout-seconds: 5
  id-column: id
  dameng-uppercase-identifiers: true
  access:
    enabled: true         # 启动时读 access 表
    table: access
```

有权限的表写在数据库 `access` 表里，不要再把每张表列进 yml。临时兜底仍可用 `tables` 和 `table-names`，见 [register-table.md](register-table.md)。进程跑起来之后：

```java
queryEngine.registerTable("Note");
```

这会马上允许查询 `Note`，并插入 `access`。宿主若自己声明了 `QueryEngine` Bean，自动配置会让开；那种情况下要自己调用 `JdbcAccessStore` 或 `builder.accessStore(...)`。

## 扩展点

- `Dialect`：标识符引用和分页语法
- `AccessRegistry`：表注册与 method 授权。默认实现的 `register` 授予全部 method
- `SqlExecutor` / `SqlSession`：替换 JDBC。参数必须走占位符

构建器上的 `accessRegistry(...)` 和 `sqlExecutor(...)` 用来换实现。
