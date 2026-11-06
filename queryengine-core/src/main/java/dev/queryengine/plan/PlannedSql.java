package dev.queryengine.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PlannedSql(String sql, List<Object> parameters) {

    public PlannedSql {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }
}
