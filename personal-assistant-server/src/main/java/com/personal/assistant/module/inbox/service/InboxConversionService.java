package com.personal.assistant.module.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.assistant.common.exception.*;
import com.personal.assistant.module.finance.entity.FinanceTransaction;
import com.personal.assistant.module.finance.mapper.FinanceAccountMapper;
import com.personal.assistant.module.finance.mapper.FinanceCategoryMapper;
import com.personal.assistant.module.finance.mapper.FinanceTransactionMapper;
import com.personal.assistant.module.inbox.dto.*;
import com.personal.assistant.module.inbox.entity.InboxItem;
import com.personal.assistant.module.inbox.mapper.InboxItemMapper;
import com.personal.assistant.module.learning.dto.LearningRecordRequest;
import com.personal.assistant.module.learning.service.LearningService;
import com.personal.assistant.module.quicknote.dto.QuickNoteCreateRequest;
import com.personal.assistant.module.quicknote.service.QuickNoteService;
import com.personal.assistant.module.reminder.dto.ReminderRequest;
import com.personal.assistant.module.reminder.service.ReminderService;
import com.personal.assistant.module.task.dto.*;
import com.personal.assistant.module.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.*;
import java.util.*;

@Service
public class InboxConversionService {
 private static final Set<String>TYPES=Set.of("TASK","REMINDER","EXPENSE","LEARNING","NOTE");
 private final InboxItemMapper inbox; private final TaskService tasks; private final ReminderService reminders; private final LearningService learning; private final QuickNoteService notes; private final FinanceTransactionMapper transactions; private final FinanceAccountMapper accounts; private final FinanceCategoryMapper categories; private final ObjectMapper json;
 public InboxConversionService(InboxItemMapper inbox,TaskService tasks,ReminderService reminders,LearningService learning,QuickNoteService notes,FinanceTransactionMapper transactions,FinanceAccountMapper accounts,FinanceCategoryMapper categories,ObjectMapper json){this.inbox=inbox;this.tasks=tasks;this.reminders=reminders;this.learning=learning;this.notes=notes;this.transactions=transactions;this.accounts=accounts;this.categories=categories;this.json=json;}
 @Transactional public InboxConversionResponse convert(Long uid,Long id,InboxConversionRequest r){InboxItem item=require(uid,id);String type=r.type().toUpperCase();if(!TYPES.contains(type))throw validation("转换类型不合法");Long target=switch(type){case "TASK"->task(uid,item,r);case "REMINDER"->reminder(uid,item,r);case "EXPENSE"->expense(uid,item,r);case "LEARNING"->learning(uid,item,r);case "NOTE"->note(uid,item,r);default->throw validation("转换类型不合法");};item.setConfirmedType(type);item.setStatus("CONVERTED");item.setConvertedType(type);item.setConvertedId(target);item.setConvertedAt(now());item.setConfirmedAt(now());item.setConversionVersion(Optional.ofNullable(item.getConversionVersion()).orElse(0)+1);try{item.setConversionParams(json.writeValueAsString(r));}catch(Exception e){throw new IllegalStateException(e);}inbox.updateById(item);return response(item);}
 @Transactional public void undo(Long uid,Long id){InboxItem item=require(uid,id);if(!"CONVERTED".equals(item.getStatus()))throw validation("该记录尚未转换");item.setStatus("PENDING");item.setConfirmedType(null);item.setConvertedType(null);item.setConvertedId(null);item.setConvertedAt(null);item.setConfirmedAt(null);inbox.updateById(item);}
 public InboxConversionResponse result(Long uid,Long id){InboxItem item=require(uid,id);if(item.getConvertedId()==null)throw validation("该记录尚未转换");return response(item);}
 private Long task(Long uid,InboxItem x,InboxConversionRequest r){if(!StringUtils.hasText(r.taskType())||!StringUtils.hasText(r.priority()))throw validation("任务类型和优先级不能为空");TaskType type=parse(TaskType.class,r.taskType(),"任务类型不合法");TaskUpsertRequest request=new TaskUpsertRequest(title(x,r),type,parse(TaskPriority.class,r.priority(),"优先级不合法"),TaskStatus.NOT_STARTED,r.planTime(),r.deadline(),false,null,x.getTags(),combine(x,r.note()),type==TaskType.LIFE?required(r.category(),"生活分类不能为空"):null,type==TaskType.WORK?required(r.workType(),"工作类型不能为空"):null,r.projectName());return tasks.create(uid,request);}
 private Long reminder(Long uid,InboxItem x,InboxConversionRequest r){if(r.remindTime()==null)throw validation("提醒时间不能为空");return reminders.create(uid,new ReminderRequest("CUSTOM",null,r.channelId(),title(x,r),combine(x,r.note()),r.remindTime()));}
 private Long learning(Long uid,InboxItem x,InboxConversionRequest r){if(r.learningPlanId()==null)throw validation("学习记录必须关联学习计划");return learning.createRecord(uid,new LearningRecordRequest(r.learningPlanId(),x.getContent(),Optional.ofNullable(r.durationMinutes()).orElse(0),r.note(),null,null,Optional.ofNullable(r.transactionTime()).orElse(x.getRecordedAt())));}
 private Long note(Long uid,InboxItem x,InboxConversionRequest r){return notes.create(uid,new QuickNoteCreateRequest(combine(x,r.note())));}
 private Long expense(Long uid,InboxItem x,InboxConversionRequest r){if(r.accountId()==null||r.amount()==null||r.transactionTime()==null||!StringUtils.hasText(r.direction()))throw validation("账户、金额、方向和交易时间不能为空");var account=accounts.selectById(r.accountId());if(account==null||!uid.equals(account.getUserId()))throw validation("账户不存在");if(r.categoryId()!=null){var category=categories.selectById(r.categoryId());if(category==null||!uid.equals(category.getUserId()))throw validation("分类不存在");}FinanceTransaction tx=new FinanceTransaction();tx.setUserId(uid);tx.setAccountId(r.accountId());tx.setCategoryId(r.categoryId());tx.setTransactionTime(r.transactionTime());tx.setMerchant(title(x,r));tx.setDescription(x.getContent());tx.setDirection(r.direction().toUpperCase());tx.setAmount(r.amount());tx.setTransactionType("MANUAL");tx.setNote(r.note());tx.setConfirmed(true);tx.setFingerprint("inbox:"+x.getId()+":"+(Optional.ofNullable(x.getConversionVersion()).orElse(0)+1));tx.setCreatedAt(now());tx.setUpdatedAt(now());transactions.insert(tx);return tx.getId();}
 private InboxItem require(Long uid,Long id){InboxItem x=inbox.selectById(id);if(x==null||!uid.equals(x.getUserId()))throw new BusinessException(ErrorCode.NOT_FOUND,"收件箱记录不存在");return x;}
 private InboxConversionResponse response(InboxItem x){String route=switch(x.getConvertedType()){case "TASK"->"/life";case "REMINDER"->"/reminders";case "EXPENSE"->"/finance";case "LEARNING"->"/learning/records";default->"/dashboard";};return new InboxConversionResponse(x.getId(),x.getConvertedType(),x.getConvertedId(),x.getConvertedAt(),route,Optional.ofNullable(x.getConversionVersion()).orElse(0));}
 private String title(InboxItem x,InboxConversionRequest r){return StringUtils.hasText(r.title())?r.title().trim():x.getContent().substring(0,Math.min(x.getContent().length(),200));}private String combine(InboxItem x,String note){return x.getContent()+(StringUtils.hasText(note)?"\n\n"+note:"");}private String required(String value,String message){if(!StringUtils.hasText(value))throw validation(message);return value;}private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}private BusinessException validation(String message){return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}private <E extends Enum<E>>E parse(Class<E>type,String value,String message){try{return Enum.valueOf(type,value.toUpperCase());}catch(Exception e){throw validation(message);}}
}
