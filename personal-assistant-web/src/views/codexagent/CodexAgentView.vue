<template>
  <div class="page">
    <el-card>
      <template #header><div class="header"><span>电脑端 Agent</span><el-button type="primary" @click="agentDialog = true">添加电脑</el-button></div></template>
      <el-alert title="电脑端 Agent 主动领取任务；令牌明文只在创建后显示一次。" type="info" :closable="false"/>
      <el-table :data="agents" class="table">
        <el-table-column prop="name" label="电脑名称" min-width="160"/>
        <el-table-column prop="tokenPrefix" label="令牌前缀" min-width="180"/>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="agentTag(row.status)">{{ agentStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="最后在线" width="180"><template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="!row.revokedAt" link type="danger" @click="revoke(row.id)">撤销</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header><div class="header"><span>远程 Codex 任务</span><div><el-button @click="load">刷新</el-button><el-button type="primary" :disabled="!activeAgents.length" @click="taskDialog = true">新建任务</el-button></div></div></template>
      <el-table :data="tasks" v-loading="loading" @row-click="openTask">
        <el-table-column prop="requestedAt" label="创建时间" width="180"><template #default="{ row }">{{ formatTime(row.requestedAt) }}</template></el-table-column>
        <el-table-column prop="agentName" label="电脑" width="140"/>
        <el-table-column prop="projectKey" label="项目标识" width="170"/>
        <el-table-column prop="prompt" label="任务" min-width="260" show-overflow-tooltip/>
        <el-table-column label="权限" width="110"><template #default="{ row }">{{ row.permissionMode === 'READ_ONLY' ? '只读' : '允许修改' }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="taskTag(row.status)">{{ taskStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" @click.stop="openTask(row)">详情</el-button><el-button v-if="row.status === 'PENDING'" link type="danger" @click.stop="cancel(row.id)">取消</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !tasks.length" description="暂无远程任务"/>
    </el-card>

    <el-dialog v-model="agentDialog" title="添加电脑端 Agent" width="480px">
      <el-form label-width="90px"><el-form-item label="电脑名称"><el-input v-model="agentName" placeholder="例如：家里电脑"/></el-form-item></el-form>
      <template #footer><el-button @click="agentDialog = false">取消</el-button><el-button type="primary" @click="createAgent">创建</el-button></template>
    </el-dialog>
    <el-dialog v-model="tokenDialog" title="请立即复制 Agent 令牌" width="640px" :close-on-click-modal="false">
      <el-alert title="关闭后不能再次查看。请将令牌配置到电脑的 PA_AGENT_TOKEN 环境变量。" type="warning" :closable="false"/>
      <el-input v-model="createdToken" readonly class="token"><template #append><el-button @click="copyToken">复制</el-button></template></el-input>
    </el-dialog>
    <el-dialog v-model="taskDialog" title="新建远程 Codex 任务" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="执行电脑"><el-select v-model="form.agentId" style="width:100%"><el-option v-for="agent in activeAgents" :key="agent.id" :label="agent.name" :value="agent.id"/></el-select></el-form-item>
        <el-form-item label="项目标识"><el-input v-model="form.projectKey" placeholder="必须与电脑 config.json 中的 projects 键一致"/></el-form-item>
        <el-form-item label="执行权限"><el-radio-group v-model="form.permissionMode"><el-radio value="READ_ONLY">只读分析</el-radio><el-radio value="WORKSPACE_WRITE">允许修改项目</el-radio></el-radio-group></el-form-item>
        <el-form-item label="任务内容"><el-input v-model="form.prompt" type="textarea" :rows="7" maxlength="20000" show-word-limit/></el-form-item>
      </el-form>
      <el-alert v-if="form.permissionMode === 'WORKSPACE_WRITE'" title="此任务可以修改电脑上的项目文件，请确认项目和任务内容。" type="warning" :closable="false"/>
      <template #footer><el-button @click="taskDialog = false">取消</el-button><el-button type="primary" @click="createTask">提交任务</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="Codex 任务详情" size="60%">
      <template v-if="selectedTask">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="电脑">{{ selectedTask.agentName }}</el-descriptions-item><el-descriptions-item label="项目">{{ selectedTask.projectKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskStatus(selectedTask.status) }}</el-descriptions-item><el-descriptions-item label="权限">{{ selectedTask.permissionMode }}</el-descriptions-item>
          <el-descriptions-item label="Thread ID" :span="2">{{ selectedTask.threadId || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h3>任务内容</h3><div class="pre">{{ selectedTask.prompt }}</div>
        <template v-if="selectedTask.finalResponse"><h3>Codex 回复</h3><pre>{{ selectedTask.finalResponse }}</pre></template>
        <template v-if="selectedTask.errorMessage"><h3>失败原因</h3><pre class="error">{{ selectedTask.errorMessage }}</pre></template>
        <h3>执行事件</h3><el-timeline><el-timeline-item v-for="event in events" :key="event.id" :timestamp="formatTime(event.createdAt)"><b>{{ event.eventType }}</b><pre>{{ prettyEvent(event.content) }}</pre></el-timeline-item></el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { cancelCodexTask, createCodexAgent, createCodexTask, listCodexAgents, listCodexTaskEvents, listCodexTasks, revokeCodexAgent, type CodexAgent, type CodexTask, type CodexTaskEvent } from '@/api/codexAgent';

const agents = ref<CodexAgent[]>([]); const tasks = ref<CodexTask[]>([]); const events = ref<CodexTaskEvent[]>([]); const loading = ref(false);
const agentDialog = ref(false); const tokenDialog = ref(false); const taskDialog = ref(false); const detailVisible = ref(false);
const agentName = ref('本机 Codex'); const createdToken = ref(''); const selectedTask = ref<CodexTask>();
const form = reactive({ agentId: undefined as number | undefined, projectKey: 'personal-assistant', permissionMode: 'READ_ONLY', prompt: '' });
const activeAgents = computed(() => agents.value.filter(agent => !agent.revokedAt));
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
const agentStatus = (value: string) => ({ ONLINE: '在线', OFFLINE: '离线', REVOKED: '已撤销' }[value] || value);
const agentTag = (value: string) => value === 'ONLINE' ? 'success' : value === 'REVOKED' ? 'info' : 'warning';
const taskStatus = (value: string) => ({ PENDING: '等待电脑', RUNNING: '执行中', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[value] || value);
const taskTag = (value: string) => ({ PENDING: 'warning', RUNNING: 'primary', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }[value] || 'info');
async function load() { loading.value = true; try { [agents.value, tasks.value] = await Promise.all([(await listCodexAgents()).data, (await listCodexTasks()).data]); if (!form.agentId && activeAgents.value.length) form.agentId = activeAgents.value[0].id; } finally { loading.value = false; } }
async function createAgent() { if (!agentName.value.trim()) return ElMessage.warning('请输入电脑名称'); const result = await createCodexAgent({ name: agentName.value.trim() }); createdToken.value = result.data.token; agentDialog.value = false; tokenDialog.value = true; await load(); }
async function copyToken() { await navigator.clipboard.writeText(createdToken.value); ElMessage.success('令牌已复制'); }
async function revoke(id: number) { await ElMessageBox.confirm('撤销后该电脑将不能继续领取或回传任务。', '确认撤销', { type: 'warning' }); await revokeCodexAgent(id); await load(); }
async function createTask() { if (!form.agentId || !form.projectKey.trim() || !form.prompt.trim()) return ElMessage.warning('请完整填写任务'); if (form.permissionMode === 'WORKSPACE_WRITE') await ElMessageBox.confirm('该任务允许 Codex 修改本地项目文件，确认提交？', '写入权限确认', { type: 'warning' }); await createCodexTask({ agentId: form.agentId, projectKey: form.projectKey.trim(), permissionMode: form.permissionMode, prompt: form.prompt.trim() }); form.prompt = ''; taskDialog.value = false; ElMessage.success('任务已提交'); await load(); }
async function cancel(id: number) { await cancelCodexTask(id); ElMessage.success('任务已取消'); await load(); }
async function openTask(task: CodexTask) { selectedTask.value = task; events.value = (await listCodexTaskEvents(task.id)).data; detailVisible.value = true; }
function prettyEvent(content: string) { try { return JSON.stringify(JSON.parse(content), null, 2); } catch { return content; } }
onMounted(load);
</script>

<style scoped>
.page{display:grid;gap:16px}.header{display:flex;align-items:center;justify-content:space-between;font-weight:600}.table{margin-top:16px}.token{margin-top:16px}.pre,pre{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.65;padding:14px;background:#f6f8fa;border-radius:6px}.error{color:#b42318}.page h3{margin-top:22px}
</style>
