package com.personal.assistant.module.devlog.service;

import com.personal.assistant.module.devlog.dto.PatCreateRequest;
import com.personal.assistant.module.devlog.entity.PersonalAccessToken;
import com.personal.assistant.module.devlog.mapper.PersonalAccessTokenMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalAccessTokenServiceTest {
    @Mock PersonalAccessTokenMapper mapper;

    @Test
    void createReturnsRawTokenButStoresOnlyHash() {
        when(mapper.insert(any(PersonalAccessToken.class))).thenAnswer(invocation -> {
            PersonalAccessToken token = invocation.getArgument(0); token.setId(3L); return 1;
        });
        PersonalAccessTokenService service = new PersonalAccessTokenService(mapper);
        var created = service.create(1L, new PatCreateRequest("Codex", null));
        assertTrue(created.token().startsWith(PersonalAccessTokenService.TOKEN_PREFIX));
    }

    @Test
    void expiredTokenCannotAuthenticate() {
        PersonalAccessToken expired = new PersonalAccessToken();
        expired.setScope(PersonalAccessTokenService.DEVLOG_WRITE_SCOPE);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectOne(any())).thenReturn(expired);
        PersonalAccessTokenService service = new PersonalAccessTokenService(mapper);
        assertNull(service.authenticate("pa_pat_expired"));
    }
}
