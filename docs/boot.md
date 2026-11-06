# 马上启动

`queryengine-boot` 是一个带 `main` 的 Spring Boot 进程，依赖 `queryengine-web` 和 MySQL 方言。QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。默认激活 `mysql` profile（见 `application.yml`），数据源在 `application-mysql.yml`，启动前先导入 `scripts/schema-mysql.sql`。去掉该 profile 后，主配置里的内存 H2 会执行 `classpath:schema.sql`。有权限的表来自各 schema 自己的 `access` 表，不是 `application.yml` 里的列表，也不是启动时一次性装进内存。第一次请求某个 schema 才加载并缓存。`demo.access` 含 `Book_menu_tip`；`demo1.access` 只有 User、Moment、Comment、Book、Book_menu、Type，没有 tip。请求键大小写不敏感。不需要登录。

新表不必改 yml。建好物理表后，在进程里调用 `queryEngine.registerTable("Note")`，或直接打 HTTP：

```bash
curl -s -X POST http://127.0.0.1:8080/access/register \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Note", "alias": "note" }'
```

当前请求马上可以查 `Note`，这一行会写入 `access`，重启后仍然在。停用：

```bash
curl -s -X POST http://127.0.0.1:8080/access/unregister \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Note" }'
```

```bash
mvn -pl queryengine-boot -am spring-boot:run
```

监听 `http://127.0.0.1:8080`。

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

上面是 NestQL。`POST /nestql/get` 与 `POST /get` 相同。同一条数据的另外两种入口：

```bash
curl -s http://127.0.0.1:8080/rest/User/38710
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{ "query": "{ User(id: 38710) { id name tag } }" }'
```

GraphQL 的列参数只有自定义标量 `Long` 和 `String`，没有内置 `ID`、`Int`、`Float`、`Boolean`。变量里的主键写成 `$uid: Long!`，不要写成 `ID!` 或 `Int!`。详见 [graphql.md](graphql.md)。

细节见 [http.md](http.md)、[rest.md](rest.md)、[graphql.md](graphql.md)。

种子数据与 `scripts/schema-mysql.sql` 一致：Tommy `38710`、Lily `70793`、Tom `82001`，Moment / Comment（含 id=13 的 `aoisa`），以及 book、book_menu、book_menu_tip、type。`book_menu_tip` 挂在 Tommy 的章节上：id=1 对应 `book_menu_id=1`（章节一提示），id=2 对应 `book_menu_id=2`（章节二提示）。

一次取出 38710 的用户和相关行。`User` 写成 `user` 或 `USER` 同样命中小写物理表 `user`；`Book` / `book` / `BOOK` 同样命中 `book`。

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{
    "User": { "id": 38710 },
    "Moment[]": { "Moment": { "userId": 38710 } },
    "Comment[]": { "Comment": { "userId": 38710 } },
    "Book[]": { "Book": { "user_id": 38710 } },
    "Book_menu[]": { "Book_menu": { "user_id": 38710 } },
    "Type[]": { "Type": { "user_id": 38710 } }
  }'
```

期望：`code` 为 200，`User.name` 为 Tommy，Moment 为 hello 与 world，Comment 为 aoisa，Book 为安徒生童话，Book_menu 为章节一和章节二，Type 为类型名称和新增。

切到 `demo1` 后 tip 不在那份名单里，返回 403：

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -H 'X-QueryEngine-Schema: demo1' \
  -d '{ "Book_menu_tip": { "id": 1 } }'
```

```bash
curl -s -X POST http://127.0.0.1:8080/access/list \
  -H 'X-QueryEngine-Schema: demo1'
```

## 本机 MySQL

导入（会创建数据库 `demo`，并重建业务表和 `access` 授权表）：

```bash
mysql -uroot -p < scripts/schema-mysql.sql
```

启动：

```bash
mvn -pl queryengine-boot -am spring-boot:run -Dspring-boot.run.profiles=mysql
```

`application-mysql.yml` 连接 `jdbc:mysql://127.0.0.1:3306/demo`，用户默认 `root` / `root`。覆盖方式：

```bash
MYSQL_USER=app MYSQL_PASSWORD=secret mvn -pl queryengine-boot -am spring-boot:run -Dspring-boot.run.profiles=mysql
```

这个 profile 不会再跑 H2 的初始化脚本。表结构以 `scripts/schema-mysql.sql` 为准。

## 达梦

脚本：`scripts/schema-dameng.sql`。用 disql 或达梦管理工具执行。模式是 `"DEMO"`，表和列都是大写双引号（`"USER"."ID"`），种子行与 MySQL 脚本相同。

不要把 `schema-mysql.sql` 拿到达梦上执行。差别：

| | MySQL / 默认 H2 | 达梦脚本 |
| --- | --- | --- |
| 库 | `CREATE DATABASE demo`，反引号 `` `User` `` | `CREATE SCHEMA "DEMO"`，双引号大写 `"USER"` |
| 引擎方言 | `queryengine.dialect=mysql`，`schema: demo` | `dialect: dameng`，`schema: DEMO`，标识符默认转大写 |
| 字符集 / 引擎子句 | `utf8mb4`、`ENGINE=InnoDB` | 没有这些子句 |
| 驱动 | 启动模块已带 MySQL 驱动；H2 仅默认 profile | 不打包达梦驱动，需自行加入 `com.dameng:DmJdbcDriver18` |

驱动加进 `queryengine-boot` 后再启动：

```bash
mvn -pl queryengine-boot -am spring-boot:run -Dspring-boot.run.profiles=dameng
```

`application-dameng.yml` 默认连接 `jdbc:dm://127.0.0.1:5236`，用户 `SYSDBA` / `SYSDBA`，可用 `DAMENG_USER`、`DAMENG_PASSWORD` 覆盖。若 `"DEMO"` 已经存在，跳过脚本里的 `CREATE SCHEMA` 再执行后面的 `DROP` / `CREATE`。

若建表时用双引号保留了驼峰名（`"User"` 而不是 `"USER"`），把 `queryengine.dameng-uppercase-identifiers` 设为 `false`，否则 SQL 会去找 `"USER"`。
