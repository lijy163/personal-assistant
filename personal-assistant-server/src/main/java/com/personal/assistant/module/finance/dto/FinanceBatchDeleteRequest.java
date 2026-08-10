package com.personal.assistant.module.finance.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FinanceBatchDeleteRequest(
        @NotEmpty(message = "请选择要删除的交易")
        @Size(max = 1000, message = "单次最多删除 1000 条交易")
        List<@NotNull Long> ids) {
}
