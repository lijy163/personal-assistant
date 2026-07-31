package com.personal.assistant.module.tradingreview.service;

import com.personal.assistant.module.tradingreview.dto.MarketSnapshot;
import com.personal.assistant.module.tradingreview.dto.SentimentResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class SentimentRuleEngine {
    public static final String VERSION = "V2.0";

    public SentimentResult evaluate(MarketSnapshot market) {
        if (market.risingCount() == null || market.fallingCount() == null || market.limitUpCount() == null
                || market.limitDownCount() == null || market.brokenBoardRate() == null || market.maxStreak() == null
                || market.shanghaiChange() == null || market.turnoverAmount() == null)
            return new SentimentResult(null, "数据不完整", null, "关键行情指标缺失，请手工补充后重新计算。",
                    VERSION, "{}", "INCOMPLETE");
        BigDecimal breadth = breadthScore(market);
        BigDecimal board = boardScore(market);
        BigDecimal streak = clamp(BigDecimal.valueOf(25 + Math.min(market.maxStreak(), 8) * 8.5));
        BigDecimal index = indexScore(market);
        BigDecimal turnover = market.turnoverChange() == null ? BigDecimal.valueOf(50)
                : clamp(BigDecimal.valueOf(50).add(market.turnoverChange()));
        BigDecimal score = breadth.multiply(new BigDecimal("0.30")).add(board.multiply(new BigDecimal("0.30")))
                .add(streak.multiply(new BigDecimal("0.15"))).add(index.multiply(new BigDecimal("0.15")))
                .add(turnover.multiply(new BigDecimal("0.10"))).setScale(2, RoundingMode.HALF_UP);
        String stage = score.compareTo(BigDecimal.valueOf(75)) >= 0 ? "上升"
                : score.compareTo(BigDecimal.valueOf(50)) >= 0 ? "震荡"
                : score.compareTo(BigDecimal.valueOf(30)) >= 0 ? "退潮" : "冰点";
        BigDecimal position = stage.equals("上升") ? BigDecimal.valueOf(70) : stage.equals("震荡")
                ? BigDecimal.valueOf(40) : stage.equals("退潮") ? BigDecimal.valueOf(20) : BigDecimal.valueOf(10);
        return new SentimentResult(score, stage, position, conclusion(market, stage, position), VERSION,
                dimensions(breadth, board, streak, index, turnover), "COMPLETE");
    }

    private BigDecimal breadthScore(MarketSnapshot market) {
        int total = Math.max(1, market.risingCount() + market.fallingCount());
        return clamp(BigDecimal.valueOf(50 + 50.0 * (market.risingCount() - market.fallingCount()) / total));
    }

    private BigDecimal boardScore(MarketSnapshot market) {
        return clamp(BigDecimal.valueOf(50 + market.limitUpCount() * 0.8 - market.limitDownCount() * 1.8)
                .subtract(market.brokenBoardRate().multiply(BigDecimal.valueOf(0.45))));
    }

    private BigDecimal indexScore(MarketSnapshot market) {
        BigDecimal total = market.shanghaiChange();
        int count = 1;
        if (market.shenzhenChange() != null) { total = total.add(market.shenzhenChange()); count++; }
        if (market.chinextChange() != null) { total = total.add(market.chinextChange()); count++; }
        return clamp(BigDecimal.valueOf(50).add(total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.TEN)));
    }

    private String conclusion(MarketSnapshot market, String stage, BigDecimal position) {
        String breadth = market.risingCount() >= market.fallingCount() ? "上涨家数占优" : "下跌家数占优";
        String turnover = market.turnoverChange() == null ? "成交额暂无可比基准"
                : "成交额较前一收盘日" + (market.turnoverChange().signum() >= 0 ? "放量" : "缩量")
                + market.turnoverChange().abs().setScale(2, RoundingMode.HALF_UP) + "%";
        return "市场处于" + stage + "阶段，" + breadth + "，" + turnover + "；涨停" + market.limitUpCount()
                + "家、跌停" + market.limitDownCount() + "家、炸板率" + market.brokenBoardRate() + "%、最高"
                + market.maxStreak() + "连板。建议仓位不高于" + position + "%（仅作复盘参考）。";
    }

    private String dimensions(BigDecimal breadth, BigDecimal board, BigDecimal streak, BigDecimal index,
                              BigDecimal turnover) {
        return "{\"breadth\":" + dimension("市场宽度", breadth, 30, "上涨与下跌家数")
                + ",\"board\":" + dimension("涨跌停与炸板", board, 30, "涨停、跌停和炸板率")
                + ",\"streak\":" + dimension("连板高度", streak, 15, "最高连板，8板封顶计分")
                + ",\"index\":" + dimension("主要指数", index, 15, "上证、深证、创业板均值")
                + ",\"turnover\":" + dimension("成交额", turnover, 10,
                marketReason(turnover)) + "}";
    }

    private String marketReason(BigDecimal turnover) {
        return turnover.compareTo(BigDecimal.valueOf(50)) == 0 ? "无前一收盘日数据时按中性计分" : "较前一收盘日变化";
    }

    private String dimension(String label, BigDecimal score, int weight, String reason) {
        return "{\"label\":\"" + label + "\",\"score\":" + score
                + ",\"weight\":" + weight + ",\"reason\":\"" + reason + "\"}";
    }

    private BigDecimal clamp(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
