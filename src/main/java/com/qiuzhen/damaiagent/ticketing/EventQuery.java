package com.qiuzhen.damaiagent.ticketing;

/** 演出列表筛选条件；全部来自公开检索，不包含用户身份。 */
public record EventQuery(
        String keyword,
        String city,
        Long categoryId,
        int page,
        int size
) {
    public EventQuery {
        keyword = trimToNull(keyword);
        city = trimToNull(city);
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("搜索关键词不能超过100字");
        }
        if (city != null && city.length() > 50) {
            throw new IllegalArgumentException("城市名称不能超过50字");
        }
        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException("演出分类编号必须大于0");
        }
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于0");
        }
        if (size < 1 || size > 20) {
            throw new IllegalArgumentException("每页数量必须在1到20之间");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
