package com.personal.assistant.module.tradingreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.auth.entity.UserAccount;
import com.personal.assistant.module.auth.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class TradingSnapshotScheduler {
    private static final Logger log = LoggerFactory.getLogger(TradingSnapshotScheduler.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final UserAccountMapper userMapper;
    private final TradingMarketCollectionService collectionService;
    private final TradingCalendarService calendarService;

    public TradingSnapshotScheduler(UserAccountMapper userMapper, TradingMarketCollectionService collectionService,
                                    TradingCalendarService calendarService) {
        this.userMapper = userMapper;
        this.collectionService = collectionService;
        this.calendarService = calendarService;
    }

    @Scheduled(cron = "0 0 10,11,14 * * MON-FRI", zone = "Asia/Shanghai")
    public void collectRealtime() {
        collect("REALTIME");
    }

    @Scheduled(cron = "0 10 15 * * MON-FRI", zone = "Asia/Shanghai")
    public void collectFinal() {
        collect("FINAL");
    }

    private void collect(String snapshotType) {
        LocalDate today = LocalDate.now(SHANGHAI);
        if (!calendarService.isTradingDay(today)) return;
        userMapper.selectList(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEnabled, true)).forEach(user -> {
            try {
                collectionService.refresh(user.getId(), today, snapshotType);
            } catch (RuntimeException exception) {
                log.warn("交易快照采集失败 userId={}, type={}: {}", user.getId(), snapshotType, exception.getMessage());
            }
        });
    }
}
