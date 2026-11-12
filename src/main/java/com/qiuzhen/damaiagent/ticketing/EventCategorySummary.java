package com.qiuzhen.damaiagent.ticketing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventCategorySummary(long id, String name) {}
