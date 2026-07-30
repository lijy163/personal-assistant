package com.personal.assistant.module.tradingreview.service;

import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@Service
public class TradingCalendarService {
    private final Set<LocalDate> holidays = Set.of();
    private final Set<LocalDate> extraTradingDays = Set.of();

    public boolean isTradingDay(LocalDate date) {
        if (extraTradingDays.contains(date)) return true;
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }
}
