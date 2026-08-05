package com.personal.assistant.module.finance.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FinanceTextParser {
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d.])([+\\-]?)\\s*[¥￥]?\\s*(\\d{1,12}(?:,\\d{3})*\\.\\d{1,2})(?![\\d.])");
    private static final Pattern FULL_TIME = Pattern.compile("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern SHORT_TIME = Pattern.compile("(?<!\\d)(\\d{1,2})[-/.月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern RELATIVE_TIME = Pattern.compile("(今天|昨天|前天)\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

    public ParseResult parse(String text, LocalDateTime now) {
        List<String> lines = text.lines().map(String::trim).filter(StringUtils::hasText).toList();
        List<Draft> fixedDrafts = parseFixedFormat(lines, now);
        if (!fixedDrafts.isEmpty()) {
            return new ParseResult(fixedDrafts, Math.max(0, lines.size() - fixedDrafts.size()));
        }
        List<Integer> anchors = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (findAmount(lines.get(index)) != null) {
                anchors.add(index);
            }
        }
        List<Draft> drafts = new ArrayList<>();
        for (int index = 0; index < anchors.size(); index++) {
            int start = anchors.get(index);
            int end = index + 1 < anchors.size() ? anchors.get(index + 1) : lines.size();
            String anchor = lines.get(start);
            AmountMatch amount = findAmount(anchor);
            if (amount == null) {
                continue;
            }
            List<String> block = new ArrayList<>(lines.subList(start, end));
            if (start > 0 && merchantBeforeAmount(anchor, amount).isEmpty()) {
                block.add(0, lines.get(start - 1));
            }
            String source = String.join(" / ", block);
            LocalDateTime transactionTime = parseTime(source, now);
            String merchant = merchantBeforeAmount(anchor, amount);
            if (merchant.isEmpty()) {
                merchant = block.stream().filter(this::looksLikeMerchant).findFirst().orElse("待确认商户");
            }
            String direction = inferDirection(source, amount.sign());
            String warning = transactionTime == null ? "未识别到交易时间，请确认" : null;
            drafts.add(new Draft(transactionTime == null ? now.withSecond(0).withNano(0) : transactionTime,
                    direction, amount.value(), merchant, source, inferType(source, direction), warning));
        }
        int usedLines = anchors.size();
        return new ParseResult(drafts, Math.max(0, lines.size() - usedLines));
    }

    private List<Draft> parseFixedFormat(List<String> lines, LocalDateTime now) {
        List<Draft> drafts = new ArrayList<>();
        for (String line : lines) {
            if (!line.contains("|")) continue;
            String[] fields = line.split("\\|", -1);
            if (fields.length < 4) continue;
            LocalDateTime transactionTime = parseFixedTime(fields[0].trim());
            AmountMatch amount = findAmount(fields[2].trim());
            if (amount == null) continue;
            String directionText = fields[1].trim();
            String direction = directionText.contains("收入") || "INCOME".equalsIgnoreCase(directionText)
                    ? "INCOME" : "EXPENSE";
            String merchant = fields[3].trim().isEmpty() ? "待确认商户" : fields[3].trim();
            String category = fields.length > 4 ? fields[4].trim() : "";
            String description = fields.length > 5 ? fields[5].trim() : category;
            String source = String.join(" / ", merchant, category, description);
            String warning = transactionTime == null ? "时间格式应为 yyyy-MM-dd HH:mm" : null;
            drafts.add(new Draft(transactionTime == null ? now.withSecond(0).withNano(0) : transactionTime,
                    direction, amount.value(), merchant, source, inferType(directionText + description, direction), warning));
        }
        return drafts;
    }

    private LocalDateTime parseFixedTime(String value) {
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (java.time.DateTimeException ignored) {
            }
        }
        return null;
    }

    private AmountMatch findAmount(String line) {
        Matcher matcher = AMOUNT.matcher(line);
        AmountMatch result = null;
        while (matcher.find()) {
            BigDecimal value = new BigDecimal(matcher.group(2).replace(",", ""));
            String sign = matcher.group(1);
            result = new AmountMatch(value.abs(), "-".equals(sign) ? -1 : "+".equals(sign) ? 1 : 0,
                    matcher.start(), matcher.end());
        }
        return result;
    }

    private String merchantBeforeAmount(String line, AmountMatch amount) {
        return line.substring(0, amount.start()).replaceAll("[|｜:：\\t]+$", "").trim();
    }

    private boolean looksLikeMerchant(String line) {
        return line.length() <= 255 && !line.matches(".*\\d{1,2}:\\d{2}.*")
                && findAmount(line) == null && !line.matches("(支出|收入|转账|退款|订单|全部|筛选|交易记录)");
    }

    private LocalDateTime parseTime(String text, LocalDateTime now) {
        Matcher full = FULL_TIME.matcher(text);
        if (full.find()) {
            return dateTime(full, 1);
        }
        Matcher shortTime = SHORT_TIME.matcher(text);
        if (shortTime.find()) {
            return LocalDateTime.of(now.getYear(), number(shortTime, 1), number(shortTime, 2),
                    number(shortTime, 3), number(shortTime, 4), optionalNumber(shortTime, 5));
        }
        Matcher relative = RELATIVE_TIME.matcher(text);
        if (relative.find()) {
            int days = "昨天".equals(relative.group(1)) ? 1 : "前天".equals(relative.group(1)) ? 2 : 0;
            return LocalDateTime.of(now.toLocalDate().minusDays(days), LocalTime.of(number(relative, 2),
                    number(relative, 3), optionalNumber(relative, 4)));
        }
        return null;
    }

    private LocalDateTime dateTime(Matcher matcher, int offset) {
        try {
            return LocalDateTime.of(number(matcher, offset), number(matcher, offset + 1),
                    number(matcher, offset + 2), number(matcher, offset + 3),
                    number(matcher, offset + 4), optionalNumber(matcher, offset + 5));
        } catch (java.time.DateTimeException exception) {
            return null;
        }
    }

    private int number(Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }

    private int optionalNumber(Matcher matcher, int group) {
        return matcher.group(group) == null ? 0 : number(matcher, group);
    }

    private String inferDirection(String source, int sign) {
        String normalized = source.toLowerCase(Locale.ROOT);
        if (normalized.contains("收入") || normalized.contains("收款") || normalized.contains("到账") || sign > 0) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String inferType(String source, String direction) {
        if (source.contains("退款")) return "REFUND";
        if (source.contains("转账")) return "TRANSFER";
        if (source.contains("还款")) return "REPAYMENT";
        return direction;
    }

    public record Draft(LocalDateTime transactionTime, String direction, BigDecimal amount, String merchant,
                        String description, String transactionType, String warning) {
    }

    public record ParseResult(List<Draft> drafts, int ignoredLineCount) {
    }

    private record AmountMatch(BigDecimal value, int sign, int start, int end) {
    }
}
