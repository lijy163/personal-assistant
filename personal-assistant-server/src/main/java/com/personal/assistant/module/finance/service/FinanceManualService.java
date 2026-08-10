package com.personal.assistant.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.finance.dto.FinanceMonthlyAnalysis;
import com.personal.assistant.module.finance.dto.FinanceTextParsePreview;
import com.personal.assistant.module.finance.dto.FinanceTransactionRequest;
import com.personal.assistant.module.finance.entity.FinanceAccount;
import com.personal.assistant.module.finance.entity.FinanceCategory;
import com.personal.assistant.module.finance.entity.FinanceTransaction;
import com.personal.assistant.module.finance.mapper.FinanceAccountMapper;
import com.personal.assistant.module.finance.mapper.FinanceCategoryMapper;
import com.personal.assistant.module.finance.mapper.FinanceTransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FinanceManualService {
    private static final Set<String> DIRECTIONS = Set.of("INCOME", "EXPENSE");
    private final FinanceAccountMapper accounts;
    private final FinanceCategoryMapper categories;
    private final FinanceTransactionMapper transactions;
    private final FinanceTextParser textParser;

    public FinanceManualService(FinanceAccountMapper accounts, FinanceCategoryMapper categories,
                                FinanceTransactionMapper transactions, FinanceTextParser textParser) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.textParser = textParser;
    }

    public FinanceTextParsePreview parseText(Long userId, String text) {
        List<FinanceCategory> userCategories = categories.selectList(new LambdaQueryWrapper<FinanceCategory>()
                .eq(FinanceCategory::getUserId, userId)
                .eq(FinanceCategory::getEnabled, true));
        FinanceTextParser.ParseResult result = textParser.parse(text, LocalDateTime.now());
        List<FinanceTextParsePreview.Row> rows = new ArrayList<>();
        int rowNumber = 1;
        for (FinanceTextParser.Draft draft : result.drafts()) {
            Long categoryId = userCategories.stream()
                    .filter(category -> draft.direction().equals(category.getDirection()))
                    .filter(category -> draft.description().contains(category.getCategoryName()))
                    .map(FinanceCategory::getId)
                    .findFirst().orElse(null);
            rows.add(new FinanceTextParsePreview.Row(rowNumber++, draft.transactionTime(), draft.direction(),
                    draft.amount(), draft.merchant(), draft.description(), draft.transactionType(), categoryId,
                    draft.description(), draft.warning()));
        }
        return new FinanceTextParsePreview(rows, result.ignoredLineCount());
    }

    @Transactional
    public int saveBatch(Long userId, List<FinanceTransactionRequest> requests) {
        for (FinanceTransactionRequest request : requests) {
            save(userId, null, request);
        }
        return requests.size();
    }

    @Transactional
    public Long save(Long userId, Long id, FinanceTransactionRequest request) {
        if (!DIRECTIONS.contains(request.direction())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "收支方向不合法");
        }
        requireAccount(userId, request.accountId());
        FinanceCategory category = request.categoryId() == null ? null : requireCategory(userId, request.categoryId());
        if (category != null && !request.direction().equals(category.getDirection())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类与收支方向不一致");
        }
        FinanceTransaction item = id == null ? new FinanceTransaction() : requireTransaction(userId, id);
        LocalDateTime now = LocalDateTime.now();
        item.setUserId(userId);
        item.setAccountId(request.accountId());
        item.setCategoryId(request.categoryId());
        item.setTransactionTime(request.transactionTime());
        item.setDirection(request.direction());
        item.setAmount(request.amount().abs());
        item.setMerchant(trimToNull(request.merchant()));
        item.setDescription(trimToNull(request.description()));
        item.setTransactionType(StringUtils.hasText(request.transactionType())
                ? request.transactionType().trim().toUpperCase() : request.direction());
        item.setNote(trimToNull(request.note()));
        item.setConfirmed(request.categoryId() != null);
        item.setUpdatedAt(now);
        if (id == null) {
            item.setFingerprint(hash((userId + "|MANUAL|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)));
            item.setCreatedAt(now);
            transactions.insert(item);
        } else {
            transactions.updateById(item);
        }
        return item.getId();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        transactions.deleteById(requireTransaction(userId, id));
    }

    @Transactional
    public int deleteBatch(Long userId, List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        distinctIds.forEach(id -> requireTransaction(userId, id));
        transactions.deleteByIds(distinctIds);
        return distinctIds.size();
    }

    public FinanceMonthlyAnalysis monthly(Long userId, YearMonth month) {
        Map<Long, String> categoryNames = new HashMap<>();
        categories.selectList(new LambdaQueryWrapper<FinanceCategory>().eq(FinanceCategory::getUserId, userId))
                .forEach(category -> categoryNames.put(category.getId(), category.getCategoryName()));
        List<FinanceTransaction> rows = monthRows(userId, month);
        BigDecimal income = sum(rows, "INCOME");
        BigDecimal expense = sum(rows, "EXPENSE");
        Map<String, List<FinanceTransaction>> grouped = rows.stream()
                .filter(row -> "EXPENSE".equals(row.getDirection()))
                .collect(Collectors.groupingBy(row -> categoryNames.getOrDefault(row.getCategoryId(), "未分类")));
        List<FinanceMonthlyAnalysis.CategoryAmount> categoryAmounts = grouped.entrySet().stream().map(entry -> {
            BigDecimal amount = entry.getValue().stream().map(FinanceTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal percentage = expense.signum() == 0 ? BigDecimal.ZERO
                    : amount.multiply(BigDecimal.valueOf(100)).divide(expense, 1, RoundingMode.HALF_UP);
            return new FinanceMonthlyAnalysis.CategoryAmount(entry.getKey(), amount, entry.getValue().size(), percentage);
        }).sorted(Comparator.comparing(FinanceMonthlyAnalysis.CategoryAmount::amount).reversed()).toList();
        List<FinanceMonthlyAnalysis.MonthlyTrend> trend = new ArrayList<>();
        for (int offset = 5; offset >= 0; offset--) {
            YearMonth trendMonth = month.minusMonths(offset);
            List<FinanceTransaction> trendRows = monthRows(userId, trendMonth);
            BigDecimal trendIncome = sum(trendRows, "INCOME");
            BigDecimal trendExpense = sum(trendRows, "EXPENSE");
            trend.add(new FinanceMonthlyAnalysis.MonthlyTrend(trendMonth.toString(), trendIncome,
                    trendExpense, trendIncome.subtract(trendExpense)));
        }
        int expenseCount = (int) rows.stream().filter(row -> "EXPENSE".equals(row.getDirection())).count();
        BigDecimal dailyAverage = expense.divide(BigDecimal.valueOf(month.lengthOfMonth()), 2, RoundingMode.HALF_UP);
        String topCategory = categoryAmounts.isEmpty() ? null : categoryAmounts.get(0).categoryName();
        return new FinanceMonthlyAnalysis(month.toString(), income, expense, income.subtract(expense), rows.size(),
                expenseCount, dailyAverage, topCategory, categoryAmounts, trend);
    }

    private List<FinanceTransaction> monthRows(Long userId, YearMonth month) {
        return transactions.selectList(new LambdaQueryWrapper<FinanceTransaction>()
                .eq(FinanceTransaction::getUserId, userId)
                .ge(FinanceTransaction::getTransactionTime, month.atDay(1).atStartOfDay())
                .lt(FinanceTransaction::getTransactionTime, month.plusMonths(1).atDay(1).atStartOfDay()));
    }

    private BigDecimal sum(List<FinanceTransaction> rows, String direction) {
        return rows.stream().filter(row -> direction.equals(row.getDirection())).map(FinanceTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private FinanceAccount requireAccount(Long userId, Long id) {
        FinanceAccount item = accounts.selectById(id);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资金账户不存在");
        }
        return item;
    }

    private FinanceCategory requireCategory(Long userId, Long id) {
        FinanceCategory item = categories.selectById(id);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账单分类不存在");
        }
        return item;
    }

    private FinanceTransaction requireTransaction(Long userId, Long id) {
        FinanceTransaction item = transactions.selectById(id);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "交易不存在");
        }
        return item;
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
