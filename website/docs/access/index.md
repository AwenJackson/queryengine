# 注册与 access

每张逻辑表先登记，查询才会放行。`registerTable` 授予全部 method。没有角色，也没有会话。

access 表按「表所在 schema」分开放。行里的 `schema_name` 只表示查询时钉死的 schema，不表示这条记录属于哪份名单。`schema_name` 为空也不会变成全局授权。

名单在第一次查询、登记或列出该 schema 时加载并缓存。启动时不预加载。`reloadAccess(schema)` 丢掉这份缓存再读库。

```bash
curl -s -X POST http://127.0.0.1:8080/access/register \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Note", "alias": "note", "schema": "demo" }'
```

```bash
curl -s -X POST http://127.0.0.1:8080/access/unregister \
  -H 'Content-Type: application/json' \
  -d '{ "name": "Book_menu_tip", "schema": "demo1" }'
```

注销之后，带 `X-QueryEngine-Schema: demo1` 的 `POST /get` 查 `Book_menu_tip` 必须是 403。`demo` 里仍启用的同一张表不受影响。`POST /access/list` 和查询授权看的是同一份当前 schema 缓存。

表名大小写不敏感。`User`、`user`、`USER` 只登记一次。
