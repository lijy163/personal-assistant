package com.personal.assistant.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证访问受保护资源时返回统一 JSON 结构，而非默认登录跳转。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Object> body = ApiResponse.error(
                ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.defaultMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
