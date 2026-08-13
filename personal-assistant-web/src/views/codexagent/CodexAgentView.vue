<template>
  <div class="page">
    <el-card>
      <template #header><div class="header"><span>云端运行配置</span><el-tag :type="cloudReady ? 'success' : 'warning'">{{ cloudReady ? '已配置' : '等待配置' }}</el-tag></div></template>
      <el-alert title="密钥加密保存且不会回显；输入框留空表示保留现有密钥。保存后 Agent 会自动加载，无需重启容器。" type="info" :closable="false"/>
      <el-form :model="cloudForm" label-width="130px" class="cloud-form">
        <el-form-item label="API 服务地址"><el-input v-model="cloudForm.baseUrl" placeholder="https://www.xshoow.cloud/v1"/></el-form-item>
        <el-form-item label="XSHOOW API Key"><el-input v-model="cloudForm.apiKey" type="password" show-password :placeholder="cloudConfig?.apiKeyConfigured ? '已配置，留空表示不修改' : '请输入 API Key'" autocomplete="new-password"/></el-form-item>
        <el-divider content-position="left">云服务器任务</el-divider>
        <el-form-item label="云服务器 Agent"><el-select v-model="cloudForm.managementAgentId" clearable style="width:100%"><el-option v-for="agent in activeAgents" :key="agent.id" :label="agent.name" :value="agent.id"/></el-select></el-form-item>
        <el-form-item label="Agent 令牌"><el-input v-model="cloudForm.managementToken" type="password" show-password :placeholder="cloudConfig?.managementTokenConfigured ? '已配置，留空表示不修改' : '粘贴所选 Agent 的 pa_agent_ 令牌'" autocomplete="new-password"/></el-form-item>
        <el-divider content-position="left">免登录公开问答</el-divider>
        <el-form-item label="启用公开问答"><el-switch v-model="cloudForm.publicEnabled"/></el-form-item>
        <el-form-item label="公开问答 Agent"><el-select v-model="cloudForm.publicAgentId" clearable :disabled="!cloudForm.publicEnabled" style="width:100%"><el-option v-for="agent in activeAgents" :key="agent.id" :label="agent.name" :value="agent.id"/></el-select></el-form-item>
        <el-form-item label="公开 Agent 令牌"><el-input v-model="cloudForm.publicToken" type="password" show-password :disabled="!cloudForm.publicEnabled" :placeholder="cloudConfig?.publicTokenConfigured ? '已配置，留空表示不修改' : '粘贴公开 Agent 的 pa_agent_ 令牌'" autocomplete="new-password"/></el-form-item>
        <el-form-item><el-button type="primary" :loading="savingCloud" @click="saveCloud">保存云端配置</el-button><span v-if="cloudForm.publicEnabled" class="public-link">公开地址：<a href="/ask" target="_blank">/ask</a></span></el-form-item>
      </el-form>
    </el-card>
    <el-card>
      <template #header><div class="header"><span>电脑端 Agent</span><el-button type="primary" @click="agentDialog = true">添加电脑</el-button></div></template>
      <el-alert title="电脑端 Agent 主动领取任务；令牌明文只在创建后显示一次。" type="info" :closable="false"/>
      <el-table :data="agents" class="table">
        <el-table-column prop="name" label="电脑名称" min-width="160"/>
        <el-table-column prop="tokenPrefix" label="令牌前缀" min-width="180"/>
        <el-table-column label="模型" min-width="150"><template #default="{ row }">{{ row.model || 'CLI 默认' }}</template></el-table-column>
        <el-table-column label="推理强度" width="110"><template #default="{ row }">{{ effortLabel(row.reasoningEffort) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="agentTag(row.status)">{{ agentStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="最后在线" width="180"><template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button v-if="!row.revokedAt" link type="primary" @click="openModel(row)">配置</el-button><el-button v-if="!row.revokedAt" link type="danger" @click="revoke(row.id)">撤销</el-button></template></el-table-column>
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
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click.stop="openTask(row)">详情</el-button><el-button v-if="canCancel(row)" link type="danger" :loading="cancellingTaskId === row.id" @click.stop="cancel(row)">{{ row.status === 'PENDING' ? '取消' : '终止' }}</el-button></template></el-table-column>
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
    <el-dialog v-model="modelDialog" title="配置模型与推理强度" width="520px">
      <el-form label-width="90px">
        <el-form-item label="电脑名称"><span>{{ editingAgent?.name }}</span></el-form-item>
        <el-form-item label="API 服务"><el-input value="https://www.xshoow.cloud/v1" disabled/></el-form-item>
        <el-form-item label="模型">
          <el-select v-model="modelForm.model" filterable allow-create default-first-option clearable
            placeholder="不填写则使用 Codex CLI 默认模型" style="width:100%">
            <el-option v-for="model in modelOptions" :key="model.value" :label="model.label" :value="model.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="推理强度">
          <el-select v-model="modelForm.reasoningEffort" style="width:100%">
            <el-option v-for="effort in effortOptions" :key="effort.value" :label="effort.label" :value="effort.value"/>
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert title="模型名称必须与 XSHOOW 支持的模型 ID 完全一致；保存后只影响新创建的任务。" type="info" :closable="false"/>
      <template #footer><el-button @click="modelDialog = false">取消</el-button><el-button type="primary" :loading="savingModel" @click="saveModel">保存</el-button></template>
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
        <div v-if="canCancel(selectedTask)" class="detail-actions"><el-button type="danger" :loading="cancellingTaskId === selectedTask.id" @click="cancel(selectedTask)">{{ selectedTask.status === 'PENDING' ? '取消任务' : '终止执行' }}</el-button></div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="电脑">{{ selectedTask.agentName }}</el-descriptions-item><el-descriptions-item label="项目">{{ selectedTask.projectKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskStatus(selectedTask.status) }}</el-descriptions-item><el-descriptions-item label="权限">{{ selectedTask.permissionMode }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ selectedTask.model || 'CLI 默认' }}</el-descriptions-item><el-descriptions-item label="推理强度">{{ effortLabel(selectedTask.reasoningEffort) }}</el-descriptions-item>
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
import { cancelCodexTask, createCodexAgent, createCodexTask, getCodexCloudConfig, listCodexAgents, listCodexTaskEvents, listCodexTasks, revokeCodexAgent, saveCodexCloudConfig, updateCodexAgentModel, type CodexAgent, type CodexCloudConfig, type CodexTask, type CodexTaskEvent } from '@/api/codexAgent';

const agents = ref<CodexAgent[]>([]); const tasks = ref<CodexTask[]>([]); const events = ref<CodexTaskEvent[]>([]); const loading = ref(false);
const cloudConfig = ref<CodexCloudConfig>(); const savingCloud = ref(false);
const cloudForm = reactive({ managementAgentId: undefined as number | undefined, managementToken: '', publicAgentId: undefined as number | undefined, publicToken: '', apiKey: '', baseUrl: 'https://www.xshoow.cloud/v1', publicEnabled: false });
const agentDialog = ref(false); const tokenDialog = ref(false); const taskDialog = ref(false); const detailVisible = ref(false);
const modelDialog = ref(false); const savingModel = ref(false); const editingAgent = ref<CodexAgent>();
const modelForm = reactive({ model: '', reasoningEffort: 'medium' });
const modelOptions = [
  { label: '5.6 Sol', value: 'gpt-5.6-sol' }, { label: '5.6 Terra', value: 'gpt-5.6-terra' },
  { label: '5.6 Luna', value: 'gpt-5.6-luna' }, { label: '5.5', value: 'gpt-5.5' },
  { label: '5.4', value: 'gpt-5.4' }, { label: '5.4 Mini', value: 'gpt-5.4-mini' },
  { label: '5.2', value: 'gpt-5.2' },
];
const effortOptions = [
  { label: '轻度', value: 'minimal' }, { label: '低', value: 'low' }, { label: '中', value: 'medium' },
  { label: '高', value: 'high' }, { label: '极高', value: 'xhigh' },
];
const agentName = ref('本机 Codex'); const createdToken = ref(''); const selectedTask = ref<CodexTask>();
const cancellingTaskId = ref<number>();
const form = reactive({ agentId: undefined as number | undefined, projectKey: 'personal-assistant', permissionMode: 'READ_ONLY', prompt: '' });
const activeAgents = computed(() => agents.value.filter(agent => !agent.revokedAt));
const cloudReady = computed(() => Boolean(cloudConfig.value?.managementAgentId && cloudConfig.value?.managementTokenConfigured && cloudConfig.value?.apiKeyConfigured));
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
const agentStatus = (value: string) => ({ ONLINE: '在线', OFFLINE: '离线', REVOKED: '已撤销' }[value] || value);
const agentTag = (value: string) => value === 'ONLINE' ? 'success' : value === 'REVOKED' ? 'info' : 'warning';
const effortLabel = (value?: string) => effortOptions.find(item => item.value === (value || 'medium'))?.label || value || '中';
const taskStatus = (value: string) => ({ PENDING: '等待电脑', RUNNING: '执行中', CANCEL_REQUESTED: '正在终止', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已终止' }[value] || value);
const taskTag = (value: string) => ({ PENDING: 'warning', RUNNING: 'primary', CANCEL_REQUESTED: 'warning', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }[value] || 'info');
const canCancel = (task: CodexTask) => task.status === 'PENDING' || task.status === 'RUNNING';
async function load() { loading.value = true; try { const [agentResult, taskResult, cloudResult] = await Promise.all([listCodexAgents(), listCodexTasks(), getCodexCloudConfig()]); agents.value = agentResult.data; tasks.value = taskResult.data; cloudConfig.value = cloudResult.data; Object.assign(cloudForm, { managementAgentId: cloudResult.data.managementAgentId, managementToken: '', publicAgentId: cloudResult.data.publicAgentId, publicToken: '', apiKey: '', baseUrl: cloudResult.data.baseUrl || 'https://www.xshoow.cloud/v1', publicEnabled: cloudResult.data.publicEnabled }); if (!form.agentId && activeAgents.value.length) form.agentId = activeAgents.value[0].id; } finally { loading.value = false; } }
async function saveCloud() { savingCloud.value = true; try { const result = await saveCodexCloudConfig({ managementAgentId: cloudForm.managementAgentId, managementToken: cloudForm.managementToken.trim() || undefined, publicAgentId: cloudForm.publicAgentId, publicToken: cloudForm.publicToken.trim() || undefined, apiKey: cloudForm.apiKey.trim() || undefined, baseUrl: cloudForm.baseUrl.trim(), publicEnabled: cloudForm.publicEnabled }); cloudConfig.value = result.data; cloudForm.managementToken = ''; cloudForm.publicToken = ''; cloudForm.apiKey = ''; ElMessage.success('云端配置已保存，Agent 将在几秒内自动上线'); await load(); } finally { savingCloud.value = false; } }
async function createAgent() { if (!agentName.value.trim()) return ElMessage.warning('请输入电脑名称'); const result = await createCodexAgent({ name: agentName.value.trim() }); createdToken.value = result.data.token; agentDialog.value = false; tokenDialog.value = true; await load(); }
async function copyToken() { await navigator.clipboard.writeText(createdToken.value); ElMessage.success('令牌已复制'); }
async function revoke(id: number) { await ElMessageBox.confirm('撤销后该电脑将不能继续领取或回传任务。', '确认撤销', { type: 'warning' }); await revokeCodexAgent(id); await load(); }
function openModel(agent: CodexAgent) { editingAgent.value = agent; modelForm.model = agent.model || ''; modelForm.reasoningEffort = agent.reasoningEffort || 'medium'; modelDialog.value = true; }
async function saveModel() { if (!editingAgent.value) return; savingModel.value = true; try { await updateCodexAgentModel(editingAgent.value.id, { model: modelForm.model.trim() || undefined, reasoningEffort: modelForm.reasoningEffort }); ElMessage.success('模型配置已保存，将用于后续新任务'); modelDialog.value = false; await load(); } finally { savingModel.value = false; } }
async function createTask() { if (!form.agentId || !form.projectKey.trim() || !form.prompt.trim()) return ElMessage.warning('请完整填写任务'); if (form.permissionMode === 'WORKSPACE_WRITE') await ElMessageBox.confirm('该任务允许 Codex 修改本地项目文件，确认提交？', '写入权限确认', { type: 'warning' }); await createCodexTask({ agentId: form.agentId, projectKey: form.projectKey.trim(), permissionMode: form.permissionMode, prompt: form.prompt.trim() }); form.prompt = ''; taskDialog.value = false; ElMessage.success('任务已提交'); await load(); }
async function cancel(task: CodexTask) { await ElMessageBox.confirm(task.status === 'RUNNING' ? '将立即终止该电脑上正在运行的 Codex 进程，确认继续？' : '确认取消这个等待中的任务？', task.status === 'RUNNING' ? '终止任务' : '取消任务', { type: 'warning', confirmButtonText: task.status === 'RUNNING' ? '立即终止' : '确认取消' }); cancellingTaskId.value = task.id; try { await cancelCodexTask(task.id); ElMessage.success(task.status === 'RUNNING' ? '已发送终止请求' : '任务已取消'); await refreshTask(task.id); if (task.status === 'RUNNING') window.setTimeout(() => refreshTask(task.id), 3000); } finally { cancellingTaskId.value = undefined; } }
async function refreshTask(id: number) { await load(); selectedTask.value = tasks.value.find(item => item.id === id); }
async function openTask(task: CodexTask) { selectedTask.value = task; events.value = (await listCodexTaskEvents(task.id)).data; detailVisible.value = true; }
function prettyEvent(content: string) { try { return JSON.stringify(JSON.parse(content), null, 2); } catch { return content; } }
onMounted(load);
</script>

<style scoped>
.page{display:grid;gap:16px}.header{display:flex;align-items:center;justify-content:space-between;font-weight:600}.cloud-form{max-width:760px;margin-top:20px}.public-link{margin-left:18px;color:#64748b}.public-link a{color:#409eff}.table{margin-top:16px}.token{margin-top:16px}.detail-actions{display:flex;justify-content:flex-end;margin-bottom:16px}.pre,pre{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.65;padding:14px;background:#f6f8fa;border-radius:6px}.error{color:#b42318}.page h3{margin-top:22px}
</style>
