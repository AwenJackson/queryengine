package dev.queryengine.exec;

import dev.queryengine.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildForeignKeyTest {

    @Test
    void snakeFieldsSelectSnakeColumn() {
        assertEquals("comment_id", ChildForeignKey.choose("Type", "Comment", List.of("user_id", "type_name")));
        assertEquals("type_id", ChildForeignKey.choose("Book", "Type", List.of("book_name", "book_desc")));
        assertEquals("book_id", ChildForeignKey.choose("Book_menu", "Book", List.of("menu_name")));
        assertEquals("book_menu_id", ChildForeignKey.choose("Book_menu_tip", "Book_menu", List.of("tip_name")));
        assertEquals("book_menu_id", ChildForeignKey.choose("Book_menu_tip", "Book_menu", List.of("name")));
    }

    @Test
    void camelFieldsSelectCamelColumn() {
        assertEquals("momentId", ChildForeignKey.choose("Comment", "Moment", List.of("userId", "content")));
        assertEquals("commentId", ChildForeignKey.choose("Type", "Comment", List.of("userId")));
    }

    @Test
    void explicitCandidateIsLeftAlone() {
        assertNull(ChildForeignKey.choose("Type", "Comment", List.of("user_id", "comment_id")));
        assertNull(ChildForeignKey.choose("Type", "Comment", List.of("comment_id@")));
        assertNull(ChildForeignKey.choose("Type", "Comment", List.of("commentId")));
        assertTrue(ChildForeignKey.assigned("Comment", List.of("comment_id@")));
    }

    @Test
    void ambiguousNamesAreRejected() {
        BadRequestException none = assertThrows(
                BadRequestException.class,
                () -> ChildForeignKey.choose("Type", "Comment", List.of("id"))
        );
        assertTrue(none.getMessage().contains("comment_id"));
        assertTrue(none.getMessage().contains("commentId"));

        BadRequestException mixed = assertThrows(
                BadRequestException.class,
                () -> ChildForeignKey.choose("Type", "Comment", List.of("user_id", "userId"))
        );
        assertTrue(mixed.getMessage().contains("Comment"));
    }
}
