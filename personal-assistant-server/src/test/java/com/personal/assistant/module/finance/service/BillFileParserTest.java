package com.personal.assistant.module.finance.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillFileParserTest {
    private final BillFileParser parser = new BillFileParser();

    @Test
    void parsesQuotedCommaAndEscapedQuote() {
        String csv = "交易时间,金额,商品说明\n"
                + "2026-07-27 10:30:00,25.80,\"咖啡,大杯\"\n"
                + "2026-07-27 11:00:00,10.00,\"会员\"\"续费\"\"\n";

        var rows = parser.parse("wechat.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, rows.size());
        assertEquals("咖啡,大杯", rows.get(0).get("商品说明"));
        assertEquals("会员\"续费\"", rows.get(1).get("商品说明"));
    }

    @Test
    void skipsDescriptionLinesBeforeHeader() {
        String csv = "支付宝交易记录明细查询\n导出时间：2026-07-27\n"
                + "交易时间,交易金额,交易对方\n"
                + "2026-07-26 09:00:00,18.00,早餐店\n";

        var rows = parser.parse("alipay.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, rows.size());
        assertEquals("18.00", rows.get(0).get("交易金额"));
        assertEquals("早餐店", rows.get(0).get("交易对方"));
    }
}
