package dev.queryengine.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.queryengine.exception.BadRequestException;
import dev.queryengine.plan.RefHit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RefResolver resolver = new RefResolver();

    @Test
    void resolvesAbsoluteRelativeAndArrayLevels() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode user = root.putObject("User");
        user.put("id", 38710);

        ObjectNode outer = mapper.createObjectNode();
        outer.putObject("User").put("id", 1);
        ObjectNode inner = mapper.createObjectNode();
        inner.putObject("Moment").put("id", 2);

        var stack = RefContext.newStack();
        stack.addFirst(outer);
        stack.addFirst(inner);
        RefContext context = new RefContext(root, stack);

        assertEquals(38710L, resolver.resolve("/User/id", context).value());
        assertEquals(1L, resolver.resolve("User/id", context).value());
        assertEquals(2L, resolver.resolve("[]/Moment/id", context).value());
        assertEquals(1L, resolver.resolve("[]/[]/User/id", context).value());
    }

    @Test
    void missingAndNullAreDistinct() {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("User").putNull("tag");
        RefContext context = new RefContext(root, RefContext.newStack());

        RefHit missing = resolver.resolve("/User/nope", context);
        assertFalse(missing.found());

        RefHit tag = resolver.resolve("/User/tag", context);
        assertTrue(tag.found());
        assertNull(tag.value());

        assertThrows(BadRequestException.class, () -> resolver.resolve("[]/User/id", context));
        assertThrows(BadRequestException.class, () -> resolver.resolve("/User", context));
    }
}
