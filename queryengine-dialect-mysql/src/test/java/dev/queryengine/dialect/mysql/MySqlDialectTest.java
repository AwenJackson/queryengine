package dev.queryengine.dialect.mysql;

import dev.queryengine.plan.Assignment;
import dev.queryengine.plan.Op;
import dev.queryengine.plan.OrderBy;
import dev.queryengine.plan.PlannedSql;
import dev.queryengine.plan.Predicate;
import dev.queryengine.plan.SelectItem;
import dev.queryengine.plan.SingleTableQuery;
import dev.queryengine.plan.SqlPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MySqlDialectTest {

    private final SqlPlanner planner = new SqlPlanner(new MySqlDialect());

    @Test
    void quotesWithBackticksAndUsesLimit() {
        PlannedSql planned = planner.select(new SingleTableQuery(
                "demo",
                "User",
                List.of(new SelectItem("id", null), new SelectItem("name", "userName")),
                List.of(Predicate.eq("sex", 0), new Predicate("name", Op.LIKE, List.of("%Tom%"))),
                List.of(new OrderBy("id", true)),
                10,
                20,
                List.of()
        ));
        assertEquals(
                "SELECT `id` AS `id`, `name` AS `userName` FROM `demo`.`user`"
                        + " WHERE `sex` = ? AND `name` LIKE ? ORDER BY `id` DESC LIMIT ? OFFSET ?",
                planned.sql()
        );
        assertEquals(List.of(0, "%Tom%", 10, 20), planned.parameters());
        assertFalse(planned.sql().contains("%Tom%"));
        PlannedSql folded = planner.select(new SingleTableQuery(
                "DEMO", "USER", List.of(), List.of(), List.of(), null, 0, List.of()
        ));
        assertEquals("SELECT * FROM `DEMO`.`user`", folded.sql());
    }

    @Test
    void writesAreParameterized() {
        PlannedSql insert = planner.insert(new SingleTableQuery(
                "demo", "Todo", List.of(), List.of(), List.of(), null, 0,
                List.of(new Assignment("title", "a"))
        ));
        assertEquals("INSERT INTO `demo`.`todo` (`title`) VALUES (?)", insert.sql());
        assertEquals(List.of("a"), insert.parameters());
    }
}
