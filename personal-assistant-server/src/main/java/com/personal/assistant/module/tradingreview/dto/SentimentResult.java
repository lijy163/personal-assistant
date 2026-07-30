package com.personal.assistant.module.tradingreview.dto;
import java.math.*;
public record SentimentResult(BigDecimal score,String stage,BigDecimal suggestedPosition,String conclusion,String ruleVersion,String dimensionScores,String completeness){}