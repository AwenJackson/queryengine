package dev.queryengine.plan;

import dev.queryengine.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagePolicyTest {

    @Test
    void clampsCountAndPageWithoutRejectingThem() {
        PagePolicy policy = new PagePolicy(10, 25, 3);
        assertEquals(10, policy.count(null));
        assertEquals(25, policy.count(1000));
        assertEquals(4, policy.count(4));
        assertEquals(0, policy.page(null));
        assertEquals(3, policy.page(99));
    }

    @Test
    void zeroMaxDisablesClamp() {
        PagePolicy policy = new PagePolicy(10, 0, 0);
        assertEquals(5000, policy.count(5000));
        assertEquals(80, policy.page(80));
    }

    @Test
    void rejectsNonPositiveCount() {
        PagePolicy policy = new PagePolicy(10, 0, 0);
        assertThrows(BadRequestException.class, () -> policy.count(0));
        assertThrows(BadRequestException.class, () -> policy.page(-1));
    }
}
