package dev.queryengine.plan;

import dev.queryengine.exception.BadRequestException;

/**
 * page / count 的软限制。超出上限时夹紧，不拒绝嵌套深度、对象个数或 SQL 条数。
 * {@code maxPageSize == 0} 或 {@code maxPageIndex == 0} 表示该项不限制。
 */
public record PagePolicy(int defaultPageSize, int maxPageSize, int maxPageIndex) {

    public PagePolicy {
        if (defaultPageSize <= 0) {
            throw new IllegalArgumentException("defaultPageSize 必须为正数");
        }
        if (maxPageSize < 0) {
            throw new IllegalArgumentException("maxPageSize 不能为负，0 表示不限制");
        }
        if (maxPageIndex < 0) {
            throw new IllegalArgumentException("maxPageIndex 不能为负，0 表示不限制");
        }
    }

    public int count(Integer requested) {
        int count = requested == null ? defaultPageSize : requested;
        if (count <= 0) {
            throw new BadRequestException("count 必须为正整数");
        }
        if (maxPageSize > 0 && count > maxPageSize) {
            return maxPageSize;
        }
        return count;
    }

    public int page(Integer requested) {
        int page = requested == null ? 0 : requested;
        if (page < 0) {
            throw new BadRequestException("page 不能为负");
        }
        if (maxPageIndex > 0 && page > maxPageIndex) {
            return maxPageIndex;
        }
        return page;
    }
}
