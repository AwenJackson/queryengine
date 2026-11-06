package dev.queryengine.web;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 在统一信封写完之后再改一笔。不要去掉 {@code code} 和 {@code message}。
 */
@FunctionalInterface
public interface ResponseCustomizer {

    ObjectNode customize(ObjectNode body);
}
