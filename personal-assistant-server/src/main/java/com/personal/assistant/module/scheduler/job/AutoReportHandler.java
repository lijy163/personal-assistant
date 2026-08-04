package com.personal.assistant.module.scheduler.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.report.entity.GeneratedReport;
import com.personal.assistant.module.report.service.ReportService;
import com.personal.assistant.module.reminder.service.NotificationService;
import com.personal.assistant.module.tradingreview.dto.CollectionResponse;
import com.personal.assistant.module.tradingreview.service.TradingMarketCollectionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AutoReportHandler implements JobHandler {
    private final ReportService service;
    private final TradingMarketCollectionService tradingCollection;
    private final NotificationService notifications;
    private final ObjectMapper json = new ObjectMapper();

    public AutoReportHandler(ReportService service, TradingMarketCollectionService tradingCollection, NotificationService notifications) {
        this.service = service;
        this.tradingCollection = tradingCollection;
        this.notifications = notifications;
    }

    @Override
    public String type() { return "AUTO_REPORT"; }

    @Override
    public String execute(String config) {
        try {
            JsonNode node = json.readTree(config == null ? "{}" : config);
            Long userId = node.path("userId").asLong(1);
            String type = node.path("type").asText("WEEKLY");
            LocalDate reference = node.hasNonNull("referenceDate") ? LocalDate.parse(node.path("referenceDate").asText()) : LocalDate.now();
            String message = "";
            if ("TRADING_DAILY".equals(type) && node.path("collectBeforeReport").asBoolean(true)) {
                CollectionResponse collection = tradingCollection.refresh(userId, reference, node.path("snapshotType").asText("FINAL"));
                message = "????" + (collection.fresh() ? "??" : "??/??") + "?";
            }
            GeneratedReport report = service.generate(userId, type, reference);
            Long channelId = node.hasNonNull("channelId") ? node.path("channelId").asLong() : null;
            if (channelId != null) {
                notifications.send(userId, null, channelId, report.getTitle(), report.getMarkdownContent());
                message += "????";
            }
            return message + "????????" + report.getTitle();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("???????????? JSON", exception);
        }
    }
}
