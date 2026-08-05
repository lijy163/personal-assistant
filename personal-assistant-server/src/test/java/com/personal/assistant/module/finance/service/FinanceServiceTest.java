package com.personal.assistant.module.finance.service;

import com.personal.assistant.module.finance.dto.FinanceAccountRequest;
import com.personal.assistant.module.finance.entity.FinanceAccount;
import com.personal.assistant.module.finance.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {
    @Mock private FinanceAccountMapper accounts;
    @Mock private FinanceCategoryMapper categories;
    @Mock private FinanceCategoryRuleMapper rules;
    @Mock private FinanceImportBatchMapper batches;
    @Mock private FinanceRawTransactionMapper rawRows;
    @Mock private FinanceTransactionMapper transactions;
    @Mock private BillFileParser parser;
    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(accounts, categories, rules, batches, rawRows, transactions, parser);
        lenient().when(accounts.selectCount(any())).thenReturn(0L);
        AtomicLong ids = new AtomicLong(10);
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, FinanceAccount.class).setId(ids.getAndIncrement());
            return 1;
        }).when(accounts).insert(any(FinanceAccount.class));
    }

    @Test
    void createsMultipleAlipayAccountsWhenNamesDiffer() {
        Long first = service.saveAccount(3L, null, request("生活支付宝"));
        Long second = service.saveAccount(3L, null, request("备用支付宝"));

        assertEquals(10L, first);
        assertEquals(11L, second);
        verify(accounts, times(2)).insert(any(FinanceAccount.class));
    }

    @Test
    void exposesSqlStateAndConstraintForDiagnostics() {
        SQLException sql = new SQLException("duplicate key value violates unique constraint \"finance_account_pkey\"", "23505");
        assertEquals("SQLState 23505 / finance_account_pkey", FinanceService.integrityDiagnostic(sql));
    }

    private FinanceAccountRequest request(String name) {
        return new FinanceAccountRequest(name, "ALIPAY", null, "CNY", true);
    }
}
