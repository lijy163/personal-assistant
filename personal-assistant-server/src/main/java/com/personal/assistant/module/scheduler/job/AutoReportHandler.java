package com.personal.assistant.module.scheduler.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.module.report.entity.GeneratedReport;
import com.personal.assistant.module.report.service.ReportService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AutoReportHandler implements JobHandler {
    private final ReportService service;
    private final ObjectMapper json = new ObjectMapper();

    public AutoReportHandler(ReportService service) {
        this.service = service;
    }

    @Override
    public String type() {
        return "AUTO_REPORT";
    }

    @Override
    public String execute(String config) {
        try {
            JsonNode node = json.readTree(config == null ? "{}" : config);
            Long userId = node.path("userId").asLong(1);
            String type = node.path("type").asText("WEEKLY");
            GeneratedReport report = service.generate(userId, type, LocalDate.now());
            return "自动报告已生成：" + report.getTitle();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("自动报告调度参数不是有效 JSON", exception);
        }
    }
}