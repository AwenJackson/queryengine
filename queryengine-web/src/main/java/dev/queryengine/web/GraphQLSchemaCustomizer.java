package dev.queryengine.web;

import graphql.schema.GraphQLSchema;

/**
 * access 名单生成 GraphQL schema 之后、真正 build 之前的钩子。
 * 列参数仍然只有 {@code Long} 和 {@code String}，分页参数才是内置 {@code Int}。
 */
@FunctionalInterface
public interface GraphQLSchemaCustomizer {

    void customize(GraphQLSchema.Builder schema, String callSchema);
}
