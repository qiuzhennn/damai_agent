package com.qiuzhen.damaiagent.ticketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单列表摘要。与原先的六字段构造兼容，增加前端已有的场馆等信息。 */
public record OrderSummary(
        long orderId,
        String eventTitle,
        String city,
        LocalDateTime startTime,
        int status,
        BigDecimal totalAmount,
        String venue,
        String ticketCategoryName,
        Integer quantity
) {
    public OrderSummary(long orderId, String eventTitle, String city,
                        LocalDateTime startTime, int status, BigDecimal totalAmount) {
        this(orderId, eventTitle, city, startTime, status, totalAmount, null, null, null);
    }
}