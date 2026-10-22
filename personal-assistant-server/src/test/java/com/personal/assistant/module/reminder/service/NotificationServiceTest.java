package com.personal.assistant.module.reminder.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.reminder.entity.NotificationChannel;
import com.personal.assistant.module.reminder.entity.NotificationLog;
import com.personal.assistant.module.reminder.mapper.NotificationChannelMapper;
import com.personal.assistant.module.reminder.mapper.NotificationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void pushPlusPostsJsonWithTokenTitleAndTextTemplate() {
        NotificationChannelMapper channels = mock(NotificationChannelMapper.class);
        NotificationLogMapper logs = mock(NotificationLogMapper.class);
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationService service = new NotificationService(channels, logs, crypto, builder);
        String token = "0123456789abcdef0123456789abcdef";
        NotificationChannel channel = channel("PUSHPLUS");
        when(channels.selectById(3L)).thenReturn(channel);
        when(crypto.decrypt("encrypted")).thenReturn(token);
        server.expect(requestTo("https://www.pushplus.plus/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"token\":\"" + token + "\",\"title\":\"测试提醒\","
                        + "\"content\":\"### 今天\\n1. 开会\",\"template\":\"markdown\"}", true))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"请求成功\",\"data\":\"abc\"}",
                        MediaType.APPLICATION_JSON));

        service.send(7L, 9L, 3L, "测试提醒", "### 今天\n1. 开会");

        server.verify();
        verify(logs).insert(any(NotificationLog.class));
    }

    @Test
    void pushPlusTokenIsRecognizedAndUrlIsNot() {
        assertTrue(ReminderService.isPushPlusToken("0123456789abcdef0123456789abcdef"));
        assertFalse(ReminderService.isPushPlusToken("https://example.com/hook/abc"));
    }

    @Test
    void pushPlusNonSuccessCodeFailsTheSend() {
        NotificationChannelMapper channels = mock(NotificationChannelMapper.class);
        NotificationLogMapper logs = mock(NotificationLogMapper.class);
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationService service = new NotificationService(channels, logs, crypto, builder);
        NotificationChannel channel = channel("PUSHPLUS");
        when(channels.selectById(3L)).thenReturn(channel);
        when(crypto.decrypt("encrypted")).thenReturn("0123456789abcdef0123456789abcdef");
        server.expect(requestTo("https://www.pushplus.plus/send"))
                .andRespond(withSuccess("{\"code\":903,\"msg\":\"token不存在\"}", MediaType.APPLICATION_JSON));

        assertThrows(BusinessException.class, () -> service.send(7L, 9L, 3L, "测试提醒", "内容"));

        verify(logs).insert(any(NotificationLog.class));
    }

    @Test
    void webhookAdaptsPayloadForWecomAndDingTalkRobots() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc";
        assertWebhookBody(url, "{\"msgtype\":\"text\",\"text\":{\"content\":\"测试提醒\\n提醒内容\"}}");

        String dingtalk = "https://oapi.dingtalk.com/robot/send?access_token=abc";
        assertWebhookBody(dingtalk, "{\"msgtype\":\"text\",\"text\":{\"content\":\"测试提醒\\n提醒内容\"}}");
    }

    @Test
    void webhookAdaptsPayloadForFeishuRobot() {
        assertWebhookBody("https://open.feishu.cn/open-apis/bot/v2/hook/abc",
                "{\"msg_type\":\"text\",\"content\":{\"text\":\"测试提醒\\n提醒内容\"}}");
    }

    @Test
    void webhookKeepsGenericPayloadForOtherEndpoints() {
        assertWebhookBody("https://example.com/my-hook",
                "{\"title\":\"测试提醒\",\"text\":\"提醒内容\"}");
    }

    @Test
    void webhookStripsMarkdownForPlainTextChannels() {
        String url = "https://example.com/my-hook";
        NotificationChannelMapper channels = mock(NotificationChannelMapper.class);
        NotificationLogMapper logs = mock(NotificationLogMapper.class);
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationService service = new NotificationService(channels, logs, crypto, builder);
        when(channels.selectById(3L)).thenReturn(channel("WEBHOOK"));
        when(crypto.decrypt("encrypted")).thenReturn(url);
        server.expect(requestTo(url))
                .andExpect(content().json("{\"title\":\"测试提醒\",\"text\":\"今天\\n1. 开会 · 逾期 2 天\"}", true))
                .andRespond(withSuccess());

        service.send(7L, 9L, 3L, "测试提醒", "### 今天\n1. 开会 · **逾期 2 天**");

        server.verify();
    }

    private void assertWebhookBody(String url, String expectedJson) {
        NotificationChannelMapper channels = mock(NotificationChannelMapper.class);
        NotificationLogMapper logs = mock(NotificationLogMapper.class);
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationService service = new NotificationService(channels, logs, crypto, builder);
        when(channels.selectById(3L)).thenReturn(channel("WEBHOOK"));
        when(crypto.decrypt("encrypted")).thenReturn(url);
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedJson, true))
                .andRespond(withSuccess());

        service.send(7L, 9L, 3L, "测试提醒", "提醒内容");

        server.verify();
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
