package com.personal.assistant.module.auth.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.security.JwtTokenService;
import com.personal.assistant.module.auth.dto.ChangePasswordRequest;
import com.personal.assistant.module.auth.entity.UserAccount;
import com.personal.assistant.module.auth.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserAccountMapper users;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService jwtTokenService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, jwtTokenService);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        UserAccount user = user();
        when(users.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.changePassword(1L, new ChangePasswordRequest("wrong", "new-password")));
    }

    @Test
    void changePasswordStoresEncodedPassword() {
        UserAccount user = user();
        when(users.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.changePassword(1L, new ChangePasswordRequest("current-password", "new-password"));

        verify(users).updateById(user);
        org.junit.jupiter.api.Assertions.assertEquals("new-hash", user.getPasswordHash());
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setEnabled(true);
        user.setPasswordHash("old-hash");
        return user;
    }
}