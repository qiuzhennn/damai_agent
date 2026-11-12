package com.qiuzhen.damaiagent.ticketing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventSessionSummary(
        long id,
        Long eventId,
        LocalDateTime startTime,
        LocalDateTime saleStartTime,
        Integer status
) {}
