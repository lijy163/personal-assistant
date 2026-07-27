package com.personal.assistant.module.system.dto;
import java.time.LocalDateTime;
public record HealthInfo(String status,String database,String serviceName,String version,LocalDateTime checkedAt){}