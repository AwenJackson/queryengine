package dev.queryengine.authz;

import dev.queryengine.Method;
import dev.queryengine.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAccessRegistryTest {

    @Test
    void registerGrantsEveryMethod() {
        DefaultAccessRegistry registry = new DefaultAccessRegistry();
        registry.register("User", null);
        for (Method method : Method.values()) {
            assertTrue(registry.require("User", method).allows(method));
        }
        registry.register("Moment", "app_a");
        assertEquals("app_a", registry.require("Moment", Method.DELETE, "app_a").schema());
        assertEquals("Moment", registry.require("Moment", Method.GET, "app_a").physicalName());
        assertThrows(AccessDeniedException.class, () -> registry.require("Moment", Method.GET));
        assertThrows(AccessDeniedException.class, () -> registry.require("Moment", Method.GET, "demo"));
        registry.register("User", "user", null);
        assertEquals("user", registry.require("user", Method.GET).physicalName());
        assertEquals("user", registry.require("USER", Method.POST).physicalName());
        registry.register("BOOK", "book", null);
        assertEquals("book", registry.require("Book", Method.GET).physicalName());
        registry.unregister("User");
        assertThrows(AccessDeniedException.class, () -> registry.require("User", Method.GET));
        assertThrows(AccessDeniedException.class, () -> registry.require("Secret", Method.GET));
    }

    @Test
    void queryUsesOnlyTheCurrentSchemaCatalog() {
        DefaultAccessRegistry registry = new DefaultAccessRegistry();
        registry.registerIn("demo", "Book_menu_tip", "book_menu_tip", "demo1");
        registry.registerIn("demo", "Book", "book", "demo");
        registry.registerIn(null, "Moment", "moment", null);
        registry.registerIn("demo1", "User", "user", null);
        assertEquals("demo1", registry.require("Book_menu_tip", Method.GET, "demo").schema());
        assertThrows(AccessDeniedException.class,
                () -> registry.require("Book_menu_tip", Method.GET, "demo1"));
        assertThrows(AccessDeniedException.class,
                () -> registry.require("Book", Method.GET, "demo1"));
        assertThrows(AccessDeniedException.class,
                () -> registry.require("Moment", Method.GET, "demo1"));
        assertEquals("moment", registry.require("Moment", Method.GET).physicalName());
        assertEquals("user", registry.require("User", Method.GET, "demo1").physicalName());
    }
}
