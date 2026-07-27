<template><div ref="container" class="base-chart" /></template>

<script setup lang="ts">
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps<{ option: EChartsOption }>();
const container = ref<HTMLElement>(); let chart: echarts.ECharts | undefined; let observer: ResizeObserver | undefined;
async function render() { await nextTick(); if (!container.value) return; chart ||= echarts.init(container.value); chart.setOption(props.option, true); }
watch(() => props.option, render, { deep: true });
onMounted(() => { void render(); if (container.value) { observer = new ResizeObserver(() => chart?.resize()); observer.observe(container.value); } });
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose(); });
</script>

<style scoped>.base-chart { width:100%; height:340px; }</style>
