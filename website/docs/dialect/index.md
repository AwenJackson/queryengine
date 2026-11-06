# MySQL 与达梦

两种方言都只生成参数化 SQL。表名大小写不敏感，列名保持请求原文。

| | MySQL | 达梦 |
| --- | --- | --- |
| 构件 | `queryengine-dialect-mysql` | `queryengine-dialect-dameng` |
| 引用 | 反引号 | 双引号 |
| 表名 | `canonicalTable` 折成小写 | `quote` 默认折成大写 |
| 分页 | `LIMIT ? OFFSET ?` | `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY` |
| 驱动 | `com.mysql:mysql-connector-j` | 宿主自行加入 `com.dameng:DmJdbcDriver18`，本仓库不捆绑 |

MySQL 脚本是 `scripts/schema-mysql.sql`。达梦脚本是 `scripts/schema-dameng.sql`，标识符为大写，不能把 MySQL 脚本直接拿去执行。

```yaml
queryengine:
  dialect: mysql
  schema: demo
```

```yaml
queryengine:
  dialect: dameng
  schema: DEMO
  dameng-uppercase-identifiers: true
```

实现 `Dialect` 并声明成 `@Bean` 时，自动配置用这个 Bean，不再按 `queryengine.dialect` 新建。
