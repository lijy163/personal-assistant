package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.tradingreview.dto.TradingReviewAnalyticsResponse;
import com.personal.assistant.module.tradingreview.entity.*;
import com.personal.assistant.module.tradingreview.mapper.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class TradingReviewAnalyticsService {
    private final TradingDailyReviewMapper reviews; private final TradingMarketSnapshotPointMapper points;
    private final TradingNextPlanMapper plans; private final TradingLogMapper trades; private final ObjectMapper json;
    public TradingReviewAnalyticsService(TradingDailyReviewMapper reviews, TradingMarketSnapshotPointMapper points,
                                         TradingNextPlanMapper plans, TradingLogMapper trades, ObjectMapper json) {
        this.reviews=reviews;this.points=points;this.plans=plans;this.trades=trades;this.json=json;
    }
    public TradingReviewAnalyticsResponse analytics(Long uid, LocalDate date) {
        LocalDate target=date==null?LocalDate.now(ZoneId.of("Asia/Shanghai")):date;
        List<TradingDailyReview> finals=reviews.selectList(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId,uid).eq(TradingDailyReview::getSnapshotType,"FINAL")
                .le(TradingDailyReview::getTradeDate,target).orderByDesc(TradingDailyReview::getTradeDate).last("limit 5"));
        List<TradingReviewAnalyticsResponse.DailyTrend> trend=new ArrayList<>();
        List<TradingDailyReview> chronological=new ArrayList<>(finals);
        Collections.reverse(chronological);
        chronological.forEach(v->trend.add(new TradingReviewAnalyticsResponse.DailyTrend(v.getTradeDate(),v.getSentimentScore(),
                v.getRisingCount(),v.getFallingCount(),v.getLimitUpCount(),v.getLimitDownCount(),v.getBrokenBoardRate(),
                v.getMaxStreak(),v.getTurnoverAmount(),v.getTurnoverChange())));
        List<TradingReviewAnalyticsResponse.TimelinePoint> timeline=points.selectList(new LambdaQueryWrapper<TradingMarketSnapshotPoint>()
                .eq(TradingMarketSnapshotPoint::getUserId,uid).eq(TradingMarketSnapshotPoint::getTradeDate,target)
                .orderByAsc(TradingMarketSnapshotPoint::getQuoteTime)).stream().map(v->new TradingReviewAnalyticsResponse.TimelinePoint(
                v.getQuoteTime(),v.getSentimentScore(),v.getMarketStage(),v.getRisingCount(),v.getFallingCount(),
                v.getLimitUpCount(),v.getLimitDownCount(),v.getBrokenBoardRate(),v.getTurnoverAmount())).toList();
        return new TradingReviewAnalyticsResponse(trend,timeline,advancement(finals),execution(uid,target));
    }
    private TradingReviewAnalyticsResponse.Advancement advancement(List<TradingDailyReview> finals) {
        if(finals.size()<2)return unavailable(finals.isEmpty()?null:finals.get(0).getTradeDate(),null,"历史收盘数据不足");
        TradingDailyReview current=finals.get(0),previous=finals.get(1);
        Map<String,Integer> now=stocks(current.getRawMetrics()),before=stocks(previous.getRawMetrics());
        if(now.isEmpty()||before.isEmpty())return unavailable(current.getTradeDate(),previous.getTradeDate(),"涨停股票明细尚未积累");
        long first=before.values().stream().filter(v->v==1).count(),second=before.values().stream().filter(v->v==2).count();
        long firstAdvanced=before.entrySet().stream().filter(v->v.getValue()==1&&now.getOrDefault(v.getKey(),0)>=2).count();
        long secondAdvanced=before.entrySet().stream().filter(v->v.getValue()==2&&now.getOrDefault(v.getKey(),0)>=3).count();
        return new TradingReviewAnalyticsResponse.Advancement(current.getTradeDate(),previous.getTradeDate(),(int)first,
                (int)firstAdvanced,rate(firstAdvanced,first),(int)second,(int)secondAdvanced,rate(secondAdvanced,second),"AVAILABLE");
    }
    private Map<String,Integer> stocks(String raw){Map<String,Integer> result=new HashMap<>();if(raw==null)return result;
        try{for(JsonNode v:json.readTree(raw).path("limitUpStocks"))result.put(v.path("code").asText(),v.path("streak").asInt(1));}
        catch(Exception ignored){}return result;}
    private TradingReviewAnalyticsResponse.Advancement unavailable(LocalDate now,LocalDate before,String status){return new TradingReviewAnalyticsResponse.Advancement(now,before,0,0,BigDecimal.ZERO,0,0,BigDecimal.ZERO,status);}
    private TradingReviewAnalyticsResponse.Execution execution(Long uid,LocalDate date){List<TradingNextPlan> due=plans.selectList(new LambdaQueryWrapper<TradingNextPlan>().eq(TradingNextPlan::getUserId,uid).le(TradingNextPlan::getTradeDate,date).ne(TradingNextPlan::getStatus,"CANCELLED"));
        List<TradingLog> logs=trades.selectList(new LambdaQueryWrapper<TradingLog>().eq(TradingLog::getUserId,uid));long completed=due.stream().filter(v->"COMPLETED".equals(v.getStatus())).count(),planned=logs.stream().filter(v->Boolean.TRUE.equals(v.getPlanned())).count();
        return new TradingReviewAnalyticsResponse.Execution(due.size(),completed,rate(completed,due.size()),logs.size(),planned,rate(planned,logs.size()));}
    private BigDecimal rate(long value,long total){return total==0?BigDecimal.ZERO:BigDecimal.valueOf(value*100).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);}
}
