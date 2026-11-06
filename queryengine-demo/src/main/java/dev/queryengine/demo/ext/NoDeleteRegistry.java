package dev.queryengine.demo.ext;

import dev.queryengine.Method;
import dev.queryengine.authz.DefaultAccessRegistry;
import dev.queryengine.exception.AccessDeniedException;
import dev.queryengine.spi.TableGrant;

/**
 * 继承默认授权表，禁止一切 DELETE。其余 method 仍走父类。
 */
public class NoDeleteRegistry extends DefaultAccessRegistry {

    @Override
    public TableGrant require(String table, Method method, String callSchema) {
        if (method == Method.DELETE) {
            throw new AccessDeniedException("禁止删除: " + table);
        }
        return super.require(table, method, callSchema);
    }
}
