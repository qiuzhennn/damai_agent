package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EventToolsTest {
    private final RecordingEventGateway gateway = new RecordingEventGateway();
    private final EventTools tools = new EventTools(gateway);

    @Test
    void shouldExposeFiveReadOnlyToolsWithoutIdentity() {
        // 不依赖 @Tool 反射扫描顺序。
        Map<String, ToolCallback> callbacks = java.util.Arrays.stream(ToolCallbacks.from(tools))
                .collect(Collectors.toMap(t -> t.getToolDefinition().name(), Function.identity()));

        assertEquals(5, callbacks.size());
        assertTrue(callbacks.keySet().containsAll(List.of(
                "searchEvents", "listEventCategories", "getEventDetail",
                "getEventSessions", "getSessionTicketCategories")));
        for (ToolCallback callback : callbacks.values()) {
            String schema = callback.getToolDefinition().inputSchema();
            assertFalse(schema.contains(TicketingAgent.CURRENT_USER_ID));
            assertFalse(schema.contains(TicketingAgent.SESSION_ID));
        }
    }

    @Test
    void shouldDefaultPaginationAndPreserveRealIds() {
        var page = tools.searchEvents("  音乐会  ", "北京", 8L, null, null);
        assertEquals(0, page.total());
        assertEquals(new EventQuery("音乐会", "北京", 8L, 1, 10), gateway.lastQuery);

        assertEquals("北京某场馆", tools.getEventDetail(21).venue());
        assertEquals(1, tools.getEventSessions(21).size());
        assertEquals(1, tools.getSessionTicketCategories(33).size());
        assertEquals(1, tools.listEventCategories().size());
        assertEquals(List.of("detail:21", "sessions:21", "tickets:33", "categories"),
                gateway.calls);
    }

    @Test
    void shouldRejectInvalidIdsBeforeCallingGateway() {
        assertThrows(IllegalArgumentException.class, () -> tools.getEventDetail(-1));
        assertThrows(IllegalArgumentException.class, () -> tools.getEventSessions(0));
        assertThrows(IllegalArgumentException.class, () -> tools.getSessionTicketCategories(0));
        assertTrue(gateway.calls.isEmpty());
    }

    private static class RecordingEventGateway implements EventGateway {
        EventQuery lastQuery;
        final List<String> calls = new ArrayList<>();

        @Override
        public EventPage searchEvents(EventQuery query) {
            lastQuery = query;
            return new EventPage(List.of(), 0, query.page(), query.size());
        }

        @Override
        public EventSummary getEventDetail(long eventId) {
            calls.add("detail:" + eventId);
            return new EventSummary(eventId, "北京音乐会", "歌手A", "北京", "北京某场馆", 8L, 1);
        }

        @Override
        public List<EventCategorySummary> listEventCategories() {
            calls.add("categories");
            return List.of(new EventCategorySummary(8, "演唱会"));
        }

        @Override
        public List<EventSessionSummary> getEventSessions(long eventId) {
            calls.add("sessions:" + eventId);
            return List.of(new EventSessionSummary(33, eventId,
                    LocalDateTime.of(2026, 11, 18, 19, 30),
                    LocalDateTime.of(2026, 11, 15, 10, 0), 1));
        }

        @Override
        public List<TicketCategorySummary> getSessionTicketCategories(long sessionId) {
            calls.add("tickets:" + sessionId);
            return List.of(new TicketCategorySummary(66, sessionId, "看台", new BigDecimal("280"), 9L));
        }
    }
}
