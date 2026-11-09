package com.qiuzhen.damaiagent.ticketing;

import com.qiuzhen.damaiagent.agent.TicketingAgent;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Objects;

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
                    用于订单查询和操作前的订单定位。
                    返回当前页订单及匹配总数。
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
        Object value = toolContext.getContext().get(
                TicketingAgent.CURRENT_USER_ID
        );

        if (!(value instanceof Long userId) || userId <= 0) {
            throw new IllegalStateException("缺少可信的用户身份");
        }

        return ticketingGateway.searchMyOrders(
                userId,
                new OrderQuery(
                        keyword,
                        city,
                        status,
                        page == null ? 1 : page,
                        size == null ? 10 : size
                )
        );
    }
}