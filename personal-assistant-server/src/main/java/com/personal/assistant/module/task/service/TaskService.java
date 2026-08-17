package com.personal.assistant.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.common.exception.*;
import com.personal.assistant.module.task.dto.*;
import com.personal.assistant.module.task.entity.*;
import com.personal.assistant.module.task.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {
 private final TaskItemMapper taskMapper; private final LifeItemDetailMapper lifeMapper; private final WorkItemDetailMapper workMapper; private final WorkReviewMapper reviewMapper;
 public TaskService(TaskItemMapper taskMapper,LifeItemDetailMapper lifeMapper,WorkItemDetailMapper workMapper,WorkReviewMapper reviewMapper){this.taskMapper=taskMapper;this.lifeMapper=lifeMapper;this.workMapper=workMapper;this.reviewMapper=reviewMapper;}

 public List<TaskResponse> list(Long userId,TaskType type,String keyword,TaskStatus status,TaskPriority priority,LocalDateTime start,LocalDateTime end){
  var query=new LambdaQueryWrapper<TaskItem>().eq(TaskItem::getUserId,userId).eq(TaskItem::getArchived,false)
   .eq(type!=null,TaskItem::getItemType,type==null?null:type.name()).eq(status!=null,TaskItem::getStatus,status==null?null:status.name())
   .eq(priority!=null,TaskItem::getPriority,priority==null?null:priority.name()).like(StringUtils.hasText(keyword),TaskItem::getTitle,keyword)
   .ge(start!=null,TaskItem::getPlanTime,start).le(end!=null,TaskItem::getPlanTime,end).orderByAsc(TaskItem::getPlanTime).orderByDesc(TaskItem::getCreatedAt);
  return taskMapper.selectList(query).stream().map(this::toResponse).toList();
 }
 public TaskResponse get(Long userId,Long id){return toResponse(requireOwned(userId,id));}

 @Transactional public Long create(Long userId,TaskUpsertRequest request){
  validateDetail(request,true); LocalDateTime now=LocalDateTime.now(); TaskItem item=new TaskItem(); apply(item,request); item.setUserId(userId);item.setArchived(false);item.setCreatedAt(now);item.setUpdatedAt(now);item.setStatusChangedAt(now);taskMapper.insert(item);saveDetail(item.getId(),request);return item.getId();
 }
 @Transactional public void update(Long userId,Long id,TaskUpsertRequest request){
  TaskItem item=requireOwned(userId,id); if(!item.getItemType().equals(request.itemType().name()))throw new BusinessException(ErrorCode.BUSINESS_ERROR,"事项类型创建后不能修改"); validateDetail(request,false);String oldStatus=item.getStatus();apply(item,request);item.setUpdatedAt(LocalDateTime.now());if(!oldStatus.equals(item.getStatus()))item.setStatusChangedAt(LocalDateTime.now());taskMapper.updateById(item);saveDetail(id,request);
 }
 @Transactional public void changeStatus(Long userId,Long id,TaskStatus status){TaskItem item=requireOwned(userId,id);item.setStatus(status.name());item.setArchived(status==TaskStatus.ARCHIVED);item.setStatusChangedAt(LocalDateTime.now());item.setUpdatedAt(LocalDateTime.now());taskMapper.updateById(item);}
 @Transactional public void archive(Long userId,Long id){changeStatus(userId,id,TaskStatus.ARCHIVED);}
 @Transactional public Long addReview(Long userId,Long taskId,WorkReviewRequest request){TaskItem item=requireOwned(userId,taskId);if(!TaskType.WORK.name().equals(item.getItemType()))throw new BusinessException(ErrorCode.BUSINESS_ERROR,"只有工作事项可以添加复盘");WorkReview review=new WorkReview();review.setTaskId(taskId);review.setUserId(userId);review.setContent(request.content());review.setResultType(request.resultType());review.setCreatedAt(LocalDateTime.now());reviewMapper.insert(review);return review.getId();}
 public List<WorkReview> listReviews(Long userId,Long taskId){requireOwned(userId,taskId);return reviewMapper.selectList(new LambdaQueryWrapper<WorkReview>().eq(WorkReview::getTaskId,taskId).eq(WorkReview::getUserId,userId).orderByDesc(WorkReview::getCreatedAt));}

 private TaskItem requireOwned(Long userId,Long id){TaskItem item=taskMapper.selectById(id);if(item==null||!userId.equals(item.getUserId()))throw new BusinessException(ErrorCode.NOT_FOUND,"事项不存在");return item;}
 private void validateDetail(TaskUpsertRequest r,boolean creating){if(r.itemType()==TaskType.LIFE&&!StringUtils.hasText(r.category()))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"生活分类不能为空");if(r.itemType()==TaskType.LIFE&&Boolean.TRUE.equals(r.reminderEnabled())&&r.reminderAt()==null)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"生活事项开启提醒时必须设置提醒时间");if(r.itemType()==TaskType.WORK&&!StringUtils.hasText(r.workType()))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"工作类型不能为空");if(r.itemType()==TaskType.WORK&&r.deadline()!=null&&creating&&r.deadline().isBefore(LocalDateTime.now()))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"截止时间不能早于创建时间");}
 private void apply(TaskItem i,TaskUpsertRequest r){LocalDateTime previousReminder=i.getReminderAt();boolean reminderChanged=!java.util.Objects.equals(previousReminder,r.reminderAt())||!Boolean.TRUE.equals(i.getReminderEnabled())&&Boolean.TRUE.equals(r.reminderEnabled());i.setTitle(r.title().trim());i.setItemType(r.itemType().name());i.setPriority(r.priority().name());i.setStatus(r.status().name());i.setPlanTime(r.planTime());i.setDeadline(r.deadline());i.setReminderEnabled(Boolean.TRUE.equals(r.reminderEnabled()));i.setReminderAt(r.itemType()==TaskType.LIFE&&Boolean.TRUE.equals(r.reminderEnabled())?r.reminderAt():null);if(reminderChanged||!Boolean.TRUE.equals(r.reminderEnabled()))i.setReminderSentAt(null);i.setTags(r.tags());i.setRemark(r.remark());}
 private void saveDetail(Long id,TaskUpsertRequest r){if(r.itemType()==TaskType.LIFE){LifeItemDetail d=lifeMapper.selectById(id);if(d==null){d=new LifeItemDetail();d.setTaskId(id);d.setCategory(r.category());lifeMapper.insert(d);}else{d.setCategory(r.category());lifeMapper.updateById(d);}}else if(r.itemType()==TaskType.WORK){WorkItemDetail d=workMapper.selectById(id);if(d==null){d=new WorkItemDetail();d.setTaskId(id);d.setWorkType(r.workType());d.setProjectName(r.projectName());workMapper.insert(d);}else{d.setWorkType(r.workType());d.setProjectName(r.projectName());workMapper.updateById(d);}}}
 private TaskResponse toResponse(TaskItem i){LifeItemDetail l=TaskType.LIFE.name().equals(i.getItemType())?lifeMapper.selectById(i.getId()):null;WorkItemDetail w=TaskType.WORK.name().equals(i.getItemType())?workMapper.selectById(i.getId()):null;return new TaskResponse(i.getId(),i.getTitle(),TaskType.valueOf(i.getItemType()),TaskPriority.valueOf(i.getPriority()),TaskStatus.valueOf(i.getStatus()),i.getPlanTime(),i.getDeadline(),Boolean.TRUE.equals(i.getReminderEnabled()),i.getReminderAt(),i.getReminderSentAt(),i.getTags(),i.getRemark(),l==null?null:l.getCategory(),w==null?null:w.getWorkType(),w==null?null:w.getProjectName(),i.getStatusChangedAt(),i.getCreatedAt(),i.getUpdatedAt());}
}
