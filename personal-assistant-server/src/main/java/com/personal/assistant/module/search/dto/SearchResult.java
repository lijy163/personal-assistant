package com.personal.assistant.module.search.dto;import java.time.LocalDateTime;public record SearchResult(String type,Long id,String title,String snippet,LocalDateTime occurredAt,String route){}
