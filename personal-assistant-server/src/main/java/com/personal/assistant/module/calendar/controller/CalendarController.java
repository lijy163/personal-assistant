package com.personal.assistant.module.calendar.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.calendar.dto.*;
import com.personal.assistant.module.calendar.service.CalendarService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService service; public CalendarController(CalendarService service){this.service=service;}
    private Long uid(){return SecurityContextHelper.currentUserId();}
    @GetMapping public ApiResponse<List<CalendarEventResponse>> list(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,@RequestParam(required=false) Set<String> sources,@RequestParam(required=false) Set<String> statuses){return ApiResponse.success(service.list(uid(),start,end,sources,statuses));}
    @PostMapping public ApiResponse<Long> create(@Valid @RequestBody CalendarEventRequest request){return ApiResponse.success(service.create(uid(),request));}
    @PatchMapping("/{sourceType}/{sourceId}/time") public ApiResponse<Void> move(@PathVariable String sourceType,@PathVariable Long sourceId,@Valid @RequestBody CalendarMoveRequest request){service.move(uid(),sourceType.toUpperCase(),sourceId,request);return ApiResponse.success();}
    @DeleteMapping("/custom/{id}") public ApiResponse<Void> delete(@PathVariable Long id){service.deleteCustom(uid(),id);return ApiResponse.success();}
}
