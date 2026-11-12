package com.qiuzhen.damaiagent.ticketing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** 公开票档与查询瞬间的余票；库存可能随时变化。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketCategorySummary(
        long id,
        Long sessionId,
        String name,
        BigDecimal price,
        Long availableStock
) {}
