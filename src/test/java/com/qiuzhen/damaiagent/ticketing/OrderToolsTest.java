package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 不调用模型，直接验证工具参数与程序上下文走不同的传递通道。
 */
class OrderToolsTest {

    @Test
    void shouldKeepIdentityAndSessionOutOfModelSchema() {
        TicketingGateway gateway = (userId, query, sessionId) ->
                new TicketingGateway.OrderPage(List.of(), 0, query.page(), query.size());
        var callback = java.util.Arrays.stream(ToolCallbacks.from(new OrderTools(gateway)))
                .filter(item -> "searchMyOrders".equals(item.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String schema = callback.getToolDefinition().inputSchema();

        assertTrue(schema.contains("city"));
        assertFalse(schema.contains(TicketingAgent.CURRENT_USER_ID));
        assertFalse(schema.contains(TicketingAgent.SESSION_ID));
        assertFalse(schema.contains("sessionId"));
    }

    @Test
    void shouldPassContextForEachToolInvocation() {
        List<String> identities = new ArrayList<>();
        List<OrderQuery> queries = new ArrayList<>();
        TicketingGateway gateway = (userId, query, sessionId) -> {
            identities.add(userId + ":" + sessionId);
            queries.add(query);
            return new TicketingGateway.OrderPage(
                    List.of(), 0, query.page(), query.size());
        };
        var callback = java.util.Arrays.stream(ToolCallbacks.from(new OrderTools(gateway)))
                .filter(item -> "searchMyOrders".equals(item.getToolDefinition().name()))
                .findFirst().orElseThrow();

        callback.call("""
                {"city":"上海","status":0}
                """, context(1001L, "session-a"));
        callback.call("""
                {"city":"北京","status":1}
                """, context(1002L, "session-b"));

        assertEquals(List.of("1001:session-a", "1002:session-b"), identities);
        assertEquals(new OrderQuery(null, "上海", 0, 1, 10), queries.get(0));
        assertEquals(new OrderQuery(null, "北京", 1, 1, 10), queries.get(1));
    }

    private static ToolContext context(long userId, String sessionId) {
        return new ToolContext(Map.of(
                TicketingAgent.CURRENT_USER_ID, userId,
                TicketingAgent.SESSION_ID, sessionId
        ));
    }
}

