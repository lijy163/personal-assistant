<template>
  <el-card>
    <template #header>{{ title }}</template>
    <el-empty description="模块待开发">
      <el-button type="primary">后续实现</el-button>
    </el-empty>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const title = computed(() => String(route.meta.title || '模块'));
</script>
