package com.qiuzhen.damaiagent.ticketing;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Objects;

/** 演出、分类、场次和票档查询。全部只读，不修改库存、不执行下单。 */
public class EventTools {

    private final EventGateway gateway;

    public EventTools(EventGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    @Tool(name = "searchEvents", description = """
            查询当前已公开的演出列表，支持关键词、城市、分类编号和分页。
            关键词可以是演出名称或艺人名称；没有条件时留空，不要猜测。
            仅用 listEventCategories 查询到的真实分类编号进行分类筛选。
            返回的演出列表不代表已按演出日期排序；如需确认具体日期，继续查询场次。
            """)
    public EventGateway.EventPage searchEvents(
            @ToolParam(description = "演出名称或艺人关键词，未指定留空", required = false)
            String keyword,
            @ToolParam(description = "举办城市，例如北京，未指定留空", required = false)
            String city,
            @ToolParam(description = "分类编号，只能使用 listEventCategories 查询到的编号，未确定留空", required = false)
            Long categoryId,
            @ToolParam(description = "页码从1开始，未指定留空", required = false)
            Integer page,
            @ToolParam(description = "每页1到20条，未指定留空", required = false)
            Integer size
    ) {
        return gateway.searchEvents(new EventQuery(
                keyword, city, categoryId,
                page == null ? 1 : page,
                size == null ? 10 : size));
    }

    @Tool(name = "listEventCategories", description = """
            获取平台真实的公开演出分类及其编号。例如用户问演唱会、音乐节或话剧时，
            可先查询分类，再将返回的 categoryId 传给 searchEvents；不要凭空猜分类编号。
            """)
    public List<EventCategorySummary> listEventCategories() {
        return gateway.listEventCategories();
    }

    @Tool(name = "getEventDetail", description = """
            根据真实演出编号查询名称、艺人、城市及场馆名称等演出详情。
            只使用 searchEvents 结果或用户明确给出的演出编号。
            若只返回场馆名称，不能猜测详细街道地址。
            """)
    public EventSummary getEventDetail(
            @ToolParam(description = "真实演出编号") long eventId) {
        requirePositiveId(eventId, "演出编号");
        return gateway.getEventDetail(eventId);
    }

    @Tool(name = "getEventSessions", description = """
            查询某个演出的场次、开演时间、开售时间及场次状态。
            用户询问最近、下周、什么时候开售或第几个场次时使用。
            必须使用真实演出编号，不能根据城市推测开演时间。
            """)
    public List<EventSessionSummary> getEventSessions(
            @ToolParam(description = "真实演出编号") long eventId) {
        requirePositiveId(eventId, "演出编号");
        return gateway.getEventSessions(eventId);
    }

    @Tool(name = "getSessionTicketCategories", description = """
            查询指定场次的票档名称、单价和查询瞬间的公开可用库存。
            只能使用 getEventSessions 中真实返回的场次编号。
            余票可能随时变化，查询结果不是锁票成功或购票保证。
            """)
    public List<TicketCategorySummary> getSessionTicketCategories(
            @ToolParam(description = "真实场次编号") long sessionId) {
        requirePositiveId(sessionId, "场次编号");
        return gateway.getSessionTicketCategories(sessionId);
    }

    private static void requirePositiveId(long id, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
    }
}
