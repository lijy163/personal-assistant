package com.personal.assistant.module.gold.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicGoldQuoteServiceTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void convertsUsdPerOunceToCnyPerGram() {
        RestClient client = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headers = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(client.get()).thenReturn(request);
        when(request.uri(anyString())).thenReturn(headers);
        when(headers.retrieve()).thenReturn(response);
        when(response.body(String.class)).thenReturn(
                "{\"price\":3000,\"updatedAt\":\"2026-07-30T01:00:00Z\"}",
                "{\"rates\":{\"CNY\":7.2}}"
        );

        var result = new PublicGoldQuoteService(new ObjectMapper(), client).latest();

        assertEquals(2, result.quotes().size());
        assertEquals(new BigDecimal("7.2000"), result.usdCny());
        assertEquals(new BigDecimal("694.4561"), result.quotes().get(1).price());
        assertTrue(result.quotes().get(1).converted());
    }
}
