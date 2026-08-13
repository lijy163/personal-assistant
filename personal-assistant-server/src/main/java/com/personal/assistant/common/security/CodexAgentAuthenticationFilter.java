package com.personal.assistant.common.security;

import com.personal.assistant.module.codexagent.entity.CodexAgent;
import com.personal.assistant.module.codexagent.service.CodexAgentService;
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
public class CodexAgentAuthenticationFilter extends OncePerRequestFilter {
    private static final String AGENT_PATH_PREFIX = "/api/codex-agent-runtime/";
    private static final String BEARER_PREFIX = "Bearer ";
    private final CodexAgentService agentService;

    public CodexAgentAuthenticationFilter(CodexAgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AGENT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            CodexAgent agent = agentService.authenticate(header.substring(BEARER_PREFIX.length()));
            if (agent != null) {
                AuthUser principal = new AuthUser(agent.getUserId(), "agent:" + agent.getId());
                var authentication = new UsernamePasswordAuthenticationToken(principal, agent.getId(),
                        List.of(new SimpleGrantedAuthority("codex-agent:run")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
