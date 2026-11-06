package dev.queryengine.demo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.queryengine.QueryEngine;
import dev.queryengine.dialect.mysql.MySqlDialect;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.exception.QueryExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedWriteTest {

    private static DemoDatabase database;
    private static QueryEngine engine;

    @BeforeAll
    static void startDatabase() {
        database = DemoDatabase.start();
        engine = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new MySqlDialect())
                .schema("demo")
                .registerTables("User", "Moment", "Comment", "Type", "Book", "Book_menu", "Book_menu_tip")
                .build();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @Test
    void shapeAInsertsTreeAndGetMatches() {
        JsonNode created = engine.post("""
                {
                  "Comment[]": {
                    "Comment": { "momentId": 1, "userId": 38710, "content": "hello" },
                    "Type[]": {
                      "Type": { "user_id": 38710, "type_name": "类型", "comment_id@": "[]/Comment/id" },
                      "Book[]": {
                        "Book": {
                          "user_id": 38710,
                          "book_name": "书",
                          "book_desc": "d",
                          "type_id@": "[]/Type/id"
                        },
                        "Book_menu[]": {
                          "Book_menu": { "user_id": 38710, "menu_name": "章", "book_id@": "[]/Book/id" },
                          "Book_menu_tip[]": {
                            "Book_menu_tip": { "tip_name": "tip", "book_menu_id@": "[]/Book_menu/id" }
                          }
                        }
                      }
                    }
                  }
                }
                """);

        JsonNode comment = at(created, "Comment[]", "0", "Comment");
        JsonNode type = at(created, "Comment[]", "0", "Type[]", "0", "Type");
        JsonNode book = at(created, "Comment[]", "0", "Type[]", "0", "Book[]", "0", "Book");
        JsonNode menu = at(created, "Comment[]", "0", "Type[]", "0", "Book[]", "0", "Book_menu[]", "0", "Book_menu");
        JsonNode tip = at(created, "Comment[]", "0", "Type[]", "0", "Book[]", "0", "Book_menu[]", "0", "Book_menu_tip[]", "0", "Book_menu_tip");
        assertEquals(1, created.get("Comment[]").size());
        assertRow(comment, "hello", 38710, "userId");
        assertEquals(comment.get("id").asLong(), type.get("comment_id").asLong());
        assertEquals("类型", type.get("type_name").asText());
        assertEquals(1, type.get("count").asInt());
        assertEquals(type.get("id").asLong(), book.get("type_id").asLong());
        assertEquals("书", book.get("book_name").asText());
        assertEquals("d", book.get("book_desc").asText());
        assertEquals(book.get("id").asLong(), menu.get("book_id").asLong());
        assertEquals("章", menu.get("menu_name").asText());
        assertEquals(menu.get("id").asLong(), tip.get("book_menu_id").asLong());
        assertEquals("tip", tip.get("tip_name").asText());
        assertTrue(comment.get("id").asLong() >= 100);

        JsonNode loaded = engine.get("""
                {
                  "Comment": { "id": %d },
                  "Type[]": {
                    "Type": { "comment_id@": "/Comment/id", "@order": "id+" },
                    "Book[]": {
                      "Book": { "type_id@": "[]/Type/id", "@order": "id+" },
                      "Book_menu[]": {
                        "Book_menu": { "book_id@": "[]/Book/id", "@order": "id+" },
                        "Book_menu_tip[]": {
                          "Book_menu_tip": { "book_menu_id@": "[]/Book_menu/id", "@order": "id+" }
                        }
                      }
                    }
                  }
                }
                """.formatted(comment.get("id").asLong()));
        JsonNode gotType = at(loaded, "Type[]", "0", "Type");
        JsonNode gotBook = at(loaded, "Type[]", "0", "Book[]", "0", "Book");
        JsonNode gotMenu = at(loaded, "Type[]", "0", "Book[]", "0", "Book_menu[]", "0", "Book_menu");
        JsonNode gotTip = at(loaded, "Type[]", "0", "Book[]", "0", "Book_menu[]", "0", "Book_menu_tip[]", "0", "Book_menu_tip");
        assertEquals("hello", loaded.get("Comment").get("content").asText());
        assertEquals(1, loaded.get("Type[]").size());
        assertEquals(type.get("id").asLong(), gotType.get("id").asLong());
        assertEquals("类型", gotType.get("type_name").asText());
        assertEquals(comment.get("id").asLong(), gotType.get("comment_id").asLong());
        assertEquals(1, at(loaded, "Type[]", "0", "Book[]").size());
        assertEquals("书", gotBook.get("book_name").asText());
        assertEquals("d", gotBook.get("book_desc").asText());
        assertEquals(gotType.get("id").asLong(), gotBook.get("type_id").asLong());
        assertEquals("章", gotMenu.get("menu_name").asText());
        assertEquals(gotBook.get("id").asLong(), gotMenu.get("book_id").asLong());
        assertEquals("tip", gotTip.get("tip_name").asText());
        assertEquals(gotMenu.get("id").asLong(), gotTip.get("book_menu_id").asLong());
    }

    @Test
    void shapeBFillsForeignKeysInsideParent() {
        JsonNode created = engine.post("""
                {
                  "Comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "hello",
                    "Type[]": [
                      {
                        "user_id": 38710,
                        "type_name": "类型",
                        "Book[]": [
                          {
                            "user_id": 38710,
                            "book_name": "书",
                            "book_desc": "d",
                            "Book_menu[]": [
                              {
                                "user_id": 38710,
                                "menu_name": "章",
                                "Book_menu_tip[]": [ { "tip_name": "tip" } ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """);

        JsonNode comment = created.get("Comment");
        JsonNode type = at(comment, "Type[]", "0");
        JsonNode book = at(type, "Book[]", "0");
        JsonNode menu = at(book, "Book_menu[]", "0");
        JsonNode tip = at(menu, "Book_menu_tip[]", "0");
        assertFalse(type.has("Type"));
        assertRow(comment, "hello", 38710, "userId");
        assertEquals(comment.get("id").asLong(), type.get("comment_id").asLong());
        assertEquals(38710L, type.get("user_id").asLong());
        assertEquals("类型", type.get("type_name").asText());
        assertEquals(1, type.get("count").asInt());
        assertEquals(type.get("id").asLong(), book.get("type_id").asLong());
        assertEquals("书", book.get("book_name").asText());
        assertEquals("d", book.get("book_desc").asText());
        assertEquals(book.get("id").asLong(), menu.get("book_id").asLong());
        assertEquals("章", menu.get("menu_name").asText());
        assertEquals(menu.get("id").asLong(), tip.get("book_menu_id").asLong());
        assertEquals("tip", tip.get("tip_name").asText());
        assertTrue(tip.get("id").asLong() >= 100);

        JsonNode loaded = engine.get("""
                {
                  "Comment": { "id": %d },
                  "Type[]": {
                    "Type": { "comment_id@": "/Comment/id", "@order": "id+" },
                    "Book[]": {
                      "Book": { "type_id@": "[]/Type/id", "@order": "id+" },
                      "Book_menu[]": {
                        "Book_menu": { "book_id@": "[]/Book/id", "@order": "id+" },
                        "Book_menu_tip[]": {
                          "Book_menu_tip": { "book_menu_id@": "[]/Book_menu/id", "@order": "id+" }
                        }
                      }
                    }
                  }
                }
                """.formatted(comment.get("id").asLong()));
        assertEquals(1, loaded.get("Type[]").size());
        assertEquals("类型", at(loaded, "Type[]", "0", "Type", "type_name").asText());
        assertEquals("书", at(loaded, "Type[]", "0", "Book[]", "0", "Book", "book_name").asText());
        assertEquals("章", at(loaded, "Type[]", "0", "Book[]", "0", "Book_menu[]", "0", "Book_menu", "menu_name").asText());
        assertEquals("tip", at(loaded, "Type[]", "0", "Book[]", "0", "Book_menu[]", "0", "Book_menu_tip[]", "0", "Book_menu_tip", "tip_name").asText());
        assertEquals(comment.get("id").asLong(), at(loaded, "Type[]", "0", "Type", "comment_id").asLong());
    }

    @Test
    void batchInsertsChildrenForEachParentRow() {
        JsonNode created = engine.post("""
                {
                  "Comment[]": [
                    {
                      "momentId": 1,
                      "userId": 38710,
                      "content": "row-a",
                      "Type[]": [
                        { "user_id": 38710, "type_name": "A", "comment_id@": "[]/Comment/id" }
                      ]
                    },
                    {
                      "momentId": 1,
                      "userId": 38710,
                      "content": "row-b",
                      "Type[]": [ { "user_id": 38710, "type_name": "B" } ]
                    }
                  ]
                }
                """);
        long first = at(created, "Comment[]", "0", "id").asLong();
        long second = at(created, "Comment[]", "1", "id").asLong();
        assertEquals(first, at(created, "Comment[]", "0", "Type[]", "0", "comment_id").asLong());
        assertEquals(second, at(created, "Comment[]", "1", "Type[]", "0", "comment_id").asLong());
        assertEquals("A", at(created, "Comment[]", "0", "Type[]", "0", "type_name").asText());
        assertEquals("B", at(created, "Comment[]", "1", "Type[]", "0", "type_name").asText());

        JsonNode loadedA = engine.get("{ \"Type\": { \"comment_id\": %d, \"type_name\": \"A\" } }".formatted(first));
        JsonNode loadedB = engine.get("{ \"Type\": { \"comment_id\": %d, \"type_name\": \"B\" } }".formatted(second));
        assertEquals(first, loadedA.get("Type").get("comment_id").asLong());
        assertEquals(second, loadedB.get("Type").get("comment_id").asLong());
    }

    @Test
    void absoluteRootReferenceResolvesDuringNestedInsert() {
        JsonNode created = engine.post("""
                {
                  "Comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "abs-ref",
                    "Type[]": [
                      { "user_id": 38710, "type_name": "绝对路径", "comment_id@": "/Comment/id" }
                    ]
                  }
                }
                """);
        long commentId = created.get("Comment").get("id").asLong();
        assertEquals(commentId, at(created, "Comment", "Type[]", "0", "comment_id").asLong());
        JsonNode loaded = engine.get("{ \"Type\": { \"comment_id\": %d, \"type_name\": \"绝对路径\" } }".formatted(commentId));
        assertEquals(commentId, loaded.get("Type").get("comment_id").asLong());
    }

    @Test
    void explicitForeignKeyIsNotReplaced() {
        JsonNode created = engine.post("""
                {
                  "Comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "explicit-fk",
                    "Type[]": [
                      { "user_id": 38710, "type_name": "旧评论", "comment_id": 13 },
                      { "user_id": 38710, "type_name": "空外键", "comment_id": null }
                    ]
                  }
                }
                """);
        long commentId = created.get("Comment").get("id").asLong();
        JsonNode kept = at(created, "Comment", "Type[]", "0");
        JsonNode cleared = at(created, "Comment", "Type[]", "1");
        assertEquals(13L, kept.get("comment_id").asLong());
        assertTrue(cleared.get("comment_id").isNull());
        assertTrue(commentId > 13L);

        JsonNode loadedKept = engine.get("{ \"Type\": { \"id\": %d } }".formatted(kept.get("id").asLong()));
        JsonNode loadedCleared = engine.get("{ \"Type\": { \"id\": %d } }".formatted(cleared.get("id").asLong()));
        assertEquals(13L, loadedKept.get("Type").get("comment_id").asLong());
        assertTrue(loadedCleared.get("Type").get("comment_id").isNull());
    }

    @Test
    void ambiguousAndIllegalNestsAreRejected() {
        BadRequestException ambiguous = assertThrows(BadRequestException.class, () -> engine.post("""
                {
                  "Comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "ambiguous-fk",
                    "Type[]": [ { "id": 4242 } ]
                  }
                }
                """));
        assertTrue(ambiguous.getMessage().contains("comment_id"));
        assertTrue(ambiguous.getMessage().contains("commentId"));
        assertEquals(0, countComment("ambiguous-fk"));
        assertTrue(engine.get("{ \"Type\": { \"id\": 4242 } }").get("Type").isNull());

        BadRequestException parents = assertThrows(BadRequestException.class, () -> engine.post("""
                {
                  "Comment[]": {
                    "Comment": { "momentId": 1, "userId": 38710, "content": "multi-parent" },
                    "Moment": { "id": 900, "userId": 38710, "content": "extra-moment" },
                    "Type[]": [ { "user_id": 38710, "type_name": "多父" } ]
                  }
                }
                """));
        assertTrue(parents.getMessage().contains("多个父表"));
        assertEquals(0, countComment("multi-parent"));
        assertTrue(engine.get("{ \"Moment\": { \"id\": 900 } }").get("Moment").isNull());

        BadRequestException missing = assertThrows(BadRequestException.class, () -> engine.post("""
                { "Comment[]": { "Type[]": { "Type": { "user_id": 38710, "type_name": "无主表" } } } }
                """));
        assertTrue(missing.getMessage().contains("缺少主表"));

        BadRequestException page = assertThrows(BadRequestException.class, () -> engine.post("""
                { "Comment[]": { "page": 0, "Comment": { "momentId": 1, "userId": 38710, "content": "paged" } } }
                """));
        assertTrue(page.getMessage().contains("不接受 page"));
        assertEquals(0, countComment("paged"));

        BadRequestException scalar = assertThrows(BadRequestException.class, () -> engine.post("""
                { "Comment": { "momentId": 1, "userId": 38710, "content": "scalar-nest", "Type[]": 1 } }
                """));
        assertTrue(scalar.getMessage().contains("必须是数组或对象"));
        assertEquals(0, countComment("scalar-nest"));

        BadRequestException putTree = assertThrows(BadRequestException.class, () -> engine.put("""
                { "Comment[]": { "Comment": { "id": 13, "content": "no-put" } } }
                """));
        assertTrue(putTree.getMessage().contains("写操作不接受查询形态"));
        BadRequestException deleteTree = assertThrows(BadRequestException.class, () -> engine.delete("""
                { "Comment[]": { "Comment": { "id": 13 } } }
                """));
        assertTrue(deleteTree.getMessage().contains("写操作不接受查询形态"));
        BadRequestException putEmbed = assertThrows(BadRequestException.class, () -> engine.put("""
                { "Comment": { "id": 13, "content": "changed", "Type[]": [] } }
                """));
        assertTrue(putEmbed.getMessage().contains("只有 POST 支持嵌套写入"));
        assertEquals("aoisa", engine.get("{ \"Comment\": { \"id\": 13 } }").get("Comment").get("content").asText());

        BadRequestException getBatch = assertThrows(BadRequestException.class, () -> engine.get("""
                { "Comment[]": [ { "content": "x" } ] }
                """));
        assertTrue(getBatch.getMessage().contains("必须是对象"));
    }

    @Test
    void camelCaseNameIsNotGuessedAsSnake() {
        QueryExecutionException ex = assertThrows(QueryExecutionException.class, () -> engine.post("""
                {
                  "Comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "camel-fk",
                    "Type[]": [ { "userId": 38710, "typeName": "camel" } ]
                  }
                }
                """));
        assertTrue(ex.getMessage().contains("commentId"));
        assertEquals(0, countComment("camel-fk"));
    }

    @Test
    void midTreeFailureRollsBack() {
        assertThrows(QueryExecutionException.class, () -> engine.post("""
                {
                  "Comment[]": {
                    "Comment": { "momentId": 1, "userId": 38710, "content": "rollback-tree" },
                    "Type[]": {
                      "Type": { "user_id": 38710, "type_name": "回滚类型", "comment_id@": "[]/Comment/id" },
                      "Book[]": {
                        "Book": {
                          "user_id": 38710,
                          "book_name": "回滚书",
                          "book_desc": "d",
                          "type_id@": "[]/Type/id"
                        },
                        "Book_menu[]": {
                          "Book_menu": { "user_id": 38710, "menu_name": "回滚章", "book_id@": "[]/Book/id" },
                          "Book_menu_tip[]": {
                            "Book_menu_tip": {
                              "tip_name": "tip",
                              "book_menu_id@": "[]/Book_menu/id",
                              "nope": 1
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """));
        assertEquals(0, countComment("rollback-tree"));
        assertEquals(0, engine.head("{ \"Type\": { \"type_name\": \"回滚类型\" } }").path("Type").path("count").asInt());
        assertEquals(0, engine.head("{ \"Book\": { \"book_name\": \"回滚书\" } }").path("Book").path("count").asInt());
        assertEquals(0, engine.head("{ \"Book_menu\": { \"menu_name\": \"回滚章\" } }").path("Book_menu").path("count").asInt());
    }

    @Test
    void unregisteredChildRollsBack() {
        QueryEngine commentsOnly = QueryEngine.builder()
                .dataSource(database.dataSource())
                .dialect(new MySqlDialect())
                .schema("demo")
                .registerTable("Comment")
                .build();
        assertThrows(AccessDeniedException.class, () -> commentsOnly.post("""
                {
                  "Comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "denied-tree",
                    "Type[]": [ { "user_id": 38710, "type_name": "未登记" } ]
                  }
                }
                """));
        assertEquals(0, commentsOnly.head("{ \"Comment\": { \"content\": \"denied-tree\" } }").path("Comment").path("count").asInt());
    }

    @Test
    void flatRootReferenceAndCaseStillWork() {
        JsonNode created = engine.post("""
                {
                  "Comment": { "momentId": 1, "userId": 38710, "content": "root-sib" },
                  "Type": { "user_id": 38710, "type_name": "平级", "comment_id@": "/Comment/id" }
                }
                """);
        long commentId = created.get("Comment").get("id").asLong();
        assertEquals(commentId, created.get("Type").get("comment_id").asLong());
        assertEquals(1, created.get("Type").get("count").asInt());

        JsonNode cased = engine.post("""
                {
                  "comment": {
                    "momentId": 1,
                    "userId": 38710,
                    "content": "case-row",
                    "type[]": [ { "user_id": 38710, "type_name": "大小写" } ]
                  }
                }
                """);
        long casedId = cased.get("comment").get("id").asLong();
        assertEquals(casedId, at(cased, "comment", "type[]", "0", "comment_id").asLong());
        JsonNode loaded = engine.get("{ \"Type\": { \"comment_id\": %d, \"type_name\": \"大小写\" } }".formatted(casedId));
        assertNotNull(loaded.get("Type"));
        assertEquals("大小写", loaded.get("Type").get("type_name").asText());
        assertNull(loaded.get("code"));
    }

    private static int countComment(String content) {
        return engine.head("""
                { "Comment": { "content": "%s" } }
                """.formatted(content)).path("Comment").path("count").asInt();
    }

    private static void assertRow(JsonNode row, String content, long userId, String userColumn) {
        assertEquals(content, row.get("content").asText());
        assertEquals(userId, row.get(userColumn).asLong());
        assertEquals(1L, row.get("momentId").asLong());
        assertEquals(1, row.get("count").asInt());
        assertTrue(row.get("id").asLong() > 0);
    }

    private static JsonNode at(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            assertNotNull(current);
            current = key.chars().allMatch(Character::isDigit) ? current.get(Integer.parseInt(key)) : current.get(key);
        }
        assertNotNull(current);
        return current;
    }
}
