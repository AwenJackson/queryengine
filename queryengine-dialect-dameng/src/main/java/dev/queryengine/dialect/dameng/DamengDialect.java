package dev.queryengine.dialect.dameng;

import dev.queryengine.spi.Dialect;

import java.util.List;
import java.util.Locale;

/**
 * 达梦方言。默认把目录标识符转成大写后加双引号，以匹配达梦对未加引号标识符的折叠规则。
 * 结果别名保持请求里的写法。分页使用 {@code OFFSET ? ROWS FETCH NEXT ? ROWS ONLY}。
 * <p>
 * 驱动不在本模块的编译依赖里。宿主自行引入
 * {@code com.dameng:DmJdbcDriver18}，见 docs/dialects.md。
 */
public final class DamengDialect implements Dialect {

    private final boolean uppercaseIdentifiers;

    public DamengDialect() {
        this(true);
    }

    public DamengDialect(boolean uppercaseIdentifiers) {
        this.uppercaseIdentifiers = uppercaseIdentifiers;
    }

    public boolean uppercaseIdentifiers() {
        return uppercaseIdentifiers;
    }

    @Override
    public String name() {
        return "dameng";
    }

    @Override
    public String quote(String identifier) {
        String name = uppercaseIdentifiers ? identifier.toUpperCase(Locale.ROOT) : identifier;
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String quoteAlias(String alias) {
        return "\"" + alias.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void appendPage(StringBuilder sql, List<Object> params, int limit, int offset) {
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);
    }
}
