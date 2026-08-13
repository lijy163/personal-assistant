package com.personal.assistant.module.wecom;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.wecom")
public class WeComProperties {
    private boolean enabled;
    private String corpId;
    private Long agentId;
    private String secret;
    private String token;
    private String encodingAesKey;
    private Long defaultCodexAgentId;
    private String defaultProjectKey = "personal-assistant";
    private Set<String> allowedUsers = new HashSet<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getEncodingAesKey() { return encodingAesKey; }
    public void setEncodingAesKey(String encodingAesKey) { this.encodingAesKey = encodingAesKey; }
    public Long getDefaultCodexAgentId() { return defaultCodexAgentId; }
    public void setDefaultCodexAgentId(Long defaultCodexAgentId) { this.defaultCodexAgentId = defaultCodexAgentId; }
    public String getDefaultProjectKey() { return defaultProjectKey; }
    public void setDefaultProjectKey(String defaultProjectKey) { this.defaultProjectKey = defaultProjectKey; }
    public Set<String> getAllowedUsers() { return allowedUsers; }
    public void setAllowedUsers(Set<String> allowedUsers) { this.allowedUsers = allowedUsers; }
}
