package com.qiuzhen.damaiagent.ticketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventQueryTest {
    @Test
    void shouldTrimOptionalFilters() {
        var query = new EventQuery("  周末音乐会  ", "   ", null, 1, 10);
        assertEquals("周末音乐会", query.keyword());
        assertNull(query.city());
        assertNull(query.categoryId());
    }

    @Test
    void shouldRejectInvalidFilters() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventQuery(null, null, 0L, 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new EventQuery(null, null, null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new EventQuery(null, null, null, 1, 21));
        assertThrows(IllegalArgumentException.class,
                () -> new EventQuery("a".repeat(101), null, null, 1, 10));
    }
}
