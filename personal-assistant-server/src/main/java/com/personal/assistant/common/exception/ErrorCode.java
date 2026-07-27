package com.personal.assistant.common.exception;

/**
 * 业务错误码定义。code 用于前端判断，httpStatus 用于响应状态。
 */
public enum ErrorCode {

    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败", 400),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已过期", 401),
    FORBIDDEN("FORBIDDEN", "没有访问权限", 403),
    NOT_FOUND("NOT_FOUND", "资源不存在", 404),
    BUSINESS_ERROR("BUSINESS_ERROR", "业务处理失败", 400),
    INTEGRATION_ERROR("INTEGRATION_ERROR", "外部接口调用失败", 502),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
