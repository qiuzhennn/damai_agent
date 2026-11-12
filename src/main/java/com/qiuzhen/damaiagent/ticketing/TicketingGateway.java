package com.qiuzhen.damaiagent.ticketing;

import java.util.List;

public interface TicketingGateway {

    /**
     * 查询本人订单。
     * 用户身份和登录会话由应用提供，不属于模型填写的工具参数。
     */
    OrderPage searchMyOrders(
            long currentUserId,
            OrderQuery query,
            String sessionId
    );

    record OrderPage(
            List<OrderSummary> records,
            long total,
            int page,
            int size
    ) {
        public OrderPage {
            records = List.copyOf(records);
        }

        public boolean hasNext() {
            return (long) page * size < total;
        }
    }
}

