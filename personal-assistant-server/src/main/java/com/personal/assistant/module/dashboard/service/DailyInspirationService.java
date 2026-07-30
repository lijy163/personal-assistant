package com.personal.assistant.module.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.dashboard.dto.DailyInspirationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DailyInspirationService {
    private static final String URL = "https://api.t1qq.com/api/tool/daytry?time=today&key=";
    private static final String FALLBACK = "今天也把重要的事情放到系统里";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final String apiKey;
    private volatile DailyInspirationResponse cached;

    public DailyInspirationService(ObjectMapper objectMapper,
                                   @Value("${JEWELRY_GOLD_API_KEY:}") String apiKey) {
        this(objectMapper, RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 PersonalAssistant/1.0").build(), apiKey);
    }

    DailyInspirationService(ObjectMapper objectMapper, RestClient client, String apiKey) {
        this.objectMapper = objectMapper;
        this.client = client;
        this.apiKey = apiKey;
    }

    public DailyInspirationResponse today() {
        LocalDate today = LocalDate.now(SHANGHAI);
        DailyInspirationResponse current = cached;
        if (current != null && today.equals(current.date())) return current;
        synchronized (this) {
            current = cached;
            if (current != null && today.equals(current.date())) return current;
            cached = load(today);
            return cached;
        }
    }

    private DailyInspirationResponse load(LocalDate today) {
        if (apiKey == null || apiKey.isBlank()) return fallback(today);
        try {
            String key = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String body = client.get().uri(URL + key).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt() != 200) return fallback(today);
            JsonNode data = root.path("data");
            String content = data.path("content").asText("").trim();
            String translation = data.path("note").asText("").trim();
            String imageUrl = data.path("picture").asText(data.path("img").asText("")).trim();
            if (content.isEmpty() && translation.isEmpty()) return fallback(today);
            return new DailyInspirationResponse(content, translation, imageUrl, today, "应天API");
        } catch (Exception ignored) {
            return fallback(today);
        }
    }

    private DailyInspirationResponse fallback(LocalDate today) {
        return new DailyInspirationResponse("", FALLBACK, "", today, "系统默认");
    }
}
