package com.qiuzhen.damaiagent.ticketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OrderDetailGatewayTest {

    private MockRestServiceServer server;
    private HttpTicketingGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://ticketing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new HttpTicketingGateway(builder.build());
    }

    @AfterEach
    void verify() { server.verify(); }

    @Test
    void shouldReadVenueAndCompleteOrderDetailFromAuthenticatedEndpoint() {
        server.expect(requestTo("http://ticketing.test/user/orders/23"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=session-a"))
                .andRespond(withSuccess(detailResponse(1001, 23), MediaType.APPLICATION_JSON));

        var detail = gateway.getMyOrderDetail(1001L, 23L, "session-a");
        assertEquals(23L, detail.orderId());
        assertEquals("国家大剧院", detail.venue());
        assertEquals("内场A区", detail.ticketCategoryName());
        assertEquals(2, detail.quantity());
        assertEquals(0, new BigDecimal("280.00").compareTo(detail.unitPrice()));
        assertEquals(0, new BigDecimal("560.00").compareTo(detail.totalAmount()));
        assertEquals(LocalDateTime.of(2026, 11, 15, 19, 30), detail.startTime());
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 45), detail.expireTime());
    }

    @Test
    void shouldRejectOrderBelongingToAnotherUser() {
        server.expect(requestTo("http://ticketing.test/user/orders/23"))
                .andRespond(withSuccess(detailResponse(1002, 23), MediaType.APPLICATION_JSON));

        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.getMyOrderDetail(1001L, 23L, "session-a"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void shouldRejectOrderIdMismatch() {
        server.expect(requestTo("http://ticketing.test/user/orders/23"))
                .andRespond(withSuccess(detailResponse(1001, 24), MediaType.APPLICATION_JSON));

        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.getMyOrderDetail(1001L, 23L, "session-a"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void shouldPreserveNotFoundInsteadOfInventingDetails() {
        server.expect(requestTo("http://ticketing.test/user/orders/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.getMyOrderDetail(1001L, 99L, "session-a"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    private static String detailResponse(long userId, long id) {
        return """
                {
                  "code":"SUCCESS",
                  "message":"操作成功",
                  "data": {
                    "id": %d,
                    "userId": %d,
                    "eventTitle": "北京音乐会",
                    "city": "北京",
                    "venue": "国家大剧院",
                    "startTime": "2026-11-15T19:30:00",
                    "ticketCategoryName": "内场A区",
                    "quantity": 2,
                    "unitPrice": 280.00,
                    "totalAmount": 560.00,
                    "status": 0,
                    "createdAt": "2026-11-12T14:30:00",
                    "expireTime": "2026-11-12T14:45:00",
                    "paidAt": null,
                    "updatedAt": "2026-11-12T14:30:00"
                  }
                }
                """.formatted(id, userId);
    }
}
