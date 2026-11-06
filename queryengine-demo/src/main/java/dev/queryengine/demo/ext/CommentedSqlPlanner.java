package dev.queryengine.demo.ext;

import dev.queryengine.plan.PlannedSql;
import dev.queryengine.plan.SingleTableQuery;
import dev.queryengine.plan.SqlPlanner;
import dev.queryengine.spi.Dialect;

/**
 * 继承 {@link SqlPlanner}，在 SELECT 前加注释。参数列表保持不变。
 */
public class CommentedSqlPlanner extends SqlPlanner {

    public CommentedSqlPlanner(Dialect dialect) {
        super(dialect);
    }

    @Override
    public PlannedSql select(SingleTableQuery query) {
        PlannedSql planned = super.select(query);
        return new PlannedSql("/* ext */ " + planned.sql(), planned.parameters());
    }
}
