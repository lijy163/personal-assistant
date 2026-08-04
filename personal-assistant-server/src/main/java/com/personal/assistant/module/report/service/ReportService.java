package com.personal.assistant.module.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.report.dto.ReportStats;
import com.personal.assistant.module.report.entity.GeneratedReport;
import com.personal.assistant.module.report.mapper.GeneratedReportMapper;
import com.personal.assistant.module.report.mapper.ReportStatsMapper;
import com.personal.assistant.module.tradingreview.entity.TradingAlertEvent;
import com.personal.assistant.module.tradingreview.entity.TradingDailyReview;
import com.personal.assistant.module.tradingreview.entity.TradingMarketAlertEvent;
import com.personal.assistant.module.tradingreview.entity.TradingMarketSnapshotPoint;
import com.personal.assistant.module.tradingreview.entity.TradingNextPlan;
import com.personal.assistant.module.tradingreview.dto.TradingStatsResponse;
import com.personal.assistant.module.tradingreview.service.TradingStatisticsService;
import com.personal.assistant.module.tradingreview.mapper.TradingAlertEventMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingDailyReviewMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketAlertEventMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingMarketSnapshotPointMapper;
import com.personal.assistant.module.tradingreview.mapper.TradingNextPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ReportService {
    private final GeneratedReportMapper reports;
    private final ReportStatsMapper stats;
    private final TradingDailyReviewMapper tradingReviews;
    private final TradingMarketSnapshotPointMapper tradingPoints;
    private final TradingNextPlanMapper tradingPlans;
    private final TradingAlertEventMapper tradingAlertEvents;
    private final TradingMarketAlertEventMapper marketAlertEvents;
    private final TradingStatisticsService tradingStatistics;
    private final ObjectMapper json;

    public ReportService(GeneratedReportMapper reports, ReportStatsMapper stats,
                         TradingDailyReviewMapper tradingReviews, TradingMarketSnapshotPointMapper tradingPoints,
                         TradingNextPlanMapper tradingPlans, TradingAlertEventMapper tradingAlertEvents,
                         TradingMarketAlertEventMapper marketAlertEvents, TradingStatisticsService tradingStatistics, ObjectMapper json) {
        this.reports = reports;
        this.stats = stats;
        this.tradingReviews = tradingReviews;
        this.tradingPoints = tradingPoints;
        this.tradingPlans = tradingPlans;
        this.tradingAlertEvents = tradingAlertEvents;
        this.marketAlertEvents = marketAlertEvents;
        this.tradingStatistics = tradingStatistics;
        this.json = json;
    }

    public List<GeneratedReport> list(Long uid) {
        return reports.selectList(new LambdaQueryWrapper<GeneratedReport>()
                .eq(GeneratedReport::getUserId, uid)
                .orderByDesc(GeneratedReport::getPeriodEnd)
                .last("limit 100"));
    }

    @Transactional
    public GeneratedReport generate(Long uid, String type, LocalDate reference) {
        if ("TRADING_DAILY".equals(type)) return generateTradingDaily(uid, reference);
        LocalDate start;
        LocalDate end;
        if ("WEEKLY".equals(type)) {
            start = reference.with(DayOfWeek.MONDAY);
            end = start.plusDays(6);
        } else if ("MONTHLY".equals(type)) {
            start = reference.withDayOfMonth(1);
            end = reference.withDayOfMonth(reference.lengthOfMonth());
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Report type only supports WEEKLY, MONTHLY or TRADING_DAILY");
        }
        ReportStats value = stats.stats(uid, start, end);
        String title = ("WEEKLY".equals(type) ? "个人周报 " : "个人月报 ") + start + " 至 " + end;
        String markdown = "# " + title + "\n\n"
                + "## 行动与成长\n\n"
                + "- 新建任务：" + value.createdTasks() + "\n"
                + "- 完成任务：" + value.completedTasks() + "\n"
                + "- 学习时长：" + value.learningMinutes() + " 分钟\n"
                + "- 开发沉淀：" + value.devLogCount() + " 条\n\n"
                + "## 财务摘要\n\n"
                + "- 收入：¥" + value.income() + "\n"
                + "- 支出：¥" + value.expense() + "\n"
                + "- 结余：¥" + value.income().subtract(value.expense()) + "\n\n"
                + "> 本报告由确定性统计自动生成，不包含投资建议。";
        return upsert(uid, type, start, end, title, markdown);
    }

    private GeneratedReport generateTradingDaily(Long uid, LocalDate reference) {
        TradingDailyReview review = tradingReviews.selectOne(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, uid)
                .le(TradingDailyReview::getTradeDate, reference)
                .orderByDesc(TradingDailyReview::getTradeDate)
                .orderByDesc(TradingDailyReview::getSnapshotType)
                .orderByDesc(TradingDailyReview::getUpdatedAt)
                .last("limit 1"));
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No trading review snapshot found for report generation");
        }
        LocalDate date = review.getTradeDate();
        List<TradingDailyReview> trends = tradingReviews.selectList(new LambdaQueryWrapper<TradingDailyReview>()
                .eq(TradingDailyReview::getUserId, uid)
                .eq(TradingDailyReview::getSnapshotType, "FINAL")
                .le(TradingDailyReview::getTradeDate, date)
                .orderByDesc(TradingDailyReview::getTradeDate)
                .last("limit 5"));
        Collections.reverse(trends);
        List<TradingMarketSnapshotPoint> timeline = tradingPoints.selectList(new LambdaQueryWrapper<TradingMarketSnapshotPoint>()
                .eq(TradingMarketSnapshotPoint::getUserId, uid)
                .eq(TradingMarketSnapshotPoint::getTradeDate, date)
                .orderByAsc(TradingMarketSnapshotPoint::getQuoteTime));
        List<TradingNextPlan> plans = tradingPlans.selectList(new LambdaQueryWrapper<TradingNextPlan>()
                .eq(TradingNextPlan::getUserId, uid)
                .ge(TradingNextPlan::getTradeDate, date)
                .orderByAsc(TradingNextPlan::getTradeDate)
                .last("limit 3"));
        List<TradingAlertEvent> stockEvents = tradingAlertEvents.selectList(new LambdaQueryWrapper<TradingAlertEvent>()
                .eq(TradingAlertEvent::getUserId, uid)
                .ge(TradingAlertEvent::getTriggeredAt, date.atStartOfDay())
                .lt(TradingAlertEvent::getTriggeredAt, date.plusDays(1).atStartOfDay())
                .orderByDesc(TradingAlertEvent::getTriggeredAt)
                .last("limit 10"));
        List<TradingMarketAlertEvent> marketEvents = marketAlertEvents.selectList(new LambdaQueryWrapper<TradingMarketAlertEvent>()
                .eq(TradingMarketAlertEvent::getUserId, uid)
                .eq(TradingMarketAlertEvent::getTradeDate, date)
                .orderByDesc(TradingMarketAlertEvent::getTriggeredAt)
                .last("limit 10"));
        String title = "交易研究日报 " + date + " " + review.getSnapshotType();
        TradingStatsResponse quality = tradingStatistics.calculate(uid);
        String markdown = tradingMarkdown(review, trends, timeline, plans, stockEvents, marketEvents, quality);
        return upsert(uid, "TRADING_DAILY", date, date, title, markdown);
    }

    private String tradingMarkdown(TradingDailyReview review, List<TradingDailyReview> trends,
                                   List<TradingMarketSnapshotPoint> timeline, List<TradingNextPlan> plans,
                                   List<TradingAlertEvent> stockEvents, List<TradingMarketAlertEvent> marketEvents, TradingStatsResponse quality) {
        StringBuilder md = new StringBuilder();
        md.append("# 交易研究日报 ").append(review.getTradeDate()).append("\n\n");
        md.append("> 自动整理市场数据、预警和计划，仅用于复盘与观察，不构成投资建议。\n\n");
        md.append("## 市场概览\n\n");
        bullet(md, "快照", review.getSnapshotType() + " / " + nullSafe(review.getCollectionStatus()));
        bullet(md, "情绪", nullSafe(review.getMarketStage()) + "，评分 " + value(review.getSentimentScore()));
        bullet(md, "建议仓位", value(review.getSuggestedPosition()) + "%");
        bullet(md, "涨跌家数", value(review.getRisingCount()) + " / " + value(review.getFallingCount()) + "，平盘 " + value(review.getFlatCount()));
        bullet(md, "涨跌停", value(review.getLimitUpCount()) + " / " + value(review.getLimitDownCount()) + "，炸板率 " + value(review.getBrokenBoardRate()) + "%");
        bullet(md, "成交额", value(review.getTurnoverAmount()) + "，变化 " + value(review.getTurnoverChange()) + "%");
        bullet(md, "市场中位数", value(marketMedian(review)) + "%");
        md.append("\n## 强弱原因\n\n");
        for (String item : strengthReasons(review)) md.append("- ").append(item).append("\n");
        md.append("\n## 风险点\n\n");
        for (String item : riskPoints(review, marketEvents)) md.append("- ").append(item).append("\n");
        md.append("\n## 近 5 日趋势\n\n");
        if (trends.isEmpty()) md.append("- 暂无连续收盘样本。\n");
        for (TradingDailyReview item : trends) {
            md.append("- ").append(item.getTradeDate()).append("：情绪 ").append(value(item.getSentimentScore()))
                    .append("，上涨/下跌 ").append(value(item.getRisingCount())).append("/").append(value(item.getFallingCount()))
                    .append("，涨停/跌停 ").append(value(item.getLimitUpCount())).append("/").append(value(item.getLimitDownCount()))
                    .append("，炸板率 ").append(value(item.getBrokenBoardRate())).append("%\n");
        }
        md.append("\n## 盘中时间线\n\n");
        if (timeline.isEmpty()) md.append("- 暂无盘中快照。\n");
        for (TradingMarketSnapshotPoint point : timeline) {
            md.append("- ").append(point.getQuoteTime()).append("：").append(nullSafe(point.getMarketStage()))
                    .append("，评分 ").append(value(point.getSentimentScore()))
                    .append("，上涨/下跌 ").append(value(point.getRisingCount())).append("/").append(value(point.getFallingCount()))
                    .append("，成交额 ").append(value(point.getTurnoverAmount())).append("\n");
        }
        md.append("\n## 预警回放\n\n");
        if (stockEvents.isEmpty() && marketEvents.isEmpty()) md.append("- 今日暂无触发的交易提醒或市场预警。\n");
        for (TradingMarketAlertEvent event : marketEvents) {
            md.append("- 市场：").append(event.getTitle()).append("，观察值 ").append(value(event.getObservedValue()))
                    .append("，阈值 ").append(value(event.getThresholdValue())).append("\n");
        }
        for (TradingAlertEvent event : stockEvents) {
            md.append("- 个股：").append(event.getTitle()).append("，观察值 ").append(value(event.getObservedValue()))
                    .append("，最新价 ").append(value(event.getLatestPrice())).append("\n");
        }
        md.append("\n## 明日观察项\n\n");
        for (String item : tomorrowWatch(review, plans)) md.append("- ").append(item).append("\n");
        md.append("\n## 计划检查\n\n");
        if (plans.isEmpty()) md.append("- 暂无未来交易计划。\n");
        for (TradingNextPlan plan : plans) {
            md.append("- ").append(plan.getTradeDate()).append("：目标仓位 ").append(value(plan.getTargetPosition()))
                    .append("%，关注：").append(nullSafe(plan.getWatchStocks())).append("；风控：")
                    .append(nullSafe(plan.getRiskControls())).append("\n");
        }
        md.append("\n## ????\n\n");
        bullet(md, "?? MFE????", value(quality.averageMfe()) + "%");
        bullet(md, "?? MAE????", value(quality.averageMae()) + "%");
        bullet(md, "????", value(quality.maxDrawdown()));
        bullet(md, "??/????", quality.signalAttribution().isEmpty() ? "??" : quality.signalAttribution().get(0).name() + "?" + value(quality.signalAttribution().get(0).realizedProfit()));
        md.append("\n## ????\n\n");
        md.append("| ?? | ?? | ?? | ?? | ?? | ?? | ??? |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (TradingDailyReview item : trends) {
            md.append("| ").append(item.getTradeDate()).append(" | ").append(value(item.getSentimentScore()))
                    .append(" | ").append(value(item.getRisingCount())).append(" | ").append(value(item.getFallingCount()))
                    .append(" | ").append(value(item.getLimitUpCount())).append(" | ").append(value(item.getLimitDownCount()))
                    .append(" | ").append(value(item.getBrokenBoardRate())).append("% |\n");
        }
        return md.toString();
    }

    private List<String> strengthReasons(TradingDailyReview review) {
        List<String> result = new ArrayList<>();
        if (gt(review.getRisingCount(), review.getFallingCount())) result.add("上涨家数多于下跌家数，市场宽度偏积极。");
        if (review.getLimitUpCount() != null && review.getLimitUpCount() >= 60) result.add("涨停家数处于较高水平，短线情绪活跃。");
        if (review.getMaxStreak() != null && review.getMaxStreak() >= 4) result.add("连板高度达到 " + review.getMaxStreak() + " 板，接力高度仍在。");
        JsonNode rankings = raw(review).path("sectorRankings").path("rising");
        if (rankings.isArray() && rankings.size() > 0) result.add("领涨方向集中在 " + rankings.get(0).path("name").asText("-") + "，可跟踪持续性。");
        if (result.isEmpty()) result.add("强势证据不足，等待宽度、量能或主线进一步确认。");
        return result;
    }

    private List<String> riskPoints(TradingDailyReview review, List<TradingMarketAlertEvent> marketEvents) {
        List<String> result = new ArrayList<>();
        if (review.getBrokenBoardRate() != null && review.getBrokenBoardRate().compareTo(BigDecimal.valueOf(30)) >= 0) result.add("炸板率偏高，追高和接力失败成本上升。");
        if (review.getLimitDownCount() != null && review.getLimitDownCount() >= 10) result.add("跌停家数增加，亏钱效应需要重点观察。");
        if (review.getTurnoverChange() != null && review.getTurnoverChange().compareTo(BigDecimal.ZERO) < 0) result.add("成交额环比萎缩，增量资金不足时持续性会打折。");
        if (review.getSentimentScore() != null && review.getSentimentScore().compareTo(BigDecimal.valueOf(45)) < 0) result.add("情绪评分低于 45，仓位和试错频率应更谨慎。");
        for (TradingMarketAlertEvent event : marketEvents) result.add("已触发市场预警：" + event.getTitle() + "。");
        if (result.isEmpty()) result.add("暂无明显系统性预警，但仍需按计划执行止损和仓位约束。");
        return result;
    }

    private List<String> tomorrowWatch(TradingDailyReview review, List<TradingNextPlan> plans) {
        List<String> result = new ArrayList<>();
        result.add("观察情绪评分能否维持或修复到 " + value(review.getSentimentScore()) + " 以上。 ");
        result.add("观察涨停家数、炸板率和跌停数是否同步改善。 ");
        JsonNode turnover = raw(review).path("sectorRankings").path("turnover");
        if (turnover.isArray() && turnover.size() > 0) result.add("观察成交额前排板块 " + turnover.get(0).path("name").asText("-") + " 是否继续放量。 ");
        for (TradingNextPlan plan : plans) {
            if (plan.getTradeDate() != null && plan.getTradeDate().isAfter(review.getTradeDate())) {
                result.add("按 " + plan.getTradeDate() + " 计划检查关注标的和风险控制，不把预警当作直接交易指令。");
                break;
            }
        }
        return result;
    }

    private GeneratedReport upsert(Long uid, String type, LocalDate start, LocalDate end, String title, String markdown) {
        GeneratedReport report = reports.selectOne(new LambdaQueryWrapper<GeneratedReport>()
                .eq(GeneratedReport::getUserId, uid)
                .eq(GeneratedReport::getReportType, type)
                .eq(GeneratedReport::getPeriodStart, start)
                .eq(GeneratedReport::getPeriodEnd, end));
        boolean create = report == null;
        if (create) report = new GeneratedReport();
        report.setUserId(uid);
        report.setReportType(type);
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setTitle(title);
        report.setMarkdownContent(markdown);
        report.setCreatedAt(LocalDateTime.now());
        if (create) reports.insert(report); else reports.updateById(report);
        return report;
    }

    private void bullet(StringBuilder md, String label, String value) {
        md.append("- ").append(label).append("：").append(value).append("\n");
    }

    private JsonNode raw(TradingDailyReview review) {
        try {
            return review.getRawMetrics() == null ? json.createObjectNode() : json.readTree(review.getRawMetrics());
        } catch (Exception ignored) {
            return json.createObjectNode();
        }
    }

    private BigDecimal marketMedian(TradingDailyReview review) {
        JsonNode node = raw(review).path("marketMedian").path("change");
        if (node.isMissingNode() || node.isNull()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean gt(Integer left, Integer right) {
        return left != null && right != null && left > right;
    }

    private String value(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String value(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}