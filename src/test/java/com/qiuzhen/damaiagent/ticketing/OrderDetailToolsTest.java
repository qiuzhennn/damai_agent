package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import com.qiuzhen.damaiagent.agent.ChatReply;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderDetailToolsTest {

    @Test
    void shouldUseTrustedUserAndSessionAndKeepThemOutOfModelSchema() {
        var gateway = new TicketingGateway() {
            @Override
            public OrderPage searchMyOrders(long currentUserId, OrderQuery query, String sid) {
                throw new AssertionError("不应查询列表");
            }
            @Override
            public OrderDetail getMyOrderDetail(long uid, long orderId, String sid) {
                assertEquals(1001L, uid);
                assertEquals("session-a", sid);
                assertEquals(23L, orderId);
                return new OrderDetail(orderId, "北京音乐会", "北京", "国家大剧院",
                        null, "内场A区", 2, new BigDecimal("280"),
                        new BigDecimal("560"), 0, null, null, null, null);
            }
        };
        var detailTool = List.of(ToolCallbacks.from(new OrderTools(gateway))).stream()
                .filter(callback -> "getMyOrderDetail".equals(callback.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String schema = detailTool.getToolDefinition().inputSchema();
        assertTrue(schema.contains("orderId"));
        assertFalse(schema.contains("currentUserId"));
        assertFalse(schema.contains("ticketingSessionId"));
        assertFalse(schema.contains("sessionId"));

        var cards = new ChatReply.OrderCards();
        String result = detailTool.call("{\"orderId\":23}", new ToolContext(Map.of(
                TicketingAgent.CURRENT_USER_ID, 1001L,
                TicketingAgent.SESSION_ID, "session-a",
                ChatReply.OrderCards.CONTEXT_KEY, cards)));
        assertTrue(result.contains("国家大剧院"));
        assertTrue(result.contains("内场A区"));
        assertEquals(1, cards.snapshot().size());
        assertEquals(23L, cards.snapshot().getFirst().orderId());
        assertEquals("国家大剧院", cards.snapshot().getFirst().venue());
    }
}
