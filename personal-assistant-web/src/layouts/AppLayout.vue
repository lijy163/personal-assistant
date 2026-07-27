<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="app-sidebar">
      <div class="brand"><div class="brand-logo">助</div><div><div class="brand-title">个人辅助系统</div><div class="brand-subtitle">Life · Work · Growth</div></div></div>
      <el-menu router :default-active="$route.path" class="sidebar-menu">
        <el-menu-item index="/dashboard">仪表盘</el-menu-item><el-menu-item index="/life">生活事项</el-menu-item><el-menu-item index="/work">工作事项</el-menu-item>
        <el-sub-menu index="learning"><template #title>学习成长</template><el-menu-item index="/learning/plans">学习计划</el-menu-item><el-menu-item index="/learning/records">学习记录</el-menu-item><el-menu-item index="/learning/summaries">学习总结</el-menu-item><el-menu-item index="/learning/growth">成长看板</el-menu-item></el-sub-menu>
        <el-sub-menu index="devlog"><template #title>开发沉淀</template><el-menu-item index="/devlogs">开发记录</el-menu-item><el-menu-item index="/devlog-tokens">推送令牌</el-menu-item></el-sub-menu><el-menu-item index="/inbox">统一收件箱</el-menu-item><el-menu-item index="/finance">个人账单</el-menu-item><el-menu-item index="/reports">自动报告</el-menu-item><el-menu-item index="/stocks">股票关注</el-menu-item><el-menu-item index="/gold">金价关注</el-menu-item><el-menu-item index="/reminders">提醒中心</el-menu-item><el-menu-item index="/scheduler">调度管理</el-menu-item><el-menu-item index="/system">系统设置</el-menu-item><el-menu-item index="/operations">部署运维</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header"><div><div class="page-title">{{ pageTitle }}</div><div class="page-desc">今天也把重要的事情放到系统里</div></div>
        <div class="header-actions"><el-button type="primary" @click="quickNoteVisible = true">快捷新增</el-button>
          <el-dropdown @command="handleUserCommand"><el-avatar class="user-avatar">{{ avatarText }}</el-avatar><template #dropdown><el-dropdown-menu><el-dropdown-item disabled>{{ displayName }}</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </el-header>
      <el-main class="app-main"><router-view /></el-main>
    </el-container>
  </el-container>
  <el-dialog v-model="quickNoteVisible" title="快捷记录" width="520px" @closed="quickNoteContent = ''">
    <el-input v-model="quickNoteContent" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="先记下来，稍后再整理……" />
    <template #footer><el-button @click="quickNoteVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveQuickNote">保存</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { logout } from '@/api/auth';
import { createQuickNote } from '@/api/quickNote';

const route = useRoute(); const router = useRouter();
const pageTitle = computed(() => String(route.meta.title || '个人辅助系统'));
const quickNoteVisible = ref(false); const quickNoteContent = ref(''); const saving = ref(false);
const storedUser = JSON.parse(localStorage.getItem('currentUser') || '{}') as { displayName?: string; username?: string };
const displayName = storedUser.displayName || storedUser.username || '用户';
const avatarText = computed(() => displayName.slice(0, 1));

async function saveQuickNote() {
  const content = quickNoteContent.value.trim();
  if (!content) { ElMessage.warning('请输入记录内容'); return; }
  saving.value = true;
  try { await createQuickNote(content); ElMessage.success('已保存到待整理记录'); quickNoteVisible.value = false; }
  finally { saving.value = false; }
}
async function handleUserCommand(command: string) {
  if (command !== 'logout') return;
  try { await logout(); } finally { localStorage.removeItem('token'); localStorage.removeItem('currentUser'); await router.replace('/login'); }
}
</script>