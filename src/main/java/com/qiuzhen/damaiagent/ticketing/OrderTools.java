package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Objects;

/**
 * 暴露给模型的订单查询工具。
 * 模型提供筛选条件，应用通过 ToolContext 传递当前用户身份。
 */
public class OrderTools {

    private final TicketingGateway ticketingGateway;

    public OrderTools(TicketingGateway ticketingGateway) {
        this.ticketingGateway =
                Objects.requireNonNull(ticketingGateway);
    }

    @Tool(
            name = "searchMyOrders",
            description = """
                    查询当前登录用户的订单。
                    支持演出名称、城市、订单状态筛选和分页。
                    用于订单查询，也用于操作引导时查找相关订单。
                    返回当前页订单摘要及匹配总数。
                    如果用户询问具体订单的详细信息、场馆、票档或支付截止时间，
                    应根据订单编号继续使用 getMyOrderDetail 查询，不要猜测缺失字段。
                    用户没有提供的筛选条件应留空。
                    """
    )
    public TicketingGateway.OrderPage searchMyOrders(
            @ToolParam(
                    description = "演出名称关键词，未提供时留空",
                    required = false
            )
            String keyword,

            @ToolParam(
                    description = "城市，例如上海；未指定时留空",
                    required = false
            )
            String city,

            @ToolParam(
                    description = """
                            订单状态：0待支付、1已支付、2已取消、3已超时。
                            未付款对应0。未指定状态时留空。
                            """,
                    required = false
            )
            Integer status,

            @ToolParam(
                    description = "页码，从1开始；未指定时留空",
                    required = false
            )
            Integer page,

            @ToolParam(
                    description = "每页数量，1到20；未指定时留空",
                    required = false
            )
            Integer size,

            ToolContext toolContext
    ) {
        long userId = authenticatedUserId(toolContext);
        String sessionId = authenticatedSession(toolContext);

        // 默认页码和数量由程序处理，参数合法性由 OrderQuery 统一校验。
        return ticketingGateway.searchMyOrders(
                userId,
                new OrderQuery(
                        keyword,
                        city,
                        status,
                        page == null ? 1 : page,
                        size == null ? 10 : size
                ),
                sessionId
        );
    }

    @Tool(
            name = "getMyOrderDetail",
            description = """
                    根据订单编号查询当前登录用户的订单详情。
                    获取真实场馆、演出时间、票档、数量、单价、总价、创建时间、
                    支付截止时间和订单状态。用户询问单笔订单的具体信息时使用。
                    只能用用户消息或先前订单列表的真实订单编号，不得编造。
                    """
    )
    public OrderDetail getMyOrderDetail(
            @ToolParam(description = "需要查询详情的订单编号") long orderId,
            ToolContext toolContext
    ) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("订单编号必须大于0");
        }
        return ticketingGateway.getMyOrderDetail(
                authenticatedUserId(toolContext), orderId, authenticatedSession(toolContext));
    }

    private static long authenticatedUserId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(TicketingAgent.CURRENT_USER_ID);
        if (!(value instanceof Long userId) || userId <= 0) {
            throw new IllegalStateException("缺少可信的用户身份");
        }
        return userId;
    }

    private static String authenticatedSession(ToolContext toolContext) {
        Object value = toolContext.getContext().get(TicketingAgent.SESSION_ID);
        return value instanceof String text ? text : null;
    }
}
