package com.personal.assistant.module.publiccodex;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.public-codex")
public class PublicCodexProperties {
    private boolean enabled;
    private Long agentId;
    private String projectKey = "public-qa";
    private int minimumIntervalSeconds = 10;
    private int maxActiveTasksPerSession = 2;
    private int maxActiveTasks = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public int getMinimumIntervalSeconds() { return minimumIntervalSeconds; }
    public void setMinimumIntervalSeconds(int minimumIntervalSeconds) { this.minimumIntervalSeconds = minimumIntervalSeconds; }
    public int getMaxActiveTasksPerSession() { return maxActiveTasksPerSession; }
    public void setMaxActiveTasksPerSession(int maxActiveTasksPerSession) { this.maxActiveTasksPerSession = maxActiveTasksPerSession; }
    public int getMaxActiveTasks() { return maxActiveTasks; }
    public void setMaxActiveTasks(int maxActiveTasks) { this.maxActiveTasks = maxActiveTasks; }
}
