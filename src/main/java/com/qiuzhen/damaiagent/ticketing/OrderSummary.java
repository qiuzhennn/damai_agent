package com.qiuzhen.damaiagent.ticketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummary(
        long orderId,
        String eventTitle,
        String city,
        LocalDateTime startTime,
        int status,
        BigDecimal totalAmount
) {
}