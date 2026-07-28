package com.personal.assistant.module.reminder.service;

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
import java.util.Map;

@Service
public class NotificationService {
    static final String SERVER_CHAN = "SERVER_CHAN";

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
            String url = crypto.decrypt(channel.getWebhookEncrypted());
            if (SERVER_CHAN.equals(channel.getChannelType()) || ReminderService.isServerChanWebhook(url)) {
                sendServerChan(url, title, content);
            } else {
                sendWebhook(url, title, content);
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

    private void sendWebhook(String url, String title, String content) {
        client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "text", content == null ? title : content))
                .retrieve().toBodilessEntity();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}