<template><nav class="mobile-bottom-nav" aria-label="手机主导航"><router-link v-for="item in items" :key="item.path" :to="item.path" :class="{active:route.path.startsWith(item.path)}"><span class="nav-icon">{{item.icon}}</span><span>{{item.label}}</span></router-link><button type="button" @click="$emit('collect')"><span class="collect-icon">＋</span><span>记录</span></button></nav></template>
<script setup lang="ts">
import { useRoute } from 'vue-router';

defineEmits<{ collect: [] }>();
const route = useRoute();
const items = [
  { path: '/dashboard', label: '首页', icon: '⌂' },
  { path: '/inbox', label: '收件箱', icon: '▣' },
  { path: '/life', label: '任务', icon: '✓' },
  { path: '/reminders', label: '提醒', icon: '◷' },
];
</script>
<style scoped>.mobile-bottom-nav{display:none}@media(max-width:768px){.mobile-bottom-nav{position:fixed;right:0;bottom:0;left:0;z-index:30;display:grid;grid-template-columns:repeat(5,1fr);padding:6px max(8px,env(safe-area-inset-right)) calc(6px + env(safe-area-inset-bottom)) max(8px,env(safe-area-inset-left));background:rgba(255,255,255,.96);border-top:1px solid #dbe3ee;box-shadow:0 -8px 30px rgba(15,23,42,.08);backdrop-filter:blur(16px)}a,button{display:flex;min-width:0;min-height:48px;flex-direction:column;align-items:center;justify-content:center;gap:2px;color:#64748b;background:none;border:0;font:inherit;font-size:11px;text-decoration:none}.nav-icon,.collect-icon{font-size:20px;line-height:22px}.active{color:#2563eb;font-weight:700}button{color:#0f766e;font-weight:700}.collect-icon{display:grid;width:30px;height:30px;place-items:center;color:white;background:linear-gradient(135deg,#2563eb,#14b8a6);border-radius:50%}}</style>
