package com.personal.assistant.module.tradingreview.dto;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;import java.time.*;
public record CollectionResponse(TradingDailyReview review,boolean fresh,String message,LocalDateTime lastSuccessAt){}