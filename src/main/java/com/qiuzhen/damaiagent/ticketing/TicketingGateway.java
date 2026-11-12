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

    /**
     * 查询本人单笔订单详情。调用方必须传入已经认证的登录会话。
     * 默认实现保留原有测试中 lambda 网关的兼容性。
     */
    default OrderDetail getMyOrderDetail(
            long currentUserId, long orderId, String sessionId
    ) {
        throw new UnsupportedOperationException("当前网关未实现订单详情查询");
    }

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

