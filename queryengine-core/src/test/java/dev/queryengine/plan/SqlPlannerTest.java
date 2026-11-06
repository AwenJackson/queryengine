package dev.queryengine.plan;

import dev.queryengine.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlPlannerTest {

    private final SqlPlanner planner = new SqlPlanner(new QuoteDialect());

    @Test
    void selectKeepsValuesOutOfSql() {
        SingleTableQuery query = new SingleTableQuery(
                "demo",
                "User",
                List.of(new SelectItem("id", null), new SelectItem("name", "userName")),
                List.of(
                        Predicate.eq("sex", 0),
                        new Predicate("id", Op.IN, List.of(1L, 2L)),
                        new Predicate("name", Op.LIKE, List.of("%a%", "%b%")),
                        new Predicate("tag", Op.IS_NULL, List.of()),
                        new Predicate("removed", Op.NOT_IN, List.of())
                ),
                List.of(new OrderBy("id", true), new OrderBy("name", false)),
                10,
                20,
                List.of()
        );

        PlannedSql planned = planner.select(query);

        assertEquals(
                "SELECT \"id\" AS \"id\", \"name\" AS \"userName\" FROM \"demo\".\"User\""
                        + " WHERE \"sex\" = ? AND \"id\" IN (?, ?) AND (\"name\" LIKE ? OR \"name\" LIKE ?)"
                        + " AND \"tag\" IS NULL AND 1 = 1"
                        + " ORDER BY \"id\" DESC, \"name\" ASC LIMIT ? OFFSET ?",
                planned.sql()
        );
        assertEquals(List.of(0, 1L, 2L, "%a%", "%b%", 10, 20), planned.parameters());
        assertEquals(countMarks(planned.sql()), planned.parameters().size());
        assertFalse(planned.sql().contains("%a%"));
    }

    @Test
    void literalPayloadIsBound() {
        PlannedSql planned = planner.select(new SingleTableQuery(
                null,
                "User",
                List.of(),
                List.of(Predicate.eq("name", "a' OR '1'='1")),
                List.of(),
                null,
                0,
                List.of()
        ));

        assertEquals("SELECT * FROM \"User\" WHERE \"name\" = ?", planned.sql());
        assertEquals(List.of("a' OR '1'='1"), planned.parameters());
        assertFalse(planned.sql().contains("OR"));
    }

    @Test
    void emptyInMatchesNothing() {
        PlannedSql planned = planner.select(new SingleTableQuery(
                null,
                "User",
                List.of(),
                List.of(new Predicate("id", Op.IN, List.of())),
                List.of(),
                null,
                0,
                List.of()
        ));
        assertEquals("SELECT * FROM \"User\" WHERE 1 = 0", planned.sql());
        assertEquals(List.of(), planned.parameters());
    }

    @Test
    void countInsertUpdateDelete() {
        SingleTableQuery counted = new SingleTableQuery(
                "demo", "User", List.of(), List.of(Predicate.eq("sex", 0)), List.of(), null, 0, List.of()
        );
        assertEquals(
                "SELECT COUNT(*) AS \"cnt\" FROM \"demo\".\"User\" WHERE \"sex\" = ?",
                planner.count(counted).sql()
        );

        PlannedSql insert = planner.insert(new SingleTableQuery(
                "demo",
                "Todo",
                List.of(),
                List.of(),
                List.of(),
                null,
                0,
                List.of(new Assignment("title", "买牛奶"), new Assignment("done", null))
        ));
        assertEquals("INSERT INTO \"demo\".\"Todo\" (\"title\", \"done\") VALUES (?, ?)", insert.sql());
        assertEquals(Arrays.asList("买牛奶", null), insert.parameters());

        PlannedSql update = planner.update(new SingleTableQuery(
                null,
                "Todo",
                List.of(),
                List.of(Predicate.eq("id", 7L)),
                List.of(),
                null,
                0,
                List.of(new Assignment("title", "改"))
        ));
        assertEquals("UPDATE \"Todo\" SET \"title\" = ? WHERE \"id\" = ?", update.sql());
        assertEquals(List.of("改", 7L), update.parameters());

        PlannedSql delete = planner.delete(new SingleTableQuery(
                null,
                "Todo",
                List.of(),
                List.of(new Predicate("id", Op.IN, List.of(1L, 2L))),
                List.of(),
                null,
                0,
                List.of()
        ));
        assertEquals("DELETE FROM \"Todo\" WHERE \"id\" IN (?, ?)", delete.sql());
    }

    @Test
    void updateAndDeleteRequirePredicates() {
        SingleTableQuery empty = new SingleTableQuery(
                null, "Todo", List.of(), List.of(), List.of(), null, 0, List.of(new Assignment("title", "x"))
        );
        assertThrows(BadRequestException.class, () -> planner.update(empty));
        assertThrows(BadRequestException.class, () -> planner.delete(new SingleTableQuery(
                null, "Todo", List.of(), List.of(), List.of(), null, 0, List.of()
        )));
    }

    private static int countMarks(String sql) {
        int marks = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                marks++;
            }
        }
        return marks;
    }
}
