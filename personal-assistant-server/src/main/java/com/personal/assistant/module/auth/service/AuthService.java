package com.personal.assistant.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.common.security.JwtTokenService;
import com.personal.assistant.module.auth.dto.LoginRequest;
import com.personal.assistant.module.auth.dto.LoginResponse;
import com.personal.assistant.module.auth.entity.UserAccount;
import com.personal.assistant.module.auth.mapper.UserAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserAccountMapper userAccountMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getUsername, request.username())
                        .eq(UserAccount::getEnabled, true));

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登录失败，用户名: {}", request.username());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        user.setLastLoginTime(LocalDateTime.now());
        userAccountMapper.updateById(user);

        String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
        log.info("用户登录成功，userId: {}", user.getId());
        return new LoginResponse(token, user.getUsername(), user.getDisplayName(), user.getId());
    }
}
