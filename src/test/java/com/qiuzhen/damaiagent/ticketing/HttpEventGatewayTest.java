package com.qiuzhen.damaiagent.ticketing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpEventGatewayTest {
    private MockRestServiceServer server;
    private HttpEventGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ticketing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new HttpEventGateway(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void shouldEncodeFiltersAndReadPageWithoutCookie() {
        String url = "http://ticketing.test/public/events?keyword="
                + encode("音乐会+春天") + "&city=" + encode("上海")
                + "&categoryId=8&page=2&size=5";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertFalse(request.getHeaders().containsKey(HttpHeaders.COOKIE)))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":{"records":[{
                          "id":21,"title":"春天音乐会","artist":"歌手A",
                          "city":"上海","venue":"上海体育馆", "categoryId":8,
                          "status":1,"extraPosterField":"ignored"}],
                          "total":8,"page":2,"size":5,"pages":2}}
                        """, MediaType.APPLICATION_JSON));

        var page = gateway.searchEvents(new EventQuery("音乐会+春天", "上海", 8L, 2, 5));
        assertEquals(8, page.total());
        assertFalse(page.hasNext());
        assertEquals("上海体育馆", page.records().getFirst().venue());
        assertEquals(21, page.records().getFirst().id());
    }

    @Test
    void shouldReadEventDetailAndSessionTimes() {
        server.expect(requestTo("http://ticketing.test/public/events/21"))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":{"id":21,"title":"春天音乐会",
                          "artist":"歌手A","city":"上海","venue":"上海体育馆",
                          "categoryId":8,"status":1}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ticketing.test/public/event-sessions?eventId=21"))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":[{"id":33,"eventId":21,
                          "startTime":"2026-11-18T19:30:00",
                          "saleStartTime":"2026-11-15T10:00:00", "status":1}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("春天音乐会", gateway.getEventDetail(21).title());
        var sessions = gateway.getEventSessions(21);
        assertEquals(33, sessions.getFirst().id());
        assertEquals(LocalDateTime.of(2026, 11, 15, 10, 0), sessions.getFirst().saleStartTime());
    }

    @Test
    void shouldReadCategoriesAndSessionTicketPrices() {
        server.expect(requestTo("http://ticketing.test/public/event-categories"))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":[{"id":8,"name":"演唱会"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ticketing.test/public/ticket-categories?sessionId=33"))
                .andExpect(request -> assertFalse(request.getHeaders().containsKey(HttpHeaders.COOKIE)))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":[{"id":66,"sessionId":33,"name":"看台",
                          "price":280.00,"availableStock":9}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("演唱会", gateway.listEventCategories().getFirst().name());
        var ticket = gateway.getSessionTicketCategories(33).getFirst();
        assertEquals("看台", ticket.name());
        assertEquals(0, ticket.price().compareTo(new BigDecimal("280.00")));
        assertEquals(Long.valueOf(9), ticket.availableStock());
    }

    @Test
    void shouldTreatEmptySuccessfulResultsAsEmptyLists() {
        server.expect(requestTo("http://ticketing.test/public/event-categories"))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":[]}
                        """,
                        MediaType.APPLICATION_JSON));
        assertTrue(gateway.listEventCategories().isEmpty());
    }

    @Test
    void shouldNeverTurnBackendErrorsIntoEmptySearchResults() {
        server.expect(requestTo("http://ticketing.test/public/events?page=1&size=10"))
                .andRespond(withSuccess("""
                        {"code":"QUERY_FAILED","data":null}
                        """, MediaType.APPLICATION_JSON));
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.searchEvents(new EventQuery(null, null, null, 1, 10)));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void shouldReturn404ForMissingEvent() {
        server.expect(requestTo("http://ticketing.test/public/events/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.getEventDetail(999));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void shouldRejectIncorrectEventIdFromBackend() {
        server.expect(requestTo("http://ticketing.test/public/events/21"))
                .andRespond(withSuccess("""
                        {"code":"SUCCESS","data":{"id":22,"title":"错误响应"}}
                        """, MediaType.APPLICATION_JSON));
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.getEventDetail(21));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void shouldRejectInvalidIdsWithoutSendingRequests() {
        assertThrows(IllegalArgumentException.class, () -> gateway.getEventSessions(-1));
        assertThrows(IllegalArgumentException.class, () -> gateway.getSessionTicketCategories(0));
        assertThrows(IllegalArgumentException.class, () -> gateway.getEventDetail(0));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
