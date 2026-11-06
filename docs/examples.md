# 可复制示例

下面的 JSON 是 NestQL，可以直接交给 `engine.get` / `head` / `post` / `put` / `delete`。表需要先 `registerTable`。示例数据与 `queryengine-demo` 的契约测试一致：`demo.User` 里有 Tommy（38710）、Lily（70793）、Tom（82001）。

QueryEngine 支持通过 GraphQL、RESTful、NestQL 接入。HTTP 调用见 [http.md](http.md)。库返回值没有 `code`；NestQL 的 HTTP 响应会在同一层对象上追加 `code` 与 `message`。

## 按主键读一个对象

```java
JsonNode result = engine.get("""
        { "User": { "id": 38710 } }
        """);
```

```json
{
  "User": { "id": 38710, "name": "Tommy", "sex": 0, "tag": "student" }
}
```

## 分页、列、排序

```java
JsonNode page = engine.get("""
        {
          "[]": {
            "page": 0,
            "count": 2,
            "User": { "@column": "id,name", "@order": "id+" }
          }
        }
        """);
```

## 条件

```java
engine.get("""
        {
          "[]": {
            "count": 10,
            "User": {
              "name$": "%Tom%",
              "id{}": [38710, 82001],
              "sex!": 1,
              "@column": "id,name",
              "@order": "id+"
            }
          }
        }
        """);
```

区间写法：

```json
{ "[]": { "count": 10, "User": { "id{}": ">=38710,<80000" } } }
```

## 引用赋值

键的书写顺序可以反过来，引擎会先读 `User`：

```java
engine.get("""
        {
          "Moment[]": {
            "count": 10,
            "Moment": {
              "userId@": "/User/id",
              "@column": "id,content",
              "@order": "id+"
            }
          },
          "User": { "id": 38710, "@column": "id,name" }
        }
        """);
```

嵌套数组用 `[]/` 指向当前行：

```java
engine.get("""
        {
          "[]": {
            "count": 5,
            "User": {
              "id{}": [38710, 82001],
              "@column": "id,name",
              "@order": "id+"
            },
            "Moment[]": {
              "count": 5,
              "Moment": { "userId@": "[]/User/id", "@column": "id,content", "@order": "id+" },
              "Comment[]": {
                "count": 5,
                "Comment": { "momentId@": "[]/Moment/id", "@column": "id,content", "@order": "id+" }
              }
            }
          }
        }
        """);
```

## 总数

```java
JsonNode total = engine.head("""
        { "User": { "sex": 0 } }
        """);
// total.path("User").path("count")
```

## 写入

```java
engine.registerTable("Todo");

JsonNode created = engine.post("""
        { "Todo": { "title": "买牛奶", "done": 0 } }
        """);
long id = created.path("Todo").path("id").asLong();

engine.put("""
        { "Todo": { "id": %d, "done": 1 } }
        """.formatted(id));

engine.post("""
        { "Todo[]": [ { "title": "一" }, { "title": "二" } ] }
        """);

engine.delete("""
        { "Todo": { "id": %d } }
        """.formatted(id));
```

## 切换 schema

```java
try (SchemaScope scope = engine.withSchema("app_b")) {
    JsonNode user = engine.get("""
            { "User": { "id": 1, "@column": "name" } }
            """);
}
```

## 宿主侧包装

```java
public Map<String, Object> handle(String method, String body) {
    try {
        return Map.of("ok", true, "data", engine.execute(Method.fromName(method), body));
    } catch (AccessDeniedException ex) {
        return Map.of("ok", false, "message", ex.getMessage());
    } catch (BadRequestException ex) {
        return Map.of("ok", false, "message", ex.getMessage());
    }
}
```

这层 Map 属于宿主。引擎返回的 `data` 仍然是与请求同构的业务 JSON。
