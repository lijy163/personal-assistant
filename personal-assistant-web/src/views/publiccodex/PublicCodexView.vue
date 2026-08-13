<template>
  <main class="public-page">
    <section class="hero">
      <div class="brand">助</div>
      <p class="eyebrow">公开智能问答</p>
      <h1>有什么想了解的？</h1>
      <p class="subtitle">输入问题，AI 助手会在下方给出回答。</p>
      <form class="ask-box" @submit.prevent="submit">
        <el-input v-model="question" type="textarea" :rows="4" maxlength="2000" show-word-limit
          resize="none" placeholder="请输入你的问题…" @keydown.ctrl.enter="submit" />
        <div class="ask-actions">
          <span>Ctrl + Enter 发送</span>
          <el-button type="primary" :loading="submitting" native-type="submit">发送问题</el-button>
        </div>
      </form>
    </section>

    <section v-if="conversations.length" class="answers" aria-live="polite">
      <article v-for="item in conversations" :key="item.taskId" class="conversation">
        <div class="question"><span>你</span><p>{{ item.question }}</p></div>
        <div class="answer"><span>AI</span>
          <div v-if="isPending(item.status)" class="waiting"><i></i><i></i><i></i> 正在思考</div>
          <div v-else-if="item.status === 'FAILED'" class="failed">{{ item.errorMessage || '回答失败，请稍后重试。' }}</div>
          <div v-else class="answer-content" v-html="renderMarkdown(item.answer || '')"></div>
        </div>
      </article>
    </section>
    <p class="privacy">公开页面不会展示系统数据、任务历史或管理功能。</p>
  </main>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify';
import MarkdownIt from 'markdown-it';
import { onBeforeUnmount, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { askPublicCodex, getPublicCodexAnswer, type PublicAnswer } from '@/api/publicCodex';

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true });
const question = ref('');
const submitting = ref(false);
const conversations = ref<PublicAnswer[]>([]);
const timers = new Map<number, number>();

function renderMarkdown(value: string) {
  return DOMPurify.sanitize(markdown.render(value));
}

function isPending(status: string) {
  return status === 'PENDING' || status === 'RUNNING';
}

async function submit() {
  const text = question.value.trim();
  if (!text || submitting.value) return;
  submitting.value = true;
  try {
    const response = await askPublicCodex(text);
    const item: PublicAnswer = { ...response.data, question: text };
    conversations.value.unshift(item);
    question.value = '';
    schedulePoll(item.taskId);
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '暂时无法提交问题，请稍后再试');
  } finally {
    submitting.value = false;
  }
}

function schedulePoll(taskId: number) {
  clearTimer(taskId);
  timers.set(taskId, window.setTimeout(() => poll(taskId), 2500));
}

async function poll(taskId: number) {
  try {
    const response = await getPublicCodexAnswer(taskId);
    const index = conversations.value.findIndex(item => item.taskId === taskId);
    if (index >= 0) conversations.value[index] = response.data;
    if (isPending(response.data.status)) schedulePoll(taskId);
    else clearTimer(taskId);
  } catch {
    schedulePoll(taskId);
  }
}

function clearTimer(taskId: number) {
  const timer = timers.get(taskId);
  if (timer) window.clearTimeout(timer);
  timers.delete(taskId);
}

onBeforeUnmount(() => timers.forEach(timer => window.clearTimeout(timer)));
</script>

<style scoped>
.public-page{min-height:100vh;padding:64px 20px 32px;background:radial-gradient(circle at 15% 10%,#dbeafe 0,transparent 34%),radial-gradient(circle at 85% 20%,#ccfbf1 0,transparent 30%),#f8fafc;color:#172033}.hero,.answers,.privacy{width:min(840px,100%);margin-inline:auto}.hero{text-align:center}.brand{display:grid;place-items:center;width:54px;height:54px;margin:auto;border-radius:17px;background:linear-gradient(135deg,#3b82f6,#14b8a6);color:white;font-size:25px;font-weight:800;box-shadow:0 14px 35px #2563eb42}.eyebrow{margin:20px 0 8px;color:#3970a8;font-weight:700;letter-spacing:.12em}.hero h1{margin:0;font-size:clamp(32px,6vw,52px);letter-spacing:-.04em}.subtitle{margin:12px 0 28px;color:#64748b;font-size:17px}.ask-box{padding:16px;border:1px solid #ffffffcc;border-radius:24px;background:#ffffffdf;box-shadow:0 22px 60px #527aa824;backdrop-filter:blur(16px);text-align:left}.ask-box :deep(.el-textarea__inner){border:0;box-shadow:none;background:transparent;font-size:16px;line-height:1.75}.ask-actions{display:flex;align-items:center;justify-content:space-between;padding:12px 4px 0;color:#94a3b8;font-size:12px}.ask-actions .el-button{min-width:112px;border:0;border-radius:13px;background:linear-gradient(135deg,#3b82f6,#14b8a6)}.answers{margin-top:34px}.conversation{margin-bottom:18px;padding:26px;border:1px solid #e5edf6;border-radius:22px;background:#fff;box-shadow:0 12px 32px #527aa814}.question,.answer{display:grid;grid-template-columns:42px 1fr;gap:14px;align-items:start}.question{padding-bottom:20px;border-bottom:1px solid #edf2f7}.answer{padding-top:20px}.question>span,.answer>span{display:grid;place-items:center;width:36px;height:36px;border-radius:12px;background:#e8f1ff;color:#2563eb;font-weight:700}.answer>span{background:#dcfce7;color:#059669;font-size:12px}.question p{margin:5px 0;line-height:1.7;white-space:pre-wrap}.answer-content{min-width:0;line-height:1.75;overflow-wrap:anywhere}.answer-content :deep(p:first-child){margin-top:5px}.answer-content :deep(pre){overflow:auto;padding:14px;border-radius:12px;background:#0f172a;color:#e2e8f0}.waiting{padding-top:7px;color:#64748b}.waiting i{display:inline-block;width:6px;height:6px;margin-right:4px;border-radius:50%;background:#3b82f6;animation:pulse 1.2s infinite}.waiting i:nth-child(2){animation-delay:.2s}.waiting i:nth-child(3){animation-delay:.4s}.failed{padding-top:7px;color:#dc2626}.privacy{text-align:center;margin-top:26px;color:#94a3b8;font-size:13px}@keyframes pulse{0%,80%,100%{opacity:.25;transform:scale(.8)}40%{opacity:1;transform:scale(1)}}@media(max-width:600px){.public-page{padding-top:36px}.conversation{padding:18px}.question,.answer{grid-template-columns:34px 1fr;gap:10px}.question>span,.answer>span{width:30px;height:30px;border-radius:10px}.ask-actions span{display:none}.ask-actions{justify-content:flex-end}}
</style>
