package com.qiuzhen.damaiagent.ticketing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对接仿大麦现有查询接口。
 * RestClient 可以复用，登录 Cookie 始终按本次调用设置，不保存在网关中。
 */
public class HttpTicketingGateway implements TicketingGateway {

    private final RestClient client;

    public HttpTicketingGateway(String baseUrl) {
        this(createClient(baseUrl));
    }

    public HttpTicketingGateway(RestClient client) {
        this.client = client;
    }

    /**
     * 使用后端现有登录会话取得身份，供应用在调用 Agent 前使用。
     * 本方法不注册为模型工具。
     */
    public long currentUserId(String sessionId) {
        UserBody user = read(
                client.get()
                        .uri("/auth/me")
                        .header(HttpHeaders.COOKIE, cookieHeader(sessionId)),
                new ParameterizedTypeReference<ApiBody<UserBody>>() {}
        );

        if (user.id() <= 0 || !"USER".equals(user.accountType())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "请使用普通用户账号");
        }
        return user.id();
    }

    @Override
    public OrderPage searchMyOrders(
            long currentUserId,
            OrderQuery query,
            String sessionId
    ) {
        PageBody page = read(
                client.get()
                        .uri(builder -> ordersUri(builder, query))
                        .header(HttpHeaders.COOKIE, cookieHeader(sessionId)),
                new ParameterizedTypeReference<ApiBody<PageBody>>() {}
        );

        List<OrderSummary> orders = page.records().stream()
                .map(order -> {
                    // 保证会话查到的数据与 Agent 当前用户上下文一致。
                    if (order.userId() != currentUserId) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, "订单身份与当前会话不一致");
                    }
                    return new OrderSummary(
                            order.id(), order.eventTitle(), order.city(),
                            order.startTime(), order.status(), order.totalAmount()
                    );
                })
                .toList();

        return new OrderPage(orders, page.total(), page.page(), page.size());
    }

    private static URI ordersUri(UriBuilder builder, OrderQuery query) {
        builder.path("/user/orders");
        Map<String, Object> values = new LinkedHashMap<>();

        // 通过 URI 变量编码参数，保留中文、加号等原始查询内容。
        if (query.keyword() != null) {
            builder.queryParam("keyword", "{keyword}");
            values.put("keyword", query.keyword());
        }
        if (query.city() != null) {
            builder.queryParam("city", "{city}");
            values.put("city", query.city());
        }
        if (query.status() != null) {
            builder.queryParam("status", query.status());
        }
        builder.queryParam("page", query.page()).queryParam("size", query.size());
        return builder.build(values);
    }

    private <T> T read(
            RestClient.RequestHeadersSpec<?> request,
            ParameterizedTypeReference<ApiBody<T>> type
    ) {
        ApiBody<T> body = request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 401 || status == 403) {
                        throw new ResponseStatusException(
                                response.getStatusCode(), "登录已失效或无访问权限");
                    }
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "票务服务暂时不可用");
                })
                .body(type);

        // 调用失败不能转换成空列表，否则模型会误报“没有订单”。
        if (body == null || !"SUCCESS".equals(body.code()) || body.data() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "票务服务返回了异常结果");
        }
        return body.data();
    }

    private static String cookieHeader(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "请先登录票务平台");
        }
        return "JSESSIONID=" + sessionId;
    }

    private static RestClient createClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // 接口响应类型放在网关内部，只负责匹配后端结构，不额外拆分 DTO 文件。
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiBody<T>(String code, String message, T data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserBody(long id, String accountType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageBody(List<OrderBody> records, long total, int page, int size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderBody(
            long id,
            long userId,
            String eventTitle,
            String city,
            LocalDateTime startTime,
            int status,
            BigDecimal totalAmount
    ) {}
}

