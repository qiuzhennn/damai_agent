package com.qiuzhen.damaiagent.agent;

import com.qiuzhen.damaiagent.ticketing.OrderSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 文字由模型组织；订单卡片直接使用本次工具查询的原始数据。 */
public record ChatReply(String reply, List<OrderSummary> orders) {

    public ChatReply {
        orders = List.copyOf(orders);
    }

    /**
     * 每次聊天请求单独创建，通过 ToolContext 交给工具。
     * 不放在单例工具的成员变量中，避免不同用户、不同轮次的结果混在一起。
     */
    public static final class OrderCards {
        public static final String CONTEXT_KEY = OrderCards.class.getName();

        private final Map<Long, OrderSummary> orders = new LinkedHashMap<>();

        public void addAll(List<OrderSummary> results) {
            // 同一轮可能先查列表再查详情；保留顺序，用最新结果更新同一订单。
            for (OrderSummary order : results) {
                orders.put(order.orderId(), order);
            }
        }

        public List<OrderSummary> snapshot() {
            return List.copyOf(orders.values());
        }
    }
}
