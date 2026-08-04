package com.personal.assistant.module.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinanceMonthlyAnalysis(
        String month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        int transactionCount,
        int expenseCount,
        BigDecimal averageDailyExpense,
        String topExpenseCategory,
        List<CategoryAmount> categories,
        List<MonthlyTrend> trend) {
    public record CategoryAmount(String categoryName, BigDecimal amount, int count, BigDecimal percentage) {}
    public record MonthlyTrend(String month, BigDecimal income, BigDecimal expense, BigDecimal balance) {}
}
