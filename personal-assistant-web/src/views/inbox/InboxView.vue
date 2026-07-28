<template>
  <div class="inbox-page">
    <el-card class="collector-card">
      <template #header><div class="header"><div><b>快速收集</b><small>文字、图片、文件和录音统一进入待整理收件箱</small></div><el-tag v-if="draftSaved" type="info">草稿已保存</el-tag></div></template>
      <el-input ref="contentInput" v-model="form.content" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="记下想法、任务、消费或提醒……" />
      <div class="capture-actions">
        <label class="capture-button">📷 拍照/图片<input type="file" accept="image/*" capture="environment" multiple @change="selectFiles" /></label>
        <label class="capture-button">📎 选择文件<input type="file" :accept="fileAccept" multiple @change="selectFiles" /></label>
        <label class="capture-button">🎙️ 录音文件<input type="file" accept="audio/*" capture multiple @change="selectFiles" /></label>
      </div>
      <div v-if="files.length" class="file-chips"><el-tag v-for="(file,index) in files" :key="file.name+index" closable @close="files.splice(index,1)">{{file.name}} · {{size(file.size)}}</el-tag></div>
      <el-collapse class="details"><el-collapse-item title="标签、备注和记录时间"><div class="detail-grid"><el-input v-model="form.tags" placeholder="标签，用逗号分隔"/><el-date-picker v-model="form.recordedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="记录时间" style="width:100%"/><el-input v-model="form.remark" class="full" type="textarea" :rows="2" maxlength="2000" placeholder="补充备注"/></div></el-collapse-item></el-collapse>
      <div class="submit"><span v-if="!online" class="offline-tip">离线时可继续编辑，联网后再提交</span><el-button type="primary" size="large" :loading="saving" :disabled="!online" @click="submit">加入收件箱</el-button></div>
    </el-card>

    <el-card class="items-card"><template #header><div class="header"><div><b>待整理</b><small>建议分类仅供参考，确认前不会创建业务数据</small></div><el-button @click="load">刷新</el-button></div></template>
      <div class="desktop-list"><el-table :data="items"><el-table-column label="时间" width="170"><template #default="{row}">{{time(row.recordedAt||row.createdAt)}}</template></el-table-column><el-table-column prop="content" label="原始内容" min-width="250"/><el-table-column label="附件" width="100"><template #default="{row}">{{row.attachments?.length||0}} 个</template></el-table-column><el-table-column label="建议" width="130"><template #default="{row}"><el-tag>{{typeName(row.suggestedType)}} {{Math.round(row.confidence*100)}}%</el-tag></template></el-table-column><el-table-column label="确认类型" width="170"><template #default="{row}"><el-select :model-value="row.confirmedType||row.suggestedType" @change="onConfirm(row.id,$event)"><el-option v-for="type in types" :key="type.value" :label="type.label" :value="type.value"/></el-select></template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" @click="archive(row.id)">归档</el-button></template></el-table-column></el-table></div>
      <div class="mobile-list"><article v-for="item in items" :key="item.id" class="inbox-item"><div class="item-top"><el-tag size="small">{{typeName(item.suggestedType)}} {{Math.round(item.confidence*100)}}%</el-tag><time>{{time(item.recordedAt||item.createdAt)}}</time></div><p>{{item.content}}</p><div v-if="item.tags" class="meta"># {{item.tags}}</div><div v-if="item.remark" class="meta">{{item.remark}}</div><div v-if="item.attachments?.length" class="attachments"><button v-for="file in item.attachments" :key="file.id" type="button" @click="downloadInboxAttachment(file.id,file.originalName)">{{kindIcon(file.fileKind)}} {{file.originalName}} · {{size(file.fileSize)}}</button></div><div class="item-actions"><el-select :model-value="item.confirmedType||item.suggestedType" @change="onConfirm(item.id,$event)"><el-option v-for="type in types" :key="type.value" :label="type.label" :value="type.value"/></el-select><el-button type="danger" plain @click="archive(item.id)">归档</el-button></div></article></div>
      <el-empty v-if="!items.length" description="收件箱已清空"/>
    </el-card>
  </div>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { archiveInbox, collectInbox, confirmInbox, downloadInboxAttachment, listInbox, type InboxItem } from '@/api/productivity';

const DRAFT_KEY = 'personal-assistant-inbox-draft-v1';
const route = useRoute();
const contentInput = ref();
const saving = ref(false);
const items = ref<InboxItem[]>([]);
const files = ref<File[]>([]);
const online = ref(navigator.onLine);
const draftSaved = ref(false);
const fileAccept = 'image/jpeg,image/png,image/webp,image/heic,image/heif,audio/*,.pdf,.txt,.csv,.doc,.docx,.xls,.xlsx';
const types = [{ label: '任务', value: 'TASK' }, { label: '提醒', value: 'REMINDER' }, { label: '消费', value: 'EXPENSE' }, { label: '学习', value: 'LEARNING' }, { label: '普通记录', value: 'NOTE' }];
const form = reactive({ content: '', tags: '', remark: '', source: 'MOBILE_WEB', recordedAt: '' });
let draftTimer: number | undefined;

async function load() { items.value = (await listInbox('PENDING')).data; }
function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const merged = [...files.value, ...Array.from(input.files || [])];
  if (merged.length > 5) { ElMessage.warning('单次最多上传 5 个附件'); files.value = merged.slice(0, 5); }
  else files.value = merged;
  input.value = '';
}
async function submit() {
  if (!form.content.trim() && !files.value.length) { ElMessage.warning('请输入文字或选择附件'); return; }
  if (files.value.some(file => file.size > 15 * 1024 * 1024)) { ElMessage.warning('单个附件不能超过 15 MB'); return; }
  saving.value = true;
  try {
    const item = (await collectInbox({ ...form }, files.value)).data;
    ElMessage.success(`已收集，建议分类：${typeName(item.suggestedType)}`);
    Object.assign(form, { content: '', tags: '', remark: '', source: 'MOBILE_WEB', recordedAt: '' });
    files.value = [];
    localStorage.removeItem(DRAFT_KEY);
    draftSaved.value = false;
    await load();
  } finally { saving.value = false; }
}
function onConfirm(id: number, value: unknown) { void confirm(id, String(value)); }
async function confirm(id: number, type: string) { await confirmInbox(id, type); ElMessage.success('分类已确认，原文和附件已保留'); await load(); }
async function archive(id: number) { await archiveInbox(id); await load(); }
function saveDraft() {
  clearTimeout(draftTimer);
  draftTimer = window.setTimeout(() => {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(form));
    draftSaved.value = Boolean(form.content || form.tags || form.remark);
  }, 300);
}
function restoreDraft() {
  try { Object.assign(form, JSON.parse(localStorage.getItem(DRAFT_KEY) || '{}')); draftSaved.value = Boolean(form.content || form.tags || form.remark); }
  catch { localStorage.removeItem(DRAFT_KEY); }
}
function focusCollector() { void nextTick(() => contentInput.value?.focus()); }
const typeName = (value: string) => types.find(item => item.value === value)?.label || value;
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const size = (value: number) => value < 1024 ? `${value} B` : value < 1048576 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1048576).toFixed(1)} MB`;
const kindIcon = (kind: string) => kind === 'IMAGE' ? '🖼️' : kind === 'AUDIO' ? '🎙️' : '📄';
function onOnline() { online.value = true; ElMessage.success('网络已恢复，可以提交草稿'); }
function onOffline() { online.value = false; }
watch(form, saveDraft, { deep: true });
watch(() => route.query.collect, focusCollector);
onMounted(async () => {
  restoreDraft();
  window.addEventListener('online', onOnline);
  window.addEventListener('offline', onOffline);
  await load();
  if (route.query.collect) focusCollector();
});
onBeforeUnmount(() => {
  clearTimeout(draftTimer);
  window.removeEventListener('online', onOnline);
  window.removeEventListener('offline', onOffline);
});
</script>
<style scoped>.inbox-page{display:flex;flex-direction:column;gap:16px}.header{display:flex;align-items:center;justify-content:space-between;gap:12px}.header>div{display:flex;flex-direction:column;gap:4px}.header small,.offline-tip,.meta,time{color:#64748b;font-size:12px}.capture-actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:12px}.capture-button{display:inline-flex;min-height:40px;align-items:center;padding:0 14px;color:#334155;background:#f8fafc;border:1px solid #cbd5e1;border-radius:10px;cursor:pointer}.capture-button input{display:none}.file-chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.details{margin-top:10px}.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.full{grid-column:1/-1}.submit{display:flex;align-items:center;justify-content:flex-end;gap:12px;margin-top:12px}.mobile-list{display:none}.attachments{display:flex;flex-direction:column;gap:6px;margin:10px 0}.attachments button{padding:8px;text-align:left;color:#2563eb;background:#eff6ff;border:0;border-radius:8px}.item-actions{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:12px}@media(max-width:768px){.collector-card,.items-card{border-radius:16px}.desktop-list{display:none}.mobile-list{display:flex;flex-direction:column;gap:12px}.inbox-item{padding:14px;background:#fff;border:1px solid #e2e8f0;border-radius:14px}.item-top{display:flex;align-items:center;justify-content:space-between;gap:8px}.inbox-item p{margin:12px 0;white-space:pre-wrap;word-break:break-word}.capture-actions{display:grid;grid-template-columns:repeat(3,1fr)}.capture-button{justify-content:center;padding:6px;font-size:12px;text-align:center}.detail-grid{grid-template-columns:1fr}.full{grid-column:auto}.submit{align-items:stretch;flex-direction:column}.submit .el-button{width:100%;min-height:48px}.header small{max-width:250px}.collector-card :deep(.el-card__body),.items-card :deep(.el-card__body){padding:14px}}</style>