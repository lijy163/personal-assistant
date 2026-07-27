package com.personal.assistant.common.response;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * 请求级 traceId 持有器，基于 SLF4J MDC 实现，便于日志串联和返回给前端。
 */
public final class TraceIdHolder {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdHolder() {
    }

    public static String generate() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID_KEY, traceId);
        return traceId;
    }

    public static String current() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
