package dev.queryengine.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.queryengine.Method;
import dev.queryengine.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FieldParser parser = new FieldParser();

    @Test
    void readsOperatorsColumnAndOrder() throws Exception {
        var body = mapper.readTree("""
                {
                  "@column": "id, name:userName",
                  "@order": "id-, name+",
                  "sex": 0,
                  "id>=": 10,
                  "id{}": [1, 2],
                  "name$": ["%a%", "%b%"],
                  "tag": null,
                  "removed!": 1,
                  "span{}": ">=80000,<=90000"
                }
                """);

        TableRequest request = parser.parse(body, Method.GET, path -> RefHit.missing(), "id");

        assertEquals(2, request.columns().size());
        assertEquals("userName", request.columns().get(1).alias());
        assertTrue(request.orderBy().get(0).descending());
        assertEquals(Op.LIKE, request.where().get(3).op());
        assertEquals(Op.IS_NULL, request.where().get(4).op());
        assertEquals(Op.GTE, request.where().get(6).op());
        assertEquals(80000L, request.where().get(6).values().get(0));
        assertEquals(Op.LTE, request.where().get(7).op());
    }

    @Test
    void referenceAndPutSplit() throws Exception {
        var body = mapper.readTree("""
                { "id@": "/User/id", "title": "next", "done": 1 }
                """);
        TableRequest request = parser.parse(body, Method.PUT, path -> RefHit.found(9L), "id");
        assertEquals(1, request.where().size());
        assertEquals(9L, request.where().get(0).values().get(0));
        assertEquals(2, request.assignments().size());
        assertTrue(parser.parse(mapper.readTree("{\"id@\":\"/missing\"}"), Method.GET, path -> RefHit.missing(), "id").unresolved());
    }

    @Test
    void rejectsFunctionsAndUnknownDirectives() throws Exception {
        var function = mapper.readTree("{\"@column\":\"count(id)\"}");
        assertThrows(BadRequestException.class, () -> parser.parse(function, Method.GET, path -> RefHit.missing(), "id"));
        var raw = mapper.readTree("{\"@raw\":\"1=1\"}");
        assertThrows(BadRequestException.class, () -> parser.parse(raw, Method.GET, path -> RefHit.missing(), "id"));
        var write = mapper.readTree("{\"@column\":\"id\"}");
        assertThrows(BadRequestException.class, () -> parser.parse(write, Method.POST, path -> RefHit.missing(), "id"));
    }
}
