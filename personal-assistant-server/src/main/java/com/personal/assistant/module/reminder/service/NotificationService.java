package com.personal.assistant.module.reminder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.entity.NotificationLog;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.mapper.NotificationLogMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationService {
    static final String SERVER_CHAN = "SERVER_CHAN";
    static final String PUSHPLUS = "PUSHPLUS";
    static final String PUSHPLUS_ENDPOINT = "https://www.pushplus.plus/send";

    private final NotificationChannelMapper channels;
    private final NotificationLogMapper logs;
    private final SecretCryptoService crypto;
    private final RestClient client;

    public NotificationService(NotificationChannelMapper channels, NotificationLogMapper logs,
                               SecretCryptoService crypto, RestClient.Builder clientBuilder) {
        this.channels = channels;
        this.logs = logs;
        this.crypto = crypto;
        this.client = clientBuilder.build();
    }

    public void send(Long userId, Long reminderId, Long channelId, String title, String content) {
        NotificationChannel channel = channels.selectById(channelId);
        if (channel == null || !userId.equals(channel.getUserId()) || !Boolean.TRUE.equals(channel.getEnabled())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "通知通道不可用");
        }
        boolean success = false;
        String message = "发送成功";
        try {
            String target = crypto.decrypt(channel.getWebhookEncrypted());
            if (SERVER_CHAN.equals(channel.getChannelType()) || ReminderService.isServerChanWebhook(target)) {
                sendServerChan(target, title, content);
            } else if (PUSHPLUS.equals(channel.getChannelType()) || ReminderService.isPushPlusToken(target)) {
                sendPushPlus(target, title, content);
            } else {
                sendWebhook(target, title, content);
            }
            success = true;
        } catch (Exception exception) {
            message = exception.getMessage();
            throw new BusinessException(ErrorCode.INTEGRATION_ERROR, "通知发送失败：" + safeMessage(exception));
        } finally {
            NotificationLog log = new NotificationLog();
            log.setUserId(userId);
            log.setReminderId(reminderId);
            log.setChannelId(channelId);
            log.setSuccess(success);
            log.setMessage(message);
            log.setCreatedAt(LocalDateTime.now());
            logs.insert(log);
        }
    }

    private void sendServerChan(String url, String title, String content) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", title);
        form.add("desp", content == null || content.isBlank() ? title : content);
        client.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().toBodilessEntity();
    }

    /**
     * PushPlus 用 JSON 提交，token 直接放在 body 里；template 固定 txt，
     * 这样摘要里的换行和【】分组标记不会被当成 HTML 转义掉。
     */
    private void sendPushPlus(String token, String title, String content) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("token", token.trim());
        form.put("title", title);
        form.put("content", content == null || content.isBlank() ? title : content);
        form.put("template", "markdown");
        JsonNode response = client.post().uri(PUSHPLUS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).body(form)
                .retrieve().body(JsonNode.class);
        if (response == null || response.path("code").asInt(-1) != 200) {
            throw new IllegalStateException("PushPlus 发送失败：" + response);
        }
    }

    /**
     * 通用 Webhook 按目标站点自动切换报文格式。
     *
     * <p>企业微信、钉钉、飞书的群机器人都只接受自己的固定结构，收到 {title,text}
     * 会直接返回参数错误——这正是「通道配好了却收不到消息」最常见的原因。
     * 机器人文本消息没有独立标题字段，所以标题合并进正文首行。</p>
     */
    private void sendWebhook(String url, String title, String content) {
        String text = stripMarkdown(content == null || content.isBlank() ? title : content);
        String host = hostOf(url);
        Object body;
        if (host.contains("qyapi.weixin.qq.com") || host.contains("oapi.dingtalk.com")) {
            body = Map.of("msgtype", "text", "text", Map.of("content", withTitle(title, text)));
        } else if (host.contains("open.feishu.cn") || host.contains("open.larksuite.com")) {
            body = Map.of("msg_type", "text", "content", Map.of("text", withTitle(title, text)));
        } else {
            body = Map.of("title", title, "text", text);
        }
        client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
    }

    /**
     * 群机器人的 text 消息是纯文本，Markdown 标记会原样显示成星号和井号，
     * 这里去掉标题层级和加粗，保留换行与编号。
     */
    private String stripMarkdown(String text) {
        return text.replaceAll("(?m)^#{1,6}\\s*", "").replace("**", "");
    }

    private String withTitle(String title, String text) {
        String merged = title == null || title.isBlank() ? text : title + "\n" + text;
        // 企业微信文本消息上限 2048 字节，中文按 3 字节估算，保守截到 600 字。
        return merged.length() <= 600 ? merged : merged.substring(0, 597) + "...";
    }

    private String hostOf(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}