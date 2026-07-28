package com.personal.assistant.module.reminder.service;

import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.entity.NotificationLog;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.mapper.NotificationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationServiceTest {
    @Test
    void serverChanUsesFormEncodedTitleAndDescription() {
        NotificationChannelMapper channels = mock(NotificationChannelMapper.class);
        NotificationLogMapper logs = mock(NotificationLogMapper.class);
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationService service = new NotificationService(channels, logs, crypto, builder);
        String url = "https://sctapi.ftqq.com/SCT-test.send";
        NotificationChannel channel = channel("SERVER_CHAN");
        when(channels.selectById(3L)).thenReturn(channel);
        when(crypto.decrypt("encrypted")).thenReturn(url);
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("title=%E6%B5%8B%E8%AF%95%E6%8F%90%E9%86%92&desp=%E6%8F%90%E9%86%92%E5%86%85%E5%AE%B9"))
                .andRespond(withSuccess());

        service.send(7L, 9L, 3L, "测试提醒", "提醒内容");

        server.verify();
        verify(logs).insert(any(NotificationLog.class));
    }

    @Test
    void recognizesOnlyOfficialHttpsServerChanWebhook() {
        assertTrue(ReminderService.isServerChanWebhook("https://sctapi.ftqq.com/SCT-test.send"));
    }

    private NotificationChannel channel(String type) {
        NotificationChannel channel = new NotificationChannel();
        channel.setId(3L);
        channel.setUserId(7L);
        channel.setChannelType(type);
        channel.setWebhookEncrypted("encrypted");
        channel.setEnabled(true);
        return channel;
    }
}
