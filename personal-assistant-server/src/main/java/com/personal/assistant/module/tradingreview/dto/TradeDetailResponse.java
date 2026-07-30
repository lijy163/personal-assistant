package com.personal.assistant.module.tradingreview.dto;
import com.personal.assistant.module.tradingreview.entity.*;import java.util.*;
public record TradeDetailResponse(TradingLog trade,List<TradingExecution> executions,TradeMetrics metrics){}