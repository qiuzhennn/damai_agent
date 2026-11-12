package com.qiuzhen.damaiagent.ticketing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockTicketingGatewayTest {

    private final TicketingGateway gateway =
            new MockTicketingGateway();

    @Test
    void shouldSearchOnlyCurrentUsersMatchingOrders() {
        var result = gateway.searchMyOrders(
                1001L,
                new OrderQuery(null, " 上海 ", 0, 1, 10),
                null
        );

        assertEquals(2L, result.total());
        assertEquals(
                List.of(102L, 101L),
                result.records().stream()
                        .map(OrderSummary::orderId)
                        .toList()
        );

        var otherUserResult = gateway.searchMyOrders(
                1002L,
                new OrderQuery(null, "上海", 0, 1, 10),
                null
        );

        assertEquals(
                List.of(201L),
                otherUserResult.records().stream()
                        .map(OrderSummary::orderId)
                        .toList()
        );
    }

    @Test
    void shouldPaginateAfterFiltering() {
        var firstPage = gateway.searchMyOrders(
                1001L,
                new OrderQuery(null, "上海", 0, 1, 1),
                null
        );

        var secondPage = gateway.searchMyOrders(
                1001L,
                new OrderQuery(null, "上海", 0, 2, 1),
                null
        );

        assertEquals(2L, firstPage.total());
        assertEquals(102L, firstPage.records().getFirst().orderId());
        assertTrue(firstPage.hasNext());

        assertEquals(2L, secondPage.total());
        assertEquals(101L, secondPage.records().getFirst().orderId());
        assertFalse(secondPage.hasNext());
    }

    @Test
    void shouldReturnEmptyWhenNothingMatches() {
        var result = gateway.searchMyOrders(
                1001L,
                new OrderQuery("不存在的演出", null, null, 1, 10),
                null
        );

        assertEquals(0L, result.total());
        assertTrue(result.records().isEmpty());
        assertFalse(result.hasNext());
    }

    @Test
    void shouldFindPaidAndCancelledOrders() {
        var paid = gateway.searchMyOrders(
                1001L,
                new OrderQuery(null, null, 1, 1, 10),
                null
        );

        var cancelled = gateway.searchMyOrders(
                1001L,
                new OrderQuery(null, null, 2, 1, 10),
                null
        );

        assertEquals(103L, paid.records().getFirst().orderId());
        assertEquals(104L, cancelled.records().getFirst().orderId());
    }

    @Test
    void shouldRejectInvalidQueryParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderQuery(null, null, 4, 1, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderQuery(null, null, null, 0, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderQuery(null, null, null, 1, 21)
        );
    }
}
