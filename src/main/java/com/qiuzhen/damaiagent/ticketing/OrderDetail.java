package com.qiuzhen.damaiagent.ticketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 只读订单详情，字段与仿大麦前端的 /user/orders/{id} 详情页一致。 */
public record OrderDetail(
        long orderId,
        String eventTitle,
        String city,
        String venue,
        LocalDateTime startTime,
        String ticketCategoryName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        int status,
        LocalDateTime createdAt,
        LocalDateTime expireTime,
        LocalDateTime paidAt,
        LocalDateTime updatedAt
) {}
