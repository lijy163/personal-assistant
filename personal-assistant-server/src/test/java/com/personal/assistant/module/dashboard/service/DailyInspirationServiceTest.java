package com.personal.assistant.module.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyInspirationServiceTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void returnsAndCachesTodayInspiration() {
        RestClient client = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headers = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(client.get()).thenReturn(request);
        when(request.uri(anyString())).thenReturn(headers);
        when(headers.retrieve()).thenReturn(response);
        when(response.body(String.class)).thenReturn("{\"code\":200,\"data\":{\"content\":\"Keep going.\",\"note\":\"继续前行。\",\"picture\":\"https://example.com/a.jpg\"}}");
        DailyInspirationService service = new DailyInspirationService(new ObjectMapper(), client, "test-key");

        var first = service.today();
        var second = service.today();

        assertEquals("继续前行。", first.translation());
        assertEquals("Keep going.", first.content());
        assertEquals(first, second);
        verify(client, times(1)).get();
    }

    @Test
    void fallsBackWithoutKey() {
        DailyInspirationService service = new DailyInspirationService(new ObjectMapper(), mock(RestClient.class), "");
        assertEquals("今天也把重要的事情放到系统里", service.today().translation());
    }
}
