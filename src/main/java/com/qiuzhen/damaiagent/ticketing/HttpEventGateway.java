package com.qiuzhen.damaiagent.ticketing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过仿大麦业务后端公开接口读取演出信息，不携带 JSESSIONID。
 * 与 HttpTicketingGateway 分开，避免将公开查询与私人订单权限混用。
 */
public class HttpEventGateway implements EventGateway {

    private final RestClient client;

    public HttpEventGateway(String baseUrl) {
        this(createClient(baseUrl));
    }

    public HttpEventGateway(RestClient client) {
        this.client = client;
    }

    @Override
    public EventPage searchEvents(EventQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("演出查询条件不能为空");
        }
        EventPageBody result = read(
                client.get().uri(builder -> eventsUri(builder, query)),
                new ParameterizedTypeReference<ApiBody<EventPageBody>>() {});
        if (result.records() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "票务服务未返回演出列表");
        }
        return new EventPage(result.records(), result.total(),
                result.page(), result.size());
    }

    @Override
    public EventSummary getEventDetail(long eventId) {
        requirePositiveId(eventId, "演出编号");
        EventSummary event = read(
                client.get().uri("/public/events/{id}", eventId),
                new ParameterizedTypeReference<ApiBody<EventSummary>>() {});
        if (event.id() != eventId) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "票务服务返回的演出编号不匹配");
        }
        return event;
    }

    @Override
    public List<EventCategorySummary> listEventCategories() {
        return List.copyOf(read(
                client.get().uri("/public/event-categories"),
                new ParameterizedTypeReference<ApiBody<List<EventCategorySummary>>>() {}));
    }

    @Override
    public List<EventSessionSummary> getEventSessions(long eventId) {
        requirePositiveId(eventId, "演出编号");
        return List.copyOf(read(
                client.get().uri(builder -> builder
                        .path("/public/event-sessions")
                        .queryParam("eventId", eventId)
                        .build()),
                new ParameterizedTypeReference<ApiBody<List<EventSessionSummary>>>() {}));
    }

    @Override
    public List<TicketCategorySummary> getSessionTicketCategories(long sessionId) {
        requirePositiveId(sessionId, "场次编号");
        return List.copyOf(read(
                client.get().uri(builder -> builder
                        .path("/public/ticket-categories")
                        .queryParam("sessionId", sessionId)
                        .build()),
                new ParameterizedTypeReference<ApiBody<List<TicketCategorySummary>>>() {}));
    }

    private static URI eventsUri(UriBuilder builder, EventQuery query) {
        builder.path("/public/events");
        Map<String, Object> values = new LinkedHashMap<>();
        if (query.keyword() != null) {
            builder.queryParam("keyword", "{keyword}");
            values.put("keyword", query.keyword());
        }
        if (query.city() != null) {
            builder.queryParam("city", "{city}");
            values.put("city", query.city());
        }
        if (query.categoryId() != null) {
            builder.queryParam("categoryId", query.categoryId());
        }
        builder.queryParam("page", query.page());
        builder.queryParam("size", query.size());
        return builder.build(values);
    }

    private <T> T read(RestClient.RequestHeadersSpec<?> request,
                       ParameterizedTypeReference<ApiBody<T>> type) {
        ApiBody<T> body = request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "演出或场次不存在");
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "公开演出服务暂时不可用");
                })
                .body(type);
        if (body == null || !"SUCCESS".equals(body.code()) || body.data() == null) {
            // 不能把业务失败伪装成“没有演出”或“没有余票”。
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "公开演出服务返回了异常结果");
        }
        return body.data();
    }

    private static void requirePositiveId(long id, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiBody<T>(String code, String message, T data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EventPageBody(List<EventSummary> records, long total, int page, int size) {}
}
