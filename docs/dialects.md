# MySQL 与达梦

两个方言都只生成带 `?` 占位符的 SQL。值通过 `PreparedStatement.setObject` 绑定。

| | MySQL | 达梦（默认） |
| --- | --- | --- |
| 类 | `dev.queryengine.dialect.mysql.MySqlDialect` | `dev.queryengine.dialect.dameng.DamengDialect` |
| 标识符 | 表名先折成小写再加反引号，`` `user` ``；列名保持原样 | 表名和列名都先转大写再加双引号，`"USER"` |
| 结果别名 | 与请求一致，反引号 | 与请求一致，双引号，不转大写 |
| 分页 | `LIMIT ? OFFSET ?` | `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY` |
| 模式前缀 | `` `demo`.`User` `` | `"DEMO"."USER"` |

达梦关闭大写折叠：

```java
new DamengDialect(false)
```

这样会生成 `"demo"."User"`。只有建表时用双引号保留了大小写，才应该关掉它。未加引号创建的达梦对象会被数据库折成大写，默认方言与这个规则对齐。

`SELECT *` 的 JSON 字段名来自驱动返回的列标签。显式 `@column` 会写成 `列 AS 请求中的名字`，因此即使达梦把物理列折成大写，响应键仍然是请求里的 `id`、`userName`。

分页在达梦上建议带 `@order`。没有 `ORDER BY` 时 `OFFSET/FETCH` 的行序不稳定。

## MySQL 驱动

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

版本由宿主的 Spring Boot BOM 或自己的依赖管理决定。引擎本身不传递 MySQL 驱动。

## 达梦驱动

方言模块为了能在没有达梦的环境里编译，**不依赖** JDBC 驱动。宿主自己引入：

```xml
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriver18</artifactId>
    <version>8.1.3.140</version>
</dependency>
```

`DmJdbcDriver18` 在 Maven Central 上发布。若解析不到，使用达梦安装目录里的 `DmJdbcDriver18.jar`，驱动类名是 `dm.jdbc.driver.DmDriver`。

连接串示例：

```text
jdbc:dm://127.0.0.1:5236
```

默认管理员常为 `SYSDBA`。URL、用户、密码都由宿主的 `DataSource` 持有，不要写进引擎。

Spring Boot：

```yaml
spring:
  datasource:
    url: jdbc:dm://127.0.0.1:5236
    username: SYSDBA
    password: SYSDBA
    driver-class-name: dm.jdbc.driver.DmDriver

queryengine:
  dialect: dameng
  dameng-uppercase-identifiers: true
  tables:
    - QE_IT_ITEM
```

## 测试怎么跑

`mvn test` 不需要 MySQL 或达梦：

- 方言单元测试只断言 SQL 字符串和参数列表
- 契约测试把 `MySqlDialect` 打到 **H2 的 MySQL 模式**（反引号、`LIMIT/OFFSET` 都能执行）
- `MySqlContainerTest` 发现本机没有 `/var/run/docker.sock` 时跳过

有 Docker 时：

```bash
mvn -pl queryengine-demo -Dtest=MySqlContainerTest test
```

它会拉起 `mysql:8.4`，执行一条按主键读取 `User` 的请求。

有达梦服务器时，把驱动加到 `queryengine-dialect-dameng` 的 test 依赖，然后：

```bash
export DAMENG_JDBC_URL=jdbc:dm://127.0.0.1:5236
export DAMENG_JDBC_USER=SYSDBA
export DAMENG_JDBC_PASSWORD=SYSDBA
mvn -pl queryengine-dialect-dameng -Dtest=DamengIntegrationTest test
```

没有 `DAMENG_JDBC_URL` 时该测试跳过。设置了 URL 就会加载 `dm.jdbc.driver.DmDriver`，创建 `QE_IT_ITEM`，跑通 POST / GET / PUT / DELETE，最后删表。自增键不是这个集成测试的前提：插入时显式写 `id`。若驱动的 `getGeneratedKeys` 对某类身份列返回空，不带 `id` 的 POST 响应里就不会出现生成的主键，需要在表上使用驱动支持的身份列，或由请求自己提供 `id`。

## 已知差异

- 达梦默认响应若走 `SELECT *`，列名可能是大写。要稳定的 JSON 键，请写 `@column`，或关闭 `uppercaseIdentifiers` 并用引号建表。
- 两端的分页占位符顺序不同：MySQL 先 limit 再 offset，达梦先 offset 再 limit。调用方只写 `page` / `count`，不直接接触这个顺序。
- 引擎不做多数据源事务。MySQL 与达梦不能出现在同一次 `execute` 里。
