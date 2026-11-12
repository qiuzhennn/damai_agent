package com.qiuzhen.damaiagent.ticketing;

import java.util.List;

/** 公开演出只读网关，业务查询与大模型解耦。 */
public interface EventGateway {

    EventPage searchEvents(EventQuery query);

    EventSummary getEventDetail(long eventId);

    List<EventCategorySummary> listEventCategories();

    List<EventSessionSummary> getEventSessions(long eventId);

    List<TicketCategorySummary> getSessionTicketCategories(long sessionId);

    record EventPage(List<EventSummary> records, long total, int page, int size) {
        public EventPage {
            records = List.copyOf(records);
        }

        public boolean hasNext() {
            return (long) page * size < total;
        }
    }
}
