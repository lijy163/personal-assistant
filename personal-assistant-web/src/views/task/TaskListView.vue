<template>
  <div class="task-page">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="标题关键词" @keyup.enter="load" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width:130px"><el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="filters.priority" clearable placeholder="全部" style="width:120px"><el-option v-for="o in priorityOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card><template #header><div class="task-card-header"><span>{{ pageName }}列表</span><el-button type="primary" @click="openCreate">新建{{ pageName }}</el-button></div></template>
      <el-table class="desktop-task-table" v-loading="loading" :data="items" empty-text="暂无事项">
        <el-table-column prop="title" label="标题" min-width="180"><template #default="{row}"><el-link type="primary" @click="showDetail(row)">{{ row.title }}</el-link></template></el-table-column>
        <el-table-column :label="isLife?'分类':'工作类型'" width="120"><template #default="{row}">{{ isLife?row.category:row.workType }}</template></el-table-column>
        <el-table-column v-if="!isLife" prop="projectName" label="关联项目" width="140" />
        <el-table-column label="优先级" width="100"><template #default="{row}"><el-tag :type="priorityType(row.priority)">{{ labelOf(priorityOptions,row.priority) }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ labelOf(statusOptions,row.status) }}</el-tag></template></el-table-column>
        <el-table-column :label="isLife?'计划时间':'截止时间'" width="180"><template #default="{row}">{{ formatTime(isLife?row.planTime:row.deadline) }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="row.status!=='COMPLETED'" link type="success" @click="complete(row)">完成</el-button><el-button link type="danger" @click="archive(row)">归档</el-button></template></el-table-column>
      </el-table>
      <div class="mobile-task-list">
        <article v-for="row in items" :key="row.id" class="mobile-task-card" @click="showDetail(row)">
          <div class="mobile-task-top"><b>{{ row.title }}</b><el-tag :type="priorityType(row.priority)" size="small">{{ labelOf(priorityOptions,row.priority) }}</el-tag></div>
          <div class="mobile-task-meta"><span>{{ isLife?row.category:row.workType }}</span><span>{{ formatTime(isLife?row.planTime:row.deadline) }}</span></div>
          <div class="mobile-task-actions" @click.stop><el-tag :type="statusType(row.status)">{{ labelOf(statusOptions,row.status) }}</el-tag><span class="spacer"></span><el-button text type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="row.status!=='COMPLETED'" text type="success" @click="complete(row)">完成</el-button></div>
        </article>
        <el-empty v-if="!loading&&!items.length" description="暂无事项" />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId?'编辑事项':`新建${pageName}`" width="min(620px, 94vw)" class="task-form-dialog" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="优先级" prop="priority"><el-select v-model="form.priority" style="width:100%"><el-option v-for="o in priorityOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="状态" prop="status"><el-select v-model="form.status" style="width:100%"><el-option v-for="o in editableStatusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col></el-row>
        <el-form-item v-if="isLife" label="生活分类" prop="category"><el-select v-model="form.category" allow-create filterable style="width:100%"><el-option v-for="c in lifeCategories" :key="c" :label="c" :value="c" /></el-select></el-form-item>
        <template v-else><el-row :gutter="16"><el-col :span="12"><el-form-item label="工作类型" prop="workType"><el-select v-model="form.workType" allow-create filterable style="width:100%"><el-option v-for="c in workTypes" :key="c" :label="c" :value="c" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="关联项目"><el-input v-model="form.projectName" /></el-form-item></el-col></el-row></template>
        <el-form-item v-if="isLife" label="计划时间" prop="planTime"><el-date-picker v-model="form.planTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择时间" style="width:100%" /></el-form-item><el-form-item v-else label="截止时间" prop="deadline"><el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择时间" style="width:100%" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="4" maxlength="5000" show-word-limit /></el-form-item>
        <el-form-item label="提醒"><el-switch v-model="form.reminderEnabled" /><span class="form-help">{{ isLife ? '到达设定时间后通过已启用的消息通道推送' : '工作日 08:50 合并提醒，仅推送标题' }}</span></el-form-item>
        <el-form-item v-if="isLife && form.reminderEnabled" label="提醒时间" prop="reminderAt"><el-date-picker v-model="form.reminderAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择消息提醒时间" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="selected?.title" size="min(520px, 94vw)">
      <el-descriptions v-if="selected" :column="1" border><el-descriptions-item label="状态">{{ labelOf(statusOptions,selected.status) }}</el-descriptions-item><el-descriptions-item :label="isLife?'分类':'工作类型'">{{ isLife?selected.category:selected.workType }}</el-descriptions-item><el-descriptions-item v-if="!isLife" label="关联项目">{{ selected.projectName||'-' }}</el-descriptions-item><el-descriptions-item label="时间">{{ formatTime(isLife?selected.planTime:selected.deadline) }}</el-descriptions-item><el-descriptions-item v-if="isLife" label="消息提醒">{{ selected.reminderEnabled ? `${formatTime(selected.reminderAt)}${selected.reminderSentAt ? ' · 已发送' : ''}` : '未开启' }}</el-descriptions-item><el-descriptions-item label="标签">{{ selected.tags||'-' }}</el-descriptions-item><el-descriptions-item label="备注">{{ selected.remark||'-' }}</el-descriptions-item></el-descriptions>
      <template v-if="!isLife&&selected"><el-divider>工作复盘</el-divider><el-form :model="reviewForm"><el-form-item><el-select v-model="reviewForm.resultType" style="width:140px"><el-option label="达成" value="ACHIEVED"/><el-option label="部分达成" value="PARTIAL"/><el-option label="未达成" value="MISSED"/></el-select></el-form-item><el-form-item><el-input v-model="reviewForm.content" type="textarea" :rows="3" placeholder="记录结果、经验和下一步" /></el-form-item><el-button type="primary" :loading="reviewSaving" @click="saveReview">添加复盘</el-button></el-form><div v-for="r in reviews" :key="r.id" class="review-item"><el-tag size="small">{{ r.resultType }}</el-tag><span>{{ r.content }}</span><small>{{ formatTime(r.createdAt) }}</small></div></template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import type {FormInstance,FormRules,TagProps} from 'element-plus'; import {ElMessage,ElMessageBox} from 'element-plus'; import {computed,onMounted,reactive,ref,watch} from 'vue'; import {useRoute} from 'vue-router';
import {addReview,archiveTask,changeTaskStatus,createTask,listReviews,listTasks,updateTask,type TaskItem,type TaskPayload,type TaskPriority,type TaskStatus,type TaskType,type WorkReview} from '@/api/task';
const route=useRoute(); const taskType=computed(()=>String(route.meta.taskType||'LIFE') as TaskType); const isLife=computed(()=>taskType.value==='LIFE'); const pageName=computed(()=>isLife.value?'生活事项':'工作事项');
const statusOptions=[{label:'草稿',value:'DRAFT'},{label:'未开始',value:'NOT_STARTED'},{label:'进行中',value:'IN_PROGRESS'},{label:'已完成',value:'COMPLETED'}] as const; const editableStatusOptions=statusOptions; const priorityOptions=[{label:'低',value:'LOW'},{label:'中',value:'MEDIUM'},{label:'高',value:'HIGH'},{label:'紧急',value:'URGENT'}] as const;
const priorityTag:Record<TaskPriority,TagProps['type']>={LOW:'info',MEDIUM:undefined,HIGH:'warning',URGENT:'danger'}; const statusTag:Record<TaskStatus,TagProps['type']>={DRAFT:'info',NOT_STARTED:undefined,IN_PROGRESS:'warning',COMPLETED:'success',ARCHIVED:'info'}; const lifeCategories=['日常','家庭','健康','账单','习惯','其他']; const workTypes=['任务','会议','问题','跟进','复盘','其他'];
const filters=reactive<{keyword:string;status?:TaskStatus;priority?:TaskPriority}>({keyword:''}); const items=ref<TaskItem[]>([]);const loading=ref(false);const formVisible=ref(false);const saving=ref(false);const editingId=ref<number>();const formRef=ref<FormInstance>();
const emptyForm=():TaskPayload=>({title:'',itemType:taskType.value,priority:'MEDIUM',status:'NOT_STARTED',planTime:null,deadline:null,reminderEnabled:false,reminderAt:null,tags:'',remark:'',category:'日常',workType:'任务',projectName:''});const form=reactive<TaskPayload>(emptyForm());
const rules=computed<FormRules>(()=>({title:[{required:true,message:'请输入标题',trigger:'blur'}],priority:[{required:true}],status:[{required:true}],category:isLife.value?[{required:true,message:'请选择分类'}]:[],workType:!isLife.value?[{required:true,message:'请选择工作类型'}]:[],deadline:!isLife.value?[{required:true,message:'请选择截止时间'}]:[],reminderAt:isLife.value&&form.reminderEnabled?[{required:true,message:'请选择提醒时间'}]:[]}));
const detailVisible=ref(false);const selected=ref<TaskItem>();const reviews=ref<WorkReview[]>([]);const reviewForm=reactive({resultType:'ACHIEVED',content:''});const reviewSaving=ref(false);
function labelOf(options:readonly {label:string;value:string}[],value:string){return options.find(o=>o.value===value)?.label||value;} function priorityType(value:TaskPriority){return priorityTag[value];} function statusType(value:TaskStatus){return statusTag[value];} function formatTime(v:string|null){return v?new Date(v).toLocaleString('zh-CN'):'-';}
async function load(){loading.value=true;try{items.value=(await listTasks({type:taskType.value,keyword:filters.keyword||undefined,status:filters.status,priority:filters.priority})).data;}finally{loading.value=false;}}
function resetFilters(){filters.keyword='';filters.status=undefined;filters.priority=undefined;void load();} function assignForm(v:TaskPayload){Object.assign(form,v);} function openCreate(){editingId.value=undefined;assignForm(emptyForm());formVisible.value=true;} function openEdit(row:TaskItem){editingId.value=row.id;assignForm({...emptyForm(),...row,category:row.category||'',workType:row.workType||'',projectName:row.projectName||'',tags:row.tags||'',remark:row.remark||''});formVisible.value=true;}
async function save(){if(!(await formRef.value?.validate()))return;saving.value=true;try{editingId.value?await updateTask(editingId.value,form):await createTask(form);ElMessage.success('保存成功');formVisible.value=false;await load();}finally{saving.value=false;}}
async function complete(row:TaskItem){await changeTaskStatus(row.id,'COMPLETED');ElMessage.success('已完成');await load();} async function archive(row:TaskItem){await ElMessageBox.confirm(`确认归档“${row.title}”？`,'归档确认',{type:'warning'});await archiveTask(row.id);ElMessage.success('已归档');await load();}
async function showDetail(row:TaskItem){selected.value=row;detailVisible.value=true;if(!isLife.value)reviews.value=(await listReviews(row.id)).data;} async function saveReview(){if(!selected.value||!reviewForm.content.trim()){ElMessage.warning('请输入复盘内容');return;}reviewSaving.value=true;try{await addReview(selected.value.id,reviewForm);reviewForm.content='';reviews.value=(await listReviews(selected.value.id)).data;ElMessage.success('复盘已保存');}finally{reviewSaving.value=false;}}
watch(taskType,()=>{resetFilters();assignForm(emptyForm());});onMounted(load);
</script><style scoped>
.mobile-task-list{display:none}
@media(max-width:768px){.desktop-task-table{display:none}.task-form-dialog :deep(.el-dialog__body){max-height:calc(100dvh - 150px);padding:14px;overflow-y:auto}.task-form-dialog :deep(.el-dialog__footer){padding:12px 14px calc(12px + env(safe-area-inset-bottom))}.task-form-dialog :deep(.el-form-item){display:block}.task-form-dialog :deep(.el-form-item__label){width:auto!important;height:auto;margin-bottom:6px}.task-form-dialog :deep(.el-form-item__content){margin-left:0!important}.task-form-dialog :deep(input),.task-form-dialog :deep(textarea){font-size:16px}.task-form-dialog :deep(.el-row){display:block;margin:0!important}.task-form-dialog :deep(.el-col){max-width:100%;padding:0!important}.mobile-task-list{display:flex;flex-direction:column;gap:12px}.mobile-task-card{padding:14px;background:#fff;border:1px solid #e2e8f0;border-radius:14px}.mobile-task-top,.mobile-task-actions{display:flex;align-items:center;gap:8px}.mobile-task-top{justify-content:space-between}.mobile-task-top b{min-width:0;word-break:break-word}.mobile-task-meta{display:flex;justify-content:space-between;gap:10px;margin:12px 0;color:#64748b;font-size:13px}.mobile-task-meta span:last-child{text-align:right}.spacer{flex:1}.filter-card :deep(.el-card__body){padding:14px}.task-card-header{gap:10px}.task-card-header .el-button{flex-shrink:0}}
</style>
