<template>
  <div class="nav-wrap">
    <div class="brand">
      <div class="brand-logo">助</div>
      <div><div class="brand-title">个人辅助系统</div><div class="brand-subtitle">Life · Work · Growth</div></div>
    </div>
    <el-menu router :default-active="$route.path" :default-openeds="openGroups" :unique-opened="true" class="sidebar-menu" @open="handleOpen" @select="$emit('navigate')">
      <el-sub-menu index="home">
        <template #title><el-icon><House /></el-icon><span>首页</span></template>
        <el-menu-item index="/dashboard">每日驾驶舱</el-menu-item><el-menu-item index="/inbox">统一收件箱</el-menu-item><el-menu-item index="/inbox/organize">收件箱整理</el-menu-item>
      </el-sub-menu>
      <el-sub-menu index="tasks">
        <template #title><el-icon><Calendar /></el-icon><span>事务与生活</span></template>
        <el-menu-item index="/calendar">统一日历</el-menu-item><el-menu-item index="/project-workbench">项目排期工作台</el-menu-item><el-menu-item index="/life">生活事项</el-menu-item><el-menu-item index="/work">工作事项</el-menu-item><el-menu-item index="/reminders">提醒中心</el-menu-item><el-menu-item index="/automation">自动化规则</el-menu-item><el-menu-item index="/life-management">习惯、联系人与资产</el-menu-item>
      </el-sub-menu>
      <el-sub-menu index="knowledge">
        <template #title><el-icon><Reading /></el-icon><span>知识沉淀</span></template>
        <el-menu-item index="/knowledge">个人知识库与 AI</el-menu-item><el-menu-item index="/learning/plans">学习计划</el-menu-item><el-menu-item index="/learning/records">学习记录</el-menu-item><el-menu-item index="/learning/summaries">学习总结</el-menu-item><el-menu-item index="/learning/growth">成长看板</el-menu-item><el-menu-item index="/devlogs">开发记录</el-menu-item><el-menu-item index="/blog/manage">博客管理</el-menu-item>
        <a class="external-menu-item" href="/blog" target="_blank" rel="noopener">访问工作记录 ↗</a>
        <a class="external-menu-item" href="/rain7/" target="_blank" rel="noopener">访问 Rain7 ↗</a>
      </el-sub-menu>
      <el-sub-menu index="finance">
        <template #title><el-icon><TrendCharts /></el-icon><span>财务市场</span></template>
        <el-menu-item index="/finance">个人账单</el-menu-item><el-menu-item index="/finance-planning">预算与净资产</el-menu-item><el-menu-item index="/stocks">股票关注</el-menu-item><el-menu-item index="/trading-reviews">交易复盘</el-menu-item><el-menu-item index="/gold">金价关注</el-menu-item><el-menu-item index="/reports">自动报告</el-menu-item>
      </el-sub-menu>
      <el-sub-menu index="system">
        <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
        <el-menu-item index="/security-audit">安全审计</el-menu-item><el-menu-item index="/scheduler">调度管理</el-menu-item><el-menu-item index="/devlog-tokens">推送令牌</el-menu-item><el-menu-item index="/codex-agents">远程 Codex</el-menu-item><el-menu-item index="/system">系统设置</el-menu-item><el-menu-item index="/operations">部署运维</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { Calendar, House, Reading, Setting, TrendCharts } from '@element-plus/icons-vue';
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { preloadRoutes } from '@/router/routePreload';

defineEmits<{ navigate: [] }>();
const route = useRoute();
const router = useRouter();
const groupRoutes: Record<string, string[]> = {
  home: ['/dashboard', '/inbox', '/inbox/organize'],
  tasks: ['/calendar', '/project-workbench', '/life', '/work', '/reminders', '/automation', '/life-management'],
  knowledge: ['/knowledge', '/learning/plans', '/learning/records', '/learning/summaries', '/learning/growth', '/devlogs', '/blog/manage', '/blog'],
  finance: ['/finance', '/finance-planning', '/stocks', '/trading-reviews', '/gold', '/reports'],
  system: ['/security-audit', '/scheduler', '/devlog-tokens', '/codex-agents', '/system', '/operations'],
};
const group = computed(() => {
  const path = route.path;
  if (path === '/dashboard' || path.startsWith('/inbox')) return 'home';
  if (groupRoutes.tasks.some((item) => path.startsWith(item))) return 'tasks';
  if (groupRoutes.knowledge.some((item) => path.startsWith(item))) return 'knowledge';
  if (groupRoutes.finance.some((item) => path.startsWith(item))) return 'finance';
  return 'system';
});
const openGroups = computed(() => [localStorage.getItem('navigation-open-group') || group.value]);

function handleOpen(index: string) {
  localStorage.setItem('navigation-open-group', index);
  preloadRoutes(router, groupRoutes[index] ?? []);
}

onMounted(() => preloadRoutes(router, groupRoutes[group.value]));
</script>

<style scoped>
.nav-wrap{height:100%;overflow-y:auto}.sidebar-menu :deep(.el-sub-menu__title){font-weight:750}.sidebar-menu :deep(.el-sub-menu__title .el-icon){color:#64748b}.sidebar-menu :deep(.el-menu-item){font-size:13px}.external-menu-item{display:flex;align-items:center;height:44px;padding:0 20px 0 54px;margin:5px 0;color:#516072;font-size:13px;text-decoration:none;border-radius:14px}.external-menu-item:hover{color:#2563eb;background:rgb(37 99 235 / 8%)}
</style>
