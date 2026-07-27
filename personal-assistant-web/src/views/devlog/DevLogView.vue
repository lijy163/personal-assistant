<template>
  <div class="devlog-page">
    <el-card>
      <template #header><div class="toolbar"><span>开发记录</span><div><el-input v-model="keyword" clearable placeholder="搜索标题、目标、改动或标签" @keyup.enter="load"/><el-button type="primary" @click="load">搜索</el-button></div></div></template>
      <el-table :data="logs" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="occurredAt" label="时间" width="180"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column>
        <el-table-column prop="title" label="标题" min-width="240"/>
        <el-table-column prop="projectName" label="项目" width="160"/>
        <el-table-column prop="branchName" label="分支" width="150" show-overflow-tooltip/>
        <el-table-column prop="tags" label="标签" min-width="180" show-overflow-tooltip/>
        <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !logs.length" description="还没有开发记录，请先创建 PAT 并运行推送脚本"/>
    </el-card>

    <el-drawer v-model="detailVisible" title="开发记录详情" size="58%">
      <template v-if="detail">
        <h2>{{ detail.title }}</h2>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ formatTime(detail.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="分支">{{ detail.branchName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交">{{ detail.commitHash || '-' }}</el-descriptions-item>
          <el-descriptions-item label="仓库" :span="2">{{ detail.repository || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ detail.tags || '-' }}</el-descriptions-item>
        </el-descriptions>
        <section><h3>任务目标</h3><div class="pre-wrap">{{ detail.taskGoal }}</div></section>
        <section><h3>核心修改</h3><div class="pre-wrap">{{ detail.coreChanges }}</div></section>
        <section v-if="detail.technicalDecisions"><h3>技术决策</h3><div class="pre-wrap">{{ detail.technicalDecisions }}</div></section>
        <section v-if="detail.problemSolution"><h3>问题与解决</h3><div class="pre-wrap">{{ detail.problemSolution }}</div></section>
        <section v-if="detail.verificationResult"><h3>验证结果</h3><div class="pre-wrap">{{ detail.verificationResult }}</div></section>
        <section><h3>精炼 Markdown</h3><pre class="markdown">{{ detail.markdownContent }}</pre></section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getDevLog, listDevLogs, type DevLog, type DevLogSummary } from '@/api/devlog';

const keyword = ref(''); const logs = ref<DevLogSummary[]>([]); const loading = ref(false);
const detailVisible = ref(false); const detail = ref<DevLog>();
const formatTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
async function load() { loading.value = true; try { logs.value = (await listDevLogs({ keyword: keyword.value || undefined })).data; } finally { loading.value = false; } }
async function openDetail(row: DevLogSummary) { detail.value = (await getDevLog(row.id)).data; detailVisible.value = true; }
onMounted(load);
</script>

<style scoped>
.toolbar { display:flex; align-items:center; justify-content:space-between; font-weight:600; }
.toolbar > div { display:flex; gap:8px; width:460px; }.devlog-page section { margin-top:22px; }
.pre-wrap { white-space:pre-wrap; line-height:1.7; }.markdown { white-space:pre-wrap; padding:16px; background:#f6f8fa; border-radius:6px; line-height:1.65; }
</style>
