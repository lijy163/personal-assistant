package com.personal.assistant.module.task.service;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.module.task.dto.*;
import com.personal.assistant.module.task.entity.TaskItem;
import com.personal.assistant.module.task.mapper.*;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
 @Mock TaskItemMapper taskMapper; @Mock LifeItemDetailMapper lifeMapper; @Mock WorkItemDetailMapper workMapper; @Mock WorkReviewMapper reviewMapper;
 TaskService service;
 @BeforeEach void setUp(){service=new TaskService(taskMapper,lifeMapper,workMapper,reviewMapper);}
 private TaskUpsertRequest life(String title){return new TaskUpsertRequest(title,TaskType.LIFE,TaskPriority.HIGH,TaskStatus.NOT_STARTED,LocalDateTime.now().plusDays(1),null,false,null,"家庭","备注","家庭",null,null);}
 @Test void createLifeItemSetsOwnershipAndDetail(){service.create(9L,life("缴水费"));ArgumentCaptor<TaskItem> item=ArgumentCaptor.forClass(TaskItem.class);verify(taskMapper).insert(item.capture());assertEquals(9L,item.getValue().getUserId());assertEquals("LIFE",item.getValue().getItemType());assertFalse(item.getValue().getArchived());verify(lifeMapper).insert(any(com.personal.assistant.module.task.entity.LifeItemDetail.class));}
 @Test void createRejectsMissingLifeCategory(){var request=new TaskUpsertRequest("事项",TaskType.LIFE,TaskPriority.MEDIUM,TaskStatus.NOT_STARTED,null,null,false,null,null,null,"",null,null);assertThrows(BusinessException.class,()->service.create(1L,request));}
 @Test void createRejectsEnabledLifeReminderWithoutTime(){var request=new TaskUpsertRequest("缴费",TaskType.LIFE,TaskPriority.MEDIUM,TaskStatus.NOT_STARTED,LocalDateTime.now().plusDays(1),null,true,null,null,null,"账单",null,null);assertThrows(BusinessException.class,()->service.create(1L,request));}
 @Test void archiveMarksStatusAndArchived(){TaskItem item=new TaskItem();item.setId(3L);item.setUserId(1L);item.setStatus("IN_PROGRESS");when(taskMapper.selectById(3L)).thenReturn(item);service.archive(1L,3L);assertEquals("ARCHIVED",item.getStatus());assertTrue(item.getArchived());verify(taskMapper).updateById(item);}
 @Test void cannotReadAnotherUsersTask(){TaskItem item=new TaskItem();item.setId(3L);item.setUserId(2L);when(taskMapper.selectById(3L)).thenReturn(item);assertThrows(BusinessException.class,()->service.get(1L,3L));}
}
