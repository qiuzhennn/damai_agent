package com.qiuzhen.damaiagent.ticketing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 演出公开信息。venue 表示场馆名称，并不保证包含街道门牌地址。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventSummary(
        long id,
        String title,
        String artist,
        String city,
        String venue,
        Long categoryId,
        Integer status
) {}
