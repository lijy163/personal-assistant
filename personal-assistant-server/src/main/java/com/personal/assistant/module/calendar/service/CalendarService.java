package com.personal.assistant.module.calendar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import com.personal.assistant.module.calendar.dto.*;
import com.personal.assistant.module.calendar.entity.*;
import com.personal.assistant.module.calendar.mapper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class CalendarService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SOURCES = Set.of("LIFE", "WORK", "REMINDER", "LEARNING_PLAN", "LEARNING_REVIEW", "LEARNING_RECORD", "TRADING_PLAN", "CUSTOM");
    private final JdbcTemplate jdbc;
    private final CalendarCustomEventMapper customMapper;
    private final CalendarEventOverrideMapper overrideMapper;

    public CalendarService(JdbcTemplate jdbc, CalendarCustomEventMapper customMapper, CalendarEventOverrideMapper overrideMapper) {
        this.jdbc = jdbc; this.customMapper = customMapper; this.overrideMapper = overrideMapper;
    }

    public List<CalendarEventResponse> list(Long uid, LocalDateTime start, LocalDateTime end, Set<String> sources, Set<String> statuses) {
        if (start == null || end == null || !start.isBefore(end)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时间范围不合法");
        if (Duration.between(start, end).toDays() > 400) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单次最多查询 400 天");
        Set<String> selected = sources == null || sources.isEmpty() ? SOURCES : new HashSet<>(sources);
        List<Event> events = new ArrayList<>();
        if (selected.contains("LIFE") || selected.contains("WORK")) queryTasks(uid, start, end, selected, events);
        if (selected.contains("REMINDER")) querySimple(events, uid, start, end, "REMINDER", "reminder", "remind_time", "title", "status", "/reminders");
        if (selected.contains("LEARNING_PLAN")) queryDate(events, uid, start, end, "LEARNING_PLAN", "learning_plan", "start_date", "title", "status", "/learning/plans");
        if (selected.contains("LEARNING_REVIEW")) querySimple(events, uid, start, end, "LEARNING_REVIEW", "learning_plan", "review_time", "title", "status", "/learning/plans");
        if (selected.contains("LEARNING_RECORD")) queryLearningRecords(events, uid, start, end);
        if (selected.contains("TRADING_PLAN")) queryTradingPlans(events, uid, start, end);
        if (selected.contains("CUSTOM")) customMapper.selectList(new LambdaQueryWrapper<CalendarCustomEvent>().eq(CalendarCustomEvent::getUserId, uid)
                .lt(CalendarCustomEvent::getStartAt, end).and(q -> q.isNull(CalendarCustomEvent::getEndAt).or().gt(CalendarCustomEvent::getEndAt, start)))
                .forEach(item -> events.add(Event.custom(item)));
        Map<String, CalendarEventOverride> overrides = new HashMap<>();
        overrideMapper.selectList(new LambdaQueryWrapper<CalendarEventOverride>().eq(CalendarEventOverride::getUserId, uid))
                .forEach(value -> overrides.put(key(value.getSourceType(), value.getSourceId()), value));
        List<Event> expanded = new ArrayList<>();
        for (Event event : events) {
            CalendarEventOverride override = overrides.get(key(event.sourceType, event.sourceId));
            Event applied = override == null ? event : event.override(override);
            expand(applied, start, end, expanded);
        }
        expanded.removeIf(event -> !selected.contains(event.sourceType) || (statuses != null && !statuses.isEmpty() && !statuses.contains(event.status)));
        expanded.sort(Comparator.comparing(event -> event.startAt));
        List<CalendarEventResponse> result = new ArrayList<>();
        for (Event event : expanded) result.add(event.response(hasConflict(event, expanded)));
        return result;
    }

    @Transactional
    public Long create(Long uid, CalendarEventRequest request) {
        validateTimes(request.startAt(), request.endAt());
        CalendarCustomEvent item = new CalendarCustomEvent();
        item.setUserId(uid); item.setTitle(request.title().trim()); item.setDescription(request.description());
        item.setStartAt(request.startAt()); item.setEndAt(request.endAt()); item.setAllDay(Boolean.TRUE.equals(request.allDay()));
        item.setStatus(StringUtils.hasText(request.status()) ? request.status() : "PENDING");
        item.setColor(StringUtils.hasText(request.color()) ? request.color() : "#5b7cfa");
        item.setRecurrenceRule(normalRecurrence(request.recurrenceRule())); item.setWorkdayOnly(Boolean.TRUE.equals(request.workdayOnly()));
        item.setCreatedAt(now()); item.setUpdatedAt(now()); customMapper.insert(item); return item.getId();
    }

    @Transactional
    public void move(Long uid, String sourceType, Long sourceId, CalendarMoveRequest request) {
        if (!SOURCES.contains(sourceType)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源类型不合法");
        validateTimes(request.startAt(), request.endAt());
        if ("CUSTOM".equals(sourceType)) {
            CalendarCustomEvent item = customMapper.selectById(sourceId);
            if (item == null || !uid.equals(item.getUserId())) throw notFound();
            item.setStartAt(request.startAt()); item.setEndAt(request.endAt()); item.setAllDay(Boolean.TRUE.equals(request.allDay()));
            if (StringUtils.hasText(request.color())) item.setColor(request.color()); item.setUpdatedAt(now()); customMapper.updateById(item); return;
        }
        ensureSourceOwned(uid, sourceType, sourceId);
        CalendarEventOverride item = overrideMapper.selectOne(new LambdaQueryWrapper<CalendarEventOverride>().eq(CalendarEventOverride::getUserId, uid)
                .eq(CalendarEventOverride::getSourceType, sourceType).eq(CalendarEventOverride::getSourceId, sourceId));
        if (item == null) { item = new CalendarEventOverride(); item.setUserId(uid); item.setSourceType(sourceType); item.setSourceId(sourceId); item.setCreatedAt(now()); }
        item.setStartAt(request.startAt()); item.setEndAt(request.endAt()); item.setAllDay(Boolean.TRUE.equals(request.allDay())); item.setColor(request.color()); item.setUpdatedAt(now());
        if (item.getId() == null) overrideMapper.insert(item); else overrideMapper.updateById(item);
    }

    @Transactional public void deleteCustom(Long uid, Long id) { CalendarCustomEvent item=customMapper.selectById(id); if(item==null||!uid.equals(item.getUserId()))throw notFound(); customMapper.deleteById(id); }

    private void queryTasks(Long uid, LocalDateTime start, LocalDateTime end, Set<String> selected, List<Event> events) {
        String sql="select id,item_type,title,coalesce(plan_time,deadline) event_time,status from task_item where user_id=? and archived=false and coalesce(plan_time,deadline)>=? and coalesce(plan_time,deadline)<?";
        jdbc.query(sql, (rs,n)->new Event(rs.getString("item_type"),rs.getLong("id"),rs.getString("title"),dt(rs.getTimestamp("event_time")),null,false,rs.getString("status"),color(rs.getString("item_type")),"/"+rs.getString("item_type").toLowerCase(),null,false,false), uid, Timestamp.valueOf(start), Timestamp.valueOf(end)).stream().filter(e->selected.contains(e.sourceType)).forEach(events::add);
    }
    private void querySimple(List<Event> events, Long uid, LocalDateTime start, LocalDateTime end, String type, String table, String timeColumn, String titleColumn, String statusColumn, String route) {
        String sql="select id,"+titleColumn+" title,"+timeColumn+" event_time,"+statusColumn+" status from "+table+" where user_id=? and "+timeColumn+">=? and "+timeColumn+"<?";
        events.addAll(jdbc.query(sql,(rs,n)->new Event(type,rs.getLong("id"),rs.getString("title"),dt(rs.getTimestamp("event_time")),null,false,rs.getString("status"),color(type),route,null,false,false),uid,Timestamp.valueOf(start),Timestamp.valueOf(end)));
    }
    private void queryDate(List<Event> events, Long uid, LocalDateTime start, LocalDateTime end, String type, String table, String dateColumn, String titleColumn, String statusColumn, String route) {
        String sql="select id,"+titleColumn+" title,"+dateColumn+" event_date,"+statusColumn+" status from "+table+" where user_id=? and archived=false and "+dateColumn+">=? and "+dateColumn+"<?";
        events.addAll(jdbc.query(sql,(rs,n)->new Event(type,rs.getLong("id"),rs.getString("title"),rs.getDate("event_date").toLocalDate().atStartOfDay(),null,true,rs.getString("status"),color(type),route,null,false,false),uid,start.toLocalDate(),end.toLocalDate()));
    }
    private void queryLearningRecords(List<Event> events,Long uid,LocalDateTime start,LocalDateTime end){String sql="select id,content,record_time from learning_record where user_id=? and record_time>=? and record_time<?";events.addAll(jdbc.query(sql,(rs,n)->new Event("LEARNING_RECORD",rs.getLong("id"),shortTitle(rs.getString("content")),dt(rs.getTimestamp("record_time")),null,false,"DONE",color("LEARNING_RECORD"),"/learning/records",null,false,false),uid,Timestamp.valueOf(start),Timestamp.valueOf(end)));}
    private void queryTradingPlans(List<Event> events,Long uid,LocalDateTime start,LocalDateTime end){String sql="select id,trade_date,status from trading_next_plan where user_id=? and trade_date>=? and trade_date<?";events.addAll(jdbc.query(sql,(rs,n)->new Event("TRADING_PLAN",rs.getLong("id"),"交易次日计划",rs.getDate("trade_date").toLocalDate().atTime(9,0),null,false,rs.getString("status"),color("TRADING_PLAN"),"/trading-reviews",null,true,false),uid,start.toLocalDate(),end.toLocalDate()));}
    private void ensureSourceOwned(Long uid,String type,Long id){String table=switch(type){case "LIFE","WORK"->"task_item";case "REMINDER"->"reminder";case "LEARNING_PLAN","LEARNING_REVIEW"->"learning_plan";case "LEARNING_RECORD"->"learning_record";case "TRADING_PLAN"->"trading_next_plan";default->throw notFound();};Integer count=jdbc.queryForObject("select count(*) from "+table+" where id=? and user_id=?",Integer.class,id,uid);if(count==null||count==0)throw notFound();}
    private void expand(Event event,LocalDateTime start,LocalDateTime end,List<Event> target){if(!StringUtils.hasText(event.recurrenceRule)){if(overlaps(event,start,end))target.add(event);return;}LocalDateTime cursor=event.startAt;int guard=0;while(cursor.isBefore(end)&&guard++<500){Event occurrence=event.at(cursor);if((!event.workdayOnly||cursor.getDayOfWeek().getValue()<6)&&overlaps(occurrence,start,end))target.add(occurrence);cursor=switch(event.recurrenceRule){case "DAILY"->cursor.plusDays(1);case "WEEKLY"->cursor.plusWeeks(1);case "MONTHLY"->cursor.plusMonths(1);default->end;};}}
    private boolean overlaps(Event event,LocalDateTime start,LocalDateTime end){return event.startAt.isBefore(end)&&(event.endAt==null||event.endAt.isAfter(start));}
    private boolean hasConflict(Event current,List<Event> all){if(current.allDay)return false;LocalDateTime currentEnd=current.endAt==null?current.startAt.plusHours(1):current.endAt;return all.stream().anyMatch(other->other!=current&&!other.allDay&&other.startAt.isBefore(currentEnd)&&(other.endAt==null?other.startAt.plusHours(1):other.endAt).isAfter(current.startAt));}
    private String normalRecurrence(String value){if(!StringUtils.hasText(value))return null;String normalized=value.toUpperCase();if(!Set.of("DAILY","WEEKLY","MONTHLY").contains(normalized))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"重复规则仅支持 DAILY、WEEKLY、MONTHLY");return normalized;}
    private void validateTimes(LocalDateTime start,LocalDateTime end){if(end!=null&&!end.isAfter(start))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"结束时间必须晚于开始时间");}
    private String key(String type,Long id){return type+":"+id;} private LocalDateTime dt(Timestamp value){return value==null?null:value.toLocalDateTime();} private LocalDateTime now(){return LocalDateTime.now(SHANGHAI);} private BusinessException notFound(){return new BusinessException(ErrorCode.NOT_FOUND,"日程或来源记录不存在");}
    private String shortTitle(String value){String v=value==null?"学习记录":value.replace('\n',' ');return v.length()>40?v.substring(0,40)+"…":v;}
    private String color(String type){return switch(type){case "LIFE"->"#55b8c6";case "WORK"->"#5b7cfa";case "REMINDER"->"#ef8f82";case "LEARNING_PLAN","LEARNING_REVIEW","LEARNING_RECORD"->"#9b7bd7";case "TRADING_PLAN"->"#d6a35c";default->"#5b7cfa";};}

    private record Event(String sourceType,Long sourceId,String title,LocalDateTime startAt,LocalDateTime endAt,boolean allDay,String status,String color,String route,String recurrenceRule,boolean workdayOnly,boolean overridden){
        static Event custom(CalendarCustomEvent x){return new Event("CUSTOM",x.getId(),x.getTitle(),x.getStartAt(),x.getEndAt(),Boolean.TRUE.equals(x.getAllDay()),x.getStatus(),x.getColor(),"/calendar",x.getRecurrenceRule(),Boolean.TRUE.equals(x.getWorkdayOnly()),false);}
        Event override(CalendarEventOverride x){return new Event(sourceType,sourceId,StringUtils.hasText(x.getTitleOverride())?x.getTitleOverride():title,x.getStartAt(),x.getEndAt(),Boolean.TRUE.equals(x.getAllDay()),status,StringUtils.hasText(x.getColor())?x.getColor():color,route,StringUtils.hasText(x.getRecurrenceRule())?x.getRecurrenceRule():recurrenceRule,Boolean.TRUE.equals(x.getWorkdayOnly()),true);}
        Event at(LocalDateTime value){Duration d=endAt==null?null:Duration.between(startAt,endAt);return new Event(sourceType,sourceId,title,value,d==null?null:value.plus(d),allDay,status,color,route,recurrenceRule,workdayOnly,overridden);}
        CalendarEventResponse response(boolean conflict){return new CalendarEventResponse(sourceType+":"+sourceId+":"+startAt,sourceType,sourceId,title,startAt,endAt,allDay,status,color,route,recurrenceRule,workdayOnly,overridden,conflict);}
    }
}
