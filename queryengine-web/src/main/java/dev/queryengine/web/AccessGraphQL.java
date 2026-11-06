package dev.queryengine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.Method;
import dev.queryengine.QueryEngine;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.plan.Identifiers;
import dev.queryengine.spi.TableGrant;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.SelectedField;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按当前 schema 的 access 名单懒生成 GraphQL schema。列来自一次 {@code WHERE 1 = 0} 的结果集元数据。
 */
final class AccessGraphQL {

    static final String SCHEMA_KEY = "callSchema";

    private final QueryEngine engine;
    private final ObjectMapper mapper;
    private final DataSource dataSource;
    private final GraphQLSchemaCustomizer schemaCustomizer;
    private final GraphQLScalarType longScalar = longScalar();
    private final ConcurrentHashMap<String, GraphQL> cache = new ConcurrentHashMap<>();

    AccessGraphQL(QueryEngine engine, ObjectMapper mapper, DataSource dataSource, GraphQLSchemaCustomizer schemaCustomizer) {
        this.engine = engine;
        this.mapper = mapper;
        this.dataSource = dataSource;
        this.schemaCustomizer = schemaCustomizer;
    }

    GraphQL graphQL(String callSchema) {
        List<TableGrant> grants = new ArrayList<>(engine.listAccess(callSchema));
        String key = fingerprint(callSchema, grants);
        return cache.computeIfAbsent(key, ignored -> GraphQL.newGraphQL(schema(callSchema, grants)).build());
    }

    private GraphQLSchema schema(String callSchema, List<TableGrant> grants) {
        Map<String, TableShape> shapes = new LinkedHashMap<>();
        Set<String> tableNames = new LinkedHashSet<>();
        for (TableGrant grant : grants) {
            if (!Identifiers.isValid(grant.table()) || tableNames.contains(grant.table())) {
                continue;
            }
            tableNames.add(grant.table());
            shapes.put(grant.table(), new TableShape(grant, columnsOf(callSchema, grant)));
        }
        Map<String, GraphQLObjectType.Builder> builders = new LinkedHashMap<>();
        for (TableShape shape : shapes.values()) {
            builders.put(shape.grant.table(), GraphQLObjectType.newObject().name(shape.grant.table()));
        }
        for (TableShape shape : shapes.values()) {
            GraphQLObjectType.Builder builder = builders.get(shape.grant.table());
            Set<String> columnNames = new LinkedHashSet<>();
            for (Column column : shape.columns) {
                columnNames.add(column.name);
                builder.field(GraphQLFieldDefinition.newFieldDefinition()
                        .name(column.name)
                        .type(column.integral ? longScalar : Scalars.GraphQLString));
            }
            if (!columnNames.contains("affected")) {
                builder.field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("affected")
                        .type(longScalar)
                        .dataFetcher(env -> number(env.getSource(), "count")));
            }
            for (TableShape other : shapes.values()) {
                if (other.grant.table().equals(shape.grant.table()) || columnNames.contains(other.grant.table())) {
                    continue;
                }
                builder.field(relationField(other));
            }
        }
        Map<String, GraphQLObjectType> types = new LinkedHashMap<>();
        for (var entry : builders.entrySet()) {
            types.put(entry.getKey(), entry.getValue().build());
        }
        GraphQLObjectType.Builder query = GraphQLObjectType.newObject().name("Query");
        GraphQLObjectType.Builder mutation = GraphQLObjectType.newObject().name("Mutation");
        if (types.isEmpty()) {
            query.field(GraphQLFieldDefinition.newFieldDefinition().name("_empty").type(Scalars.GraphQLBoolean).dataFetcher(env -> true));
        }
        for (TableShape shape : shapes.values()) {
            GraphQLObjectType type = types.get(shape.grant.table());
            String listName = shape.grant.table() + "List";
            if (tableNames.contains(listName)) {
                listName = shape.grant.table() + "Rows";
            }
            query.field(readField(shape, type, false).name(shape.grant.table()));
            query.field(readField(shape, type, true).name(listName));
            mutation.field(writeField(shape, type, Method.POST));
            mutation.field(writeField(shape, type, Method.PUT));
            mutation.field(writeField(shape, type, Method.DELETE));
        }
        GraphQLSchema.Builder schema = GraphQLSchema.newSchema().query(query.build());
        if (!shapes.isEmpty()) {
            schema.mutation(mutation.build());
        }
        if (schemaCustomizer != null) {
            schemaCustomizer.customize(schema, callSchema);
        }
        return schema.build();
    }

    private GraphQLFieldDefinition.Builder readField(TableShape shape, GraphQLObjectType type, boolean list) {
        GraphQLOutputType output = list ? GraphQLList.list(type) : type;
        GraphQLFieldDefinition.Builder field = GraphQLFieldDefinition.newFieldDefinition().type(output);
        if (list) {
            field.argument(GraphQLArgument.newArgument().name("page").type(Scalars.GraphQLInt));
            field.argument(GraphQLArgument.newArgument().name("count").type(Scalars.GraphQLInt));
        }
        for (Column column : shape.columns) {
            if (column.name.equals("page") || column.name.equals("count")) {
                continue;
            }
            field.argument(GraphQLArgument.newArgument().name(column.name).type(column.integral ? longScalar : Scalars.GraphQLString));
        }
        field.dataFetcher(env -> list ? fetchList(shape, env) : fetchOne(shape, env));
        return field;
    }

    private GraphQLFieldDefinition relationField(TableShape child) {
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(child.grant.table())
                .type(GraphQLList.list(GraphQLTypeReference.typeRef(child.grant.table())))
                .argument(GraphQLArgument.newArgument().name("on").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                .argument(GraphQLArgument.newArgument().name("page").type(Scalars.GraphQLInt))
                .argument(GraphQLArgument.newArgument().name("count").type(Scalars.GraphQLInt))
                .dataFetcher(env -> fetchRelation(child, env))
                .build();
    }

    private GraphQLFieldDefinition writeField(TableShape shape, GraphQLObjectType type, Method method) {
        String prefix = switch (method) {
            case POST -> "create";
            case PUT -> "update";
            case DELETE -> "delete";
            default -> throw new IllegalStateException(method.name());
        };
        GraphQLFieldDefinition.Builder field = GraphQLFieldDefinition.newFieldDefinition()
                .name(prefix + shape.grant.table())
                .type(type);
        if (method == Method.DELETE) {
            Column idColumn = shape.columns.stream().filter(column -> column.name.equals("id")).findFirst().orElse(new Column("id", true));
            field.argument(GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(idColumn.integral ? longScalar : Scalars.GraphQLString)));
        } else {
            for (Column column : shape.columns) {
                var argument = GraphQLArgument.newArgument().name(column.name).type(column.integral ? longScalar : Scalars.GraphQLString);
                if (method == Method.PUT && column.name.equals("id")) {
                    argument.type(GraphQLNonNull.nonNull(column.integral ? longScalar : Scalars.GraphQLString));
                }
                field.argument(argument);
            }
            if (method == Method.PUT && shape.columns.stream().noneMatch(column -> column.name.equals("id"))) {
                field.argument(GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(longScalar)));
            }
        }
        field.dataFetcher(env -> fetchWrite(shape, method, env));
        return field.build();
    }

    private Object fetchOne(TableShape shape, DataFetchingEnvironment env) {
        ObjectNode root = mapper.createObjectNode();
        root.set(shape.grant.table(), spec(shape, env, false));
        JsonNode result = engine.execute(Method.GET, root, callSchema(env));
        return mapRow(result.get(shape.grant.table()));
    }

    private List<Map<String, Object>> fetchList(TableShape shape, DataFetchingEnvironment env) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode array = root.putObject("[]");
        page(array, env, 0, null);
        array.set(shape.grant.table(), spec(shape, env, false));
        JsonNode result = engine.execute(Method.GET, root, callSchema(env));
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode items = result.get("[]");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                Map<String, Object> row = mapRow(item.get(shape.grant.table()));
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> fetchRelation(TableShape child, DataFetchingEnvironment env) {
        String on = env.getArgument("on");
        Identifiers.checkRequest(on, "关联字段");
        Object parentId = value(env.getSource(), "id");
        if (parentId == null) {
            return List.of();
        }
        ObjectNode spec = spec(child, env, true);
        spec.set(on, mapper.valueToTree(parentId));
        ObjectNode root = mapper.createObjectNode();
        ObjectNode array = root.putObject("[]");
        page(array, env, 0, 100);
        array.set(child.grant.table(), spec);
        JsonNode result = engine.execute(Method.GET, root, callSchema(env));
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode items = result.get("[]");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                Map<String, Object> row = mapRow(item.get(child.grant.table()));
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private Map<String, Object> fetchWrite(TableShape shape, Method method, DataFetchingEnvironment env) {
        ObjectNode spec = mapper.createObjectNode();
        for (Map.Entry<String, Object> entry : env.getArguments().entrySet()) {
            if (entry.getValue() != null) {
                spec.set(entry.getKey(), mapper.valueToTree(entry.getValue()));
            }
        }
        if (method != Method.POST && !spec.has("id")) {
            throw new BadRequestException("写操作必须包含主键条件 id");
        }
        ObjectNode root = mapper.createObjectNode();
        root.set(shape.grant.table(), spec);
        JsonNode result = engine.execute(method, root, callSchema(env));
        return mapRow(result.get(shape.grant.table()));
    }

    private ObjectNode spec(TableShape shape, DataFetchingEnvironment env, boolean relation) {
        ObjectNode spec = mapper.createObjectNode();
        if (!relation) {
            for (Column column : shape.columns) {
                if (column.name.equals("page") || column.name.equals("count")) {
                    continue;
                }
                Object argument = env.getArgument(column.name());
                if (argument != null) {
                    spec.set(column.name(), mapper.valueToTree(argument));
                }
            }
        }
        List<String> selected = new ArrayList<>();
        boolean nested = false;
        if (env.getSelectionSet() != null) {
            for (SelectedField field : env.getSelectionSet().getImmediateFields()) {
                String name = field.getName();
                if (name.equals("affected") || name.startsWith("__")) {
                    continue;
                }
                boolean column = shape.columns.stream().anyMatch(item -> item.name.equals(name));
                if (column) {
                    selected.add(name);
                } else {
                    nested = true;
                }
            }
        }
        if (nested && shape.columns.stream().anyMatch(column -> column.name.equals("id")) && !selected.contains("id")) {
            selected.add("id");
        }
        if (!selected.isEmpty()) {
            spec.put("@column", String.join(",", selected));
        }
        return spec;
    }

    private void page(ObjectNode array, DataFetchingEnvironment env, int defaultPage, Integer defaultCount) {
        Integer page = env.getArgument("page");
        Integer count = env.getArgument("count");
        array.put("page", page == null ? defaultPage : page);
        if (count != null) {
            array.put("count", count);
        } else if (defaultCount != null) {
            array.put("count", defaultCount);
        }
    }

    private List<Column> columnsOf(String callSchema, TableGrant grant) {
        String sqlSchema = grant.schema() != null ? grant.schema() : callSchema;
        String sql = "SELECT * FROM " + tableRef(sqlSchema, grant.physicalName()) + " WHERE 1 = 0";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData meta = resultSet.getMetaData();
            List<Column> columns = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String name = meta.getColumnLabel(i);
                if (name == null || name.isBlank() || !Identifiers.isValid(name) || !seen.add(name)) {
                    continue;
                }
                int type = meta.getColumnType(i);
                boolean integral = type == Types.BIGINT || type == Types.INTEGER || type == Types.SMALLINT || type == Types.TINYINT;
                columns.add(new Column(name, integral));
            }
            if (!columns.isEmpty()) {
                return columns;
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("无法读取表 " + grant.table() + " 的列: " + ex.getMessage());
        }
        return List.of(new Column("id", true));
    }

    private String tableRef(String schema, String physical) {
        boolean dameng = "dameng".equals(engine.dialectName());
        String table = quote(dameng ? physical.toUpperCase(Locale.ROOT) : physical.toLowerCase(Locale.ROOT), dameng);
        if (schema == null || schema.isBlank()) {
            return table;
        }
        return quote(dameng ? schema.toUpperCase(Locale.ROOT) : schema, dameng) + "." + table;
    }

    private static String quote(String identifier, boolean dameng) {
        if (dameng) {
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String callSchema(DataFetchingEnvironment env) {
        Object value = env.getGraphQlContext().get(SCHEMA_KEY);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapRow(JsonNode row) {
        if (row == null || row.isNull() || !row.isObject()) {
            return null;
        }
        return mapper.convertValue(row, Map.class);
    }

    private static Object value(Object source, String name) {
        if (source instanceof Map<?, ?> map) {
            return map.get(name);
        }
        return null;
    }

    private static Long number(Object source, String name) {
        Object value = value(source, name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static String fingerprint(String callSchema, List<TableGrant> grants) {
        StringBuilder builder = new StringBuilder(callSchema == null ? "" : callSchema);
        for (TableGrant grant : grants) {
            builder.append('|').append(grant.table()).append('=').append(grant.physicalName());
        }
        return builder.toString();
    }

    private static GraphQLScalarType longScalar() {
        return GraphQLScalarType.newScalar()
                .name("Long")
                .description("64 位整数")
                .coercing(new Coercing<Long, Long>() {
                    @Override
                    public Long serialize(Object dataFetcherResult, graphql.GraphQLContext graphQLContext, Locale locale) {
                        if (dataFetcherResult instanceof Number number) {
                            return number.longValue();
                        }
                        throw new CoercingSerializeException("不是整数");
                    }

                    @Override
                    public Long parseValue(Object input, graphql.GraphQLContext graphQLContext, Locale locale) {
                        return longValue(input);
                    }

                    @Override
                    public Long parseLiteral(Value<?> input, graphql.execution.CoercedVariables variables, graphql.GraphQLContext graphQLContext, Locale locale) {
                        if (input instanceof IntValue intValue) {
                            return intValue.getValue().longValue();
                        }
                        if (input instanceof StringValue stringValue) {
                            return longValue(stringValue.getValue());
                        }
                        throw new CoercingParseLiteralException("不是整数");
                    }

                    private Long longValue(Object input) {
                        if (input instanceof Number number) {
                            return number.longValue();
                        }
                        if (input instanceof String text) {
                            try {
                                return Long.parseLong(text);
                            } catch (NumberFormatException ex) {
                                throw new CoercingParseValueException("不是整数");
                            }
                        }
                        throw new CoercingParseValueException("不是整数");
                    }
                })
                .build();
    }

    private record Column(String name, boolean integral) {
    }

    private record TableShape(TableGrant grant, List<Column> columns) {
    }
}
