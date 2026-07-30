package com.personal.assistant.module.inbox.dto;
import java.time.LocalDateTime;
public record InboxConversionResponse(Long inboxId,String convertedType,Long convertedId,LocalDateTime convertedAt,String route,int version) {}
