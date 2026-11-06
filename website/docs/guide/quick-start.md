# 快速开始

默认激活 `mysql` profile。先导入脚本，再启动：

```bash
mysql -uroot -p < scripts/schema-mysql.sql
mvn -pl queryengine-boot -am spring-boot:run
```

进程监听 `http://127.0.0.1:8080`。账号默认 `root` / `root`，用 `MYSQL_USER`、`MYSQL_PASSWORD` 覆盖。

## NestQL

```bash
curl -s -X POST http://127.0.0.1:8080/get \
  -H 'Content-Type: application/json' \
  -d '{ "User": { "id": 38710 } }'
```

`/nestql/get` 与 `/get` 相同。成功时同一层有 `code: 200` 和 `message: success`，以及用户 Tommy。

## RESTful

```bash
curl -s http://127.0.0.1:8080/rest/User/38710
```

## GraphQL

```bash
curl -s -X POST http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"query ($uid: Long!) { User(id: $uid) { id name } }","variables":{"uid":38710}}'
```

列参数只有自定义标量 `Long` 和 `String`。没有内置 `ID`、`Int`、`Float`、`Boolean`。`ID!` 会得到 `Unknown type 'ID'`，`Int!` 会得到 `VariableTypeMismatch`。

不连 MySQL 时，去掉 `spring.profiles.active`，主配置里的内存 H2 会执行 `classpath:schema.sql`。
