package com.chargeplatform.common.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public record PageResponse<T>(List<T> rows, long total, int page, int size, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> data) {
        return new PageResponse<>(data.getRecords(), data.getTotal(), (int) data.getCurrent(), (int) data.getSize(), (int) data.getPages());
    }
}
