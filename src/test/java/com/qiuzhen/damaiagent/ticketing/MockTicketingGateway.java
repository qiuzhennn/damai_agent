package com.qiuzhen.damaiagent.ticketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class MockTicketingGateway implements TicketingGateway {

    private final Map<Long, List<OrderSummary>> ordersByUser = Map.of(
            1001L, List.of(
                    order(101L, "上海周末音乐会", "上海", 0, "380.00"),
                    order(102L, "上海城市话剧", "上海", 0, "180.00"),
                    order(103L, "北京交响音乐会", "北京", 1, "680.00"),
                    order(104L, "上海脱口秀", "上海", 2, "280.00")
            ),
            1002L, List.of(
                    order(201L, "上海独立音乐会", "上海", 0, "480.00")
            )
    );

    @Override
    public OrderPage searchMyOrders(
            long currentUserId,
            OrderQuery query,
            String sessionId
    ) {
        if (currentUserId <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0");
        }

        Objects.requireNonNull(query, "查询条件不能为空");

        // 先限定用户，再处理筛选条件。
        List<OrderSummary> userOrders =
                ordersByUser.getOrDefault(
                        currentUserId,
                        List.of()
                );

        List<OrderSummary> matched = userOrders.stream()
                .filter(order ->
                        query.status() == null
                                || order.status() == query.status()
                )
                .filter(order ->
                        query.city() == null
                                || query.city().equals(order.city())
                )
                .filter(order ->
                        query.keyword() == null
                                || order.eventTitle()
                                .contains(query.keyword())
                )
                .sorted(
                        Comparator.comparingLong(
                                OrderSummary::orderId
                        ).reversed()
                )
                .toList();

        // 先完成筛选，再分页。
        long offset =
                ((long) query.page() - 1) * query.size();

        List<OrderSummary> records = matched.stream()
                .skip(offset)
                .limit(query.size())
                .toList();

        return new OrderPage(
                records,
                matched.size(),
                query.page(),
                query.size()
        );
    }

    private static OrderSummary order(
            long orderId,
            String title,
            String city,
            int status,
            String amount
    ) {
        return new OrderSummary(
                orderId,
                title,
                city,
                LocalDateTime.of(2026, 11, 15, 19, 30),
                status,
                new BigDecimal(amount)
        );
    }
}
