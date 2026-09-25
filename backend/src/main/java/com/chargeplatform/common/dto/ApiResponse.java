package com.chargeplatform.common.dto;

import java.time.LocalDateTime;

/**
 * 所有业务接口统一使用的响应结构。
 *
 * @param code HTTP 语义对应的业务码
 * @param message 面向调用方的结果说明
 * @param data 实际业务数据，失败或无返回数据时为 null
 * @param timestamp 响应生成时间
 */
public record ApiResponse<T>(
    int code,
    String message,
    T data,
    LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "操作成功", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "创建成功", data, LocalDateTime.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(200, message, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }
}
