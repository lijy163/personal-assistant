package com.personal.assistant.module.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Service
public class WeComMessageService {
    private final WeComProperties properties;
    private final RestClient client;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public WeComMessageService(WeComProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    public void sendText(String userId, String content) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) return;
        JsonNode response = client.post()
                .uri("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}", token())
                .body(Map.of("touser", userId, "msgtype", "text", "agentid", properties.getAgentId(),
                        "text", Map.of("content", truncate(content, 2000)), "safe", 0))
                .retrieve().body(JsonNode.class);
        if (response == null || response.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("企业微信消息发送失败：" + response);
        }
    }

    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(expiresAt)) return accessToken;
        JsonNode response = client.get().uri("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpId}&corpsecret={secret}",
                        properties.getCorpId(), properties.getSecret()).retrieve().body(JsonNode.class);
        if (response == null || response.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("获取企业微信 access_token 失败：" + response);
        }
        accessToken = response.path("access_token").asText();
        expiresAt = Instant.now().plusSeconds(Math.max(60, response.path("expires_in").asLong(7200) - 300));
        return accessToken;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n...[内容已截断]";
    }
}
