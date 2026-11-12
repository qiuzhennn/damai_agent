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

/**
 * 只验证 HTTP 请求与响应映射，不连接真实后端，也不调用模型。
 */
class HttpTicketingGatewayTest {

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
    void verifyRequests() {
        server.verify();
    }

    @Test
    void shouldResolveCurrentUserFromBackendSession() {
        server.expect(requestTo("http://ticketing.test/auth/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=session-a"))
                .andRespond(withSuccess("""
                        {
                          "code": "SUCCESS",
                          "message": "操作成功",
                          "data": {
                            "id": 1001,
                            "username": "user1",
                            "accountType": "USER"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertEquals(1001L, gateway.currentUserId("session-a"));
    }

    @Test
    void shouldEncodeFiltersAndMapOrderResponse() {
        String url = "http://ticketing.test/user/orders"
                + "?keyword=" + encode("周末+音乐会")
                + "&city=" + encode("上海")
                + "&status=0&page=1&size=1";

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=session-a"))
                .andRespond(withSuccess(orderResponse(1001L),
                        MediaType.APPLICATION_JSON));

        var page = gateway.searchMyOrders(
                1001L, new OrderQuery("周末+音乐会", "上海", 0, 1, 1), "session-a");

        assertEquals(2L, page.total());
        assertTrue(page.hasNext());

        var order = page.records().getFirst();
        assertEquals(102L, order.orderId());
        assertEquals("上海周末+音乐会", order.eventTitle());
        assertEquals("上海", order.city());
        assertEquals(LocalDateTime.of(2026, 11, 15, 19, 30), order.startTime());
        assertEquals(0, order.status());
        assertEquals(0, new BigDecimal("180.00").compareTo(order.totalAmount()));
    }

    @Test
    void shouldRejectExpiredSession() {
        server.expect(requestTo("http://ticketing.test/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.currentUserId("expired-session"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void shouldRequireSessionInsteadOfUsingOnlyUserId() {
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.searchMyOrders(
                        1001L, new OrderQuery(null, null, null, 1, 10), null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void shouldNotTurnBackendFailureIntoEmptyOrders() {
        server.expect(requestTo(
                        "http://ticketing.test/user/orders?page=1&size=10"))
                .andRespond(withSuccess("""
                        {
                          "code": "QUERY_FAILED",
                          "message": "查询失败",
                          "data": null
                        }
                        """, MediaType.APPLICATION_JSON));

        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.searchMyOrders(
                        1001L, new OrderQuery(null, null, null, 1, 10), "session-a"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void shouldRejectOrdersForDifferentUserContext() {
        server.expect(requestTo(
                        "http://ticketing.test/user/orders?page=1&size=10"))
                .andRespond(withSuccess(orderResponse(1002L),
                        MediaType.APPLICATION_JSON));

        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.searchMyOrders(
                        1001L, new OrderQuery(null, null, null, 1, 10), "session-a"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String orderResponse(long userId) {
        return """
                {
                  "code": "SUCCESS",
                  "message": "操作成功",
                  "data": {
                    "records": [{
                      "id": 102,
                      "userId": %d,
                      "eventTitle": "上海周末+音乐会",
                      "city": "上海",
                      "startTime": "2026-11-15T19:30:00",
                      "status": 0,
                      "totalAmount": 180.00,
                      "quantity": 1,
                      "venue": "测试场馆"
                    }],
                    "total": 2,
                    "page": 1,
                    "size": 1,
                    "pages": 2
                  }
                }
                """.formatted(userId);
    }
}

