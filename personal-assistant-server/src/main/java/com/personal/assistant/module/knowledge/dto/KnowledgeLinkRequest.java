package com.personal.assistant.module.knowledge.dto;import jakarta.validation.constraints.NotNull;public record KnowledgeLinkRequest(@NotNull Long targetEntryId,String linkType){}
