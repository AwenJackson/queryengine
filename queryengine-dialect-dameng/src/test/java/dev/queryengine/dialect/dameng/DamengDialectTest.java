package dev.queryengine.dialect.dameng;

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

class DamengDialectTest {

    @Test
    void uppercasesCatalogIdentifiersAndPaginatesWithOffsetFetch() {
        PlannedSql planned = new SqlPlanner(new DamengDialect()).select(new SingleTableQuery(
                "demo",
                "User",
                List.of(new SelectItem("id", null), new SelectItem("name", "userName")),
                List.of(Predicate.eq("sex", 0)),
                List.of(new OrderBy("id", true)),
                10,
                20,
                List.of()
        ));
        assertEquals(
                "SELECT \"ID\" AS \"id\", \"NAME\" AS \"userName\" FROM \"DEMO\".\"USER\""
                        + " WHERE \"SEX\" = ? ORDER BY \"ID\" DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                planned.sql()
        );
        assertEquals(List.of(0, 20, 10), planned.parameters());
    }

    @Test
    void canKeepQuotedCase() {
        PlannedSql planned = new SqlPlanner(new DamengDialect(false)).select(new SingleTableQuery(
                "demo",
                "User",
                List.of(new SelectItem("userId", null)),
                List.of(new Predicate("id", Op.IN, List.of(1L, 2L))),
                List.of(),
                null,
                0,
                List.of()
        ));
        assertEquals(
                "SELECT \"userId\" AS \"userId\" FROM \"demo\".\"User\" WHERE \"id\" IN (?, ?)",
                planned.sql()
        );
        assertEquals(List.of(1L, 2L), planned.parameters());
    }
}
