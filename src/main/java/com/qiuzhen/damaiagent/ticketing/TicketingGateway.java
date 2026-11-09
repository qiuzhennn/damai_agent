package com.qiuzhen.damaiagent.ticketing;

import java.util.List;

public interface TicketingGateway {

    /**
     * 查询当前用户的订单。
     *
     * currentUserId 由服务端认证上下文提供。
     */
    OrderPage searchMyOrders(
            long currentUserId,
            OrderQuery query
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