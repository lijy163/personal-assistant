package com.personal.assistant.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性，对应 application.yml 中的 jwt 节点。
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥，生产环境必须通过环境变量覆盖，长度不少于 32 字节。
     */
    private String secret;

    /**
     * 令牌有效期，单位分钟。
     */
    private long expirationMinutes = 720;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
