package com.personal.assistant.module.devlog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.devlog.dto.PatCreateRequest;
import com.personal.assistant.module.devlog.dto.PatCreateResponse;
import com.personal.assistant.module.devlog.dto.PatSummary;
import com.personal.assistant.module.devlog.entity.PersonalAccessToken;
import com.personal.assistant.module.devlog.mapper.PersonalAccessTokenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class PersonalAccessTokenService {
    public static final String TOKEN_PREFIX = "pa_pat_";
    public static final String DEVLOG_WRITE_SCOPE = "devlog:write";

    private final PersonalAccessTokenMapper tokenMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalAccessTokenService(PersonalAccessTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    @Transactional
    public PatCreateResponse create(Long userId, PatCreateRequest request) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime now = LocalDateTime.now();
        PersonalAccessToken token = new PersonalAccessToken();
        token.setUserId(userId);
        token.setName(request.name().trim());
        token.setTokenPrefix(rawToken.substring(0, 14));
        token.setTokenHash(hash(rawToken));
        token.setScope(DEVLOG_WRITE_SCOPE);
        token.setExpiresAt(request.expiresAt());
        token.setCreatedAt(now);
        tokenMapper.insert(token);
        return new PatCreateResponse(token.getId(), token.getName(), rawToken, token.getScope(),
                token.getExpiresAt(), token.getCreatedAt());
    }

    public List<PatSummary> list(Long userId) {
        return tokenMapper.selectList(new LambdaQueryWrapper<PersonalAccessToken>()
                        .eq(PersonalAccessToken::getUserId, userId)
                        .orderByDesc(PersonalAccessToken::getCreatedAt))
                .stream().map(this::summary).toList();
    }

    @Transactional
    public void revoke(Long userId, Long id) {
        PersonalAccessToken token = tokenMapper.selectById(id);
        if (token == null || !token.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "访问令牌不存在");
        }
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now());
            tokenMapper.updateById(token);
        }
    }

    @Transactional
    public PersonalAccessToken authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        PersonalAccessToken token = tokenMapper.selectOne(new LambdaQueryWrapper<PersonalAccessToken>()
                .eq(PersonalAccessToken::getTokenHash, hash(rawToken)));
        LocalDateTime now = LocalDateTime.now();
        if (token == null || token.getRevokedAt() != null
                || (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now))
                || !DEVLOG_WRITE_SCOPE.equals(token.getScope())) {
            return null;
        }
        token.setLastUsedAt(now);
        tokenMapper.updateById(token);
        return token;
    }

    private PatSummary summary(PersonalAccessToken token) {
        return new PatSummary(token.getId(), token.getName(), token.getTokenPrefix(), token.getScope(),
                token.getExpiresAt(), token.getLastUsedAt(), token.getRevokedAt(), token.getCreatedAt());
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
