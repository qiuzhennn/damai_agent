package com.qiuzhen.damaiagent.ticketing;

public record OrderQuery(
        String keyword,
        String city,
        Integer status,
        int page,
        int size
) {
    public OrderQuery {
        keyword = trimToNull(keyword);
        city = trimToNull(city);

        if (status != null && (status < 0 || status > 3)) {
            throw new IllegalArgumentException(
                    "订单状态只能是0、1、2、3"
            );
        }

        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于0");
        }

        if (size < 1 || size > 20) {
            throw new IllegalArgumentException(
                    "每页数量必须在1到20之间"
            );
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.strip();
    }
}