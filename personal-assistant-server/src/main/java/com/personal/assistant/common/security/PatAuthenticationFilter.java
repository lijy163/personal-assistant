package com.personal.assistant.common.security;

import com.personal.assistant.module.devlog.entity.PersonalAccessToken;
import com.personal.assistant.module.devlog.service.PersonalAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class PatAuthenticationFilter extends OncePerRequestFilter {
    private static final String INGEST_PATH = "/api/devlogs/ingest";
    private static final String BEARER_PREFIX = "Bearer ";
    private final PersonalAccessTokenService tokenService;

    public PatAuthenticationFilter(PersonalAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INGEST_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            PersonalAccessToken token = tokenService.authenticate(header.substring(BEARER_PREFIX.length()));
            if (token != null) {
                AuthUser principal = new AuthUser(token.getUserId(), "pat:" + token.getName());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(token.getScope())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
