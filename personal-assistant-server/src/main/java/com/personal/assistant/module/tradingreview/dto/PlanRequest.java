package com.personal.assistant.module.tradingreview.dto;
import jakarta.validation.constraints.*;import java.math.*;import java.time.*;
public record PlanRequest(@NotNull LocalDate tradeDate,String marketPremise,@DecimalMin("0") @DecimalMax("100") BigDecimal targetPosition,String watchStocks,String plannedTrades,String riskControls,String reminderIds,String reportNote,String status){}