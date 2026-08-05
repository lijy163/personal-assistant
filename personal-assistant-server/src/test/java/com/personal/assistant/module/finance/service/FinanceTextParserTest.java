package com.personal.assistant.module.finance.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FinanceTextParserTest {
    private final FinanceTextParser parser = new FinanceTextParser();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 16, 0);

    @Test
    void parsesAlipayStyleTransactionList() {
        String text = """
                上海地铁乘车扣款 出站时间 08...  3.00
                交通出行
                昨天 19:21
                自助获取Linux.do邀请码  -0.66
                日用百货
                昨天 13:53
                ALDI奥乐齐  101.20
                日用百货
                08-03 20:25
                """;

        FinanceTextParser.ParseResult result = parser.parse(text, now);

        assertEquals(3, result.drafts().size());
        assertEquals("上海地铁乘车扣款 出站时间 08...", result.drafts().get(0).merchant());
        assertEquals("3.00", result.drafts().get(0).amount().toPlainString());
        assertEquals(LocalDateTime.of(2026, 8, 4, 19, 21), result.drafts().get(0).transactionTime());
        assertEquals("0.66", result.drafts().get(1).amount().toPlainString());
        assertEquals(LocalDateTime.of(2026, 8, 3, 20, 25), result.drafts().get(2).transactionTime());
    }

    @Test
    void recognizesIncomeAndRefund() {
        FinanceTextParser.ParseResult result = parser.parse("退款到账 +20.00\n2026-08-05 10:20", now);

        assertEquals(1, result.drafts().size());
        assertEquals("INCOME", result.drafts().get(0).direction());
        assertEquals("REFUND", result.drafts().get(0).transactionType());
        assertNull(result.drafts().get(0).warning());
    }
    @Test
    void parsesFixedPipeSeparatedFormatFirst() {
        String text = """
                2026-08-05 09:30 | 支出 | 3.00 | 上海地铁 | 交通出行 | 乘车
                2026-08-05 10:20 | 收入 | 20.00 | 退款到账 | 其他收入 | 订单退款
                """;

        FinanceTextParser.ParseResult result = parser.parse(text, now);

        assertEquals(2, result.drafts().size());
        assertEquals("EXPENSE", result.drafts().get(0).direction());
        assertEquals("上海地铁", result.drafts().get(0).merchant());
        assertEquals("INCOME", result.drafts().get(1).direction());
        assertEquals("REFUND", result.drafts().get(1).transactionType());
    }
}
