package com.personal.assistant.common.response;

import java.io.Serializable;

/**
 * 统一接口返回结构。
 *
 * @param code    业务状态码，SUCCESS 表示成功，其余为错误码
 * @param message 提示信息
 * @param data    业务数据
 * @param traceId 请求追踪 ID，便于排查问题
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) implements Serializable {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "操作成功";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, TraceIdHolder.current());
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, TraceIdHolder.current());
    }
}
