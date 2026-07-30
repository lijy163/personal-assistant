<template>
  <div class="app-frame">
    <PwaStatus />
    <el-container class="app-shell">
      <el-aside width="232px" class="app-sidebar desktop-sidebar"><AppNavigation /></el-aside>
      <el-container>
        <el-header class="app-header">
          <div class="title-row"><el-button class="mobile-menu-button" text @click="mobileMenu=true">☰</el-button><div><div class="page-title">{{pageTitle}}</div><div class="page-desc">今天也把重要的事情放到系统里</div></div></div>
          <div class="header-actions"><el-input v-model="searchKeyword" class="global-search" placeholder="全局搜索" clearable @keyup.enter="search"><template #append><el-button @click="search">搜索</el-button></template></el-input><el-button class="desktop-quick" type="primary" @click="openCollector">快捷新增</el-button><el-dropdown @command="handleUserCommand"><el-avatar class="user-avatar">{{avatarText}}</el-avatar><template #dropdown><el-dropdown-menu><el-dropdown-item disabled>{{displayName}}</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
        </el-header>
        <el-main class="app-main"><router-view /></el-main>
      </el-container>
    </el-container>
    <el-drawer v-model="mobileMenu" direction="ltr" size="min(86vw, 320px)" :with-header="false"><AppNavigation @navigate="mobileMenu=false" /></el-drawer>
    <el-dialog v-model="searchVisible" title="全局搜索" width="min(760px, 94vw)" class="mobile-full-dialog"><el-input v-model="searchKeyword" placeholder="搜索任务、开发日志、账单、学习总结、记录和股票" @keyup.enter="search"/><div class="search-results"><el-empty v-if="!searchResults.length" description="未找到相关内容"/><button v-for="item in searchResults" :key="item.type+item.id" class="search-item" @click="goResult(item)"><el-tag size="small">{{typeName(item.type)}}</el-tag><div><b>{{item.title}}</b><p>{{item.snippet}}</p></div><small>{{formatTime(item.occurredAt)}}</small></button></div></el-dialog>
    <MobileBottomNav @collect="openCollector" />
  </div>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { logout } from '@/api/auth';
import { globalSearch, type SearchResult } from '@/api/productivity';
import MobileBottomNav from '@/components/MobileBottomNav.vue';
import PwaStatus from '@/components/PwaStatus.vue';
import AppNavigation from './AppNavigation.vue';

const route = useRoute();
const router = useRouter();
const pageTitle = computed(() => String(route.meta.title || '个人辅助系统'));
const mobileMenu = ref(false);
const searchKeyword = ref('');
const searchVisible = ref(false);
const searchResults = ref<SearchResult[]>([]);
const storedUser = JSON.parse(localStorage.getItem('currentUser') || '{}') as { displayName?: string; username?: string };
const displayName = storedUser.displayName || storedUser.username || '用户';
const avatarText = computed(() => displayName.slice(0, 1));

async function openCollector() { await router.push({ path: '/inbox', query: { collect: String(Date.now()) } }); }
async function search() {
  if (searchKeyword.value.trim().length < 2) { ElMessage.warning('至少输入 2 个字符'); return; }
  searchResults.value = (await globalSearch(searchKeyword.value.trim())).data;
  searchVisible.value = true;
}
async function goResult(item: SearchResult) { searchVisible.value = false; await router.push(item.route); }
async function handleUserCommand(command: string) {
  if (command !== 'logout') return;
  try { await logout(); } finally {
    localStorage.removeItem('token');
    localStorage.removeItem('currentUser');
    await router.replace('/login');
  }
}
const typeName = (type: string) => ({ TASK: '任务', DEVLOG: '开发', FINANCE: '账单', QUICK_NOTE: '记录', LEARNING: '学习', STOCK: '股票', TRADING_REVIEW: '交易复盘', TRADE: '交易' }[type] || type);
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
</script>
<style scoped>.app-frame{min-height:100vh}.title-row{display:flex;align-items:center;gap:8px}.mobile-menu-button{display:none;font-size:22px}.global-search{width:280px}.search-results{max-height:520px;overflow:auto;margin-top:12px}.search-item{width:100%;display:grid;grid-template-columns:70px 1fr 150px;gap:12px;text-align:left;align-items:start;border:0;border-bottom:1px solid #eee;background:white;padding:14px;cursor:pointer}.search-item:hover{background:#f8fafc}.search-item p{margin:6px 0;color:#64748b;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.search-item small{text-align:right;color:#94a3b8}@media(max-width:768px){.desktop-sidebar{display:none}.mobile-menu-button{display:inline-flex}.app-header{height:60px;padding:0 max(12px,env(safe-area-inset-right)) 0 max(12px,env(safe-area-inset-left))}.page-desc,.desktop-quick{display:none}.global-search{width:44px}.global-search :deep(input){display:none}.header-actions{gap:6px}.app-main{padding:12px max(12px,env(safe-area-inset-right)) calc(84px + env(safe-area-inset-bottom)) max(12px,env(safe-area-inset-left));overflow-x:hidden}.search-item{grid-template-columns:60px 1fr}.search-item small{display:none}}</style>