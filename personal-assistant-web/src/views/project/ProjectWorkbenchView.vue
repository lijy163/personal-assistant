<template>
  <div class="workbench-page">
    <header class="workbench-header">
      <div>
        <p class="eyebrow">PERSONAL PROJECT TIMELINE</p>
        <h2>个人项目排期工作台</h2>
        <p>与统一日历共用同一套数据，生活、工作及其他安排会自动同步。</p>
      </div>
      <div class="header-actions">
        <el-button @click="resetFilters">显示全部</el-button>
        <el-button @click="router.push('/calendar')">打开统一日历</el-button>
        <el-button type="primary" @click="router.push('/calendar')">新增安排</el-button>
      </div>
    </header>

    <section class="summary-grid">
      <button v-for="card in summaryCards" :key="card.key" :class="['summary-card', { active: sourceFilter === card.key }]" @click="selectCard(card.key)">
        <span>{{ card.label }}</span><strong>{{ card.count }}</strong><small>{{ card.hint }}</small><i :style="{ background: card.color }" /></button>
    </section>

    <section class="workbench-body">
      <div class="gantt-panel">
        <header class="gantt-toolbar">
          <div><h3>事项甘特图</h3><p>按时间排列全部日历事项；已完成事项保留并弱化显示。</p></div>
          <div class="gantt-controls">
            <el-segmented v-model="scale" :options="scaleOptions" />
            <el-button-group><el-button @click="shift(-1)">‹</el-button><el-button @click="today">今天</el-button><el-button @click="shift(1)">›</el-button></el-button-group>
            <span>横向</span><el-button circle size="small" @click="zoom = Math.max(60, zoom - 10)">−</el-button><el-slider v-model="zoom" :min="60" :max="140" :show-tooltip="false" /><el-button circle size="small" @click="zoom = Math.min(140, zoom + 10)">＋</el-button>
          </div>
        </header>

        <div v-loading="loading" class="gantt-scroll">
          <div class="gantt-canvas" :style="canvasStyle">
            <div class="gantt-head">
              <div class="project-heading">事项 / 时间轴</div>
              <div class="date-heading">
                <div v-for="tick in ticks" :key="tick.key" :class="['date-tick', { today: tick.today }]" :style="{ width: tickWidth + 'px' }"><b>{{ tick.label }}</b><small>{{ tick.sub }}</small></div>
              </div>
            </div>
            <div v-if="!filteredEvents.length" class="empty-row">当前范围没有可展示的事项</div>
            <div v-for="event in filteredEvents" :key="event.key" :class="['gantt-row', { completed: isCompleted(event) }]">
              <button class="project-cell" @click="openEvent(event)">
                <b>{{ event.title }}</b><span><i :style="{ background: event.color }" />{{ sourceName(event.sourceType) }} · {{ statusName(event.status) }}</span>
              </button>
              <div class="timeline-cell">
                <span v-for="tick in ticks" :key="tick.key" class="grid-line" :style="{ width: tickWidth + 'px' }" />
                <button class="gantt-bar" :style="barStyle(event)" @click="openEvent(event)">
                  <small>{{ sourceName(event.sourceType) }}</small><b>{{ event.title }}</b><em>{{ isCompleted(event) ? '已完成' : statusName(event.status) }}</em>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <aside class="today-panel">
        <header><div><h3>今日待处理</h3><p>聚焦今天需要推进的事项</p></div><el-button circle @click="router.push('/calendar')">＋</el-button></header>
        <div class="today-date"><small>今天 · 周{{ weekName }}</small><strong>{{ todayText }}</strong></div>
        <div class="today-list">
          <button v-for="event in todayEvents" :key="event.key" :class="{ completed: isCompleted(event) }" @click="openEvent(event)">
            <span class="check">{{ isCompleted(event) ? '✓' : '' }}</span><b>{{ event.title }}</b><small>{{ sourceName(event.sourceType) }} · {{ statusName(event.status) }}</small>
          </button>
          <el-empty v-if="!todayEvents.length" description="今天暂无安排" :image-size="70" />
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { listCalendarEvents, type CalendarEvent } from '@/api/calendar';

type Scale = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
const router = useRouter();
const events = ref<CalendarEvent[]>([]);
const loading = ref(false);
const scale = ref<Scale>('DAY');
const cursor = ref(startOfDay(new Date()));
const zoom = ref(100);
const sourceFilter = ref('ALL');
const scaleOptions = [{ label: '日', value: 'DAY' }, { label: '周', value: 'WEEK' }, { label: '月', value: 'MONTH' }, { label: '年', value: 'YEAR' }];
const allSources = ['LIFE', 'WORK', 'REMINDER', 'LEARNING_PLAN', 'LEARNING_REVIEW', 'LEARNING_RECORD', 'TRADING_PLAN', 'CUSTOM'];
const completedStatuses = ['COMPLETED', 'DONE', 'SENT'];
const todayValue = formatDate(new Date());

const config = computed(() => ({ DAY: { ticks: 35, days: 1, width: 74 }, WEEK: { ticks: 16, days: 7, width: 92 }, MONTH: { ticks: 12, days: 30, width: 112 }, YEAR: { ticks: 12, days: 30, width: 112 } }[scale.value]));
const rangeStart = computed(() => { const date = new Date(cursor.value); date.setDate(date.getDate() - Math.floor(config.value.ticks * config.value.days * .28)); return startOfDay(date); });
const rangeEnd = computed(() => addDays(rangeStart.value, config.value.ticks * config.value.days));
const tickWidth = computed(() => Math.round(config.value.width * zoom.value / 100));
const ticks = computed(() => Array.from({ length: config.value.ticks }, (_, index) => {
  const date = addDays(rangeStart.value, index * config.value.days);
  if (scale.value === 'DAY') return { key: formatDate(date), label: String(date.getDate()), sub: `周${'日一二三四五六'[date.getDay()]}`, today: formatDate(date) === todayValue };
  if (scale.value === 'WEEK') return { key: formatDate(date), label: `${date.getMonth() + 1}/${date.getDate()}`, sub: '周', today: withinToday(date, addDays(date, 7)) };
  return { key: formatDate(date), label: `${date.getMonth() + 1}月`, sub: String(date.getFullYear()), today: withinToday(date, addDays(date, 30)) };
}));
const filteredEvents = computed(() => events.value.filter((event) => {
  if (sourceFilter.value === 'ALL') return true;
  if (sourceFilter.value === 'OTHER') return !['LIFE', 'WORK'].includes(event.sourceType);
  if (sourceFilter.value === 'COMPLETED') return isCompleted(event);
  return event.sourceType === sourceFilter.value;
}));
const todayEvents = computed(() => events.value.filter((event) => event.startAt.slice(0, 10) === todayValue).sort((first, second) => Number(isCompleted(first)) - Number(isCompleted(second))));
const summaryCards = computed(() => [
  { key: 'ALL', label: '全部事项', count: events.value.length, hint: '当前排期总览', color: '#315fe8' },
  { key: 'WORK', label: '工作事项', count: countSource('WORK'), hint: '工作任务与截止时间', color: '#6d42e5' },
  { key: 'LIFE', label: '生活事项', count: countSource('LIFE'), hint: '生活计划与安排', color: '#0891b2' },
  { key: 'OTHER', label: '其他安排', count: events.value.filter((event) => !['LIFE', 'WORK'].includes(event.sourceType)).length, hint: '提醒、学习及交易', color: '#db2777' },
  { key: 'COMPLETED', label: '已完成', count: events.value.filter(isCompleted).length, hint: '历史记录仍然可见', color: '#059669' },
]);
const canvasStyle = computed(() => ({ '--sidebar': '320px', '--timeline-width': `${ticks.value.length * tickWidth.value}px` }));
const todayText = computed(() => `${new Date().getMonth() + 1}月${new Date().getDate()}日`);
const weekName = computed(() => '日一二三四五六'[new Date().getDay()]);

async function load() {
  loading.value = true;
  try { events.value = (await listCalendarEvents(toLocal(rangeStart.value), toLocal(rangeEnd.value), allSources, [])).data; }
  finally { loading.value = false; }
}
function selectCard(key: string) { sourceFilter.value = key; }
function resetFilters() { sourceFilter.value = 'ALL'; }
function countSource(source: string) { return events.value.filter((event) => event.sourceType === source).length; }
function isCompleted(event: CalendarEvent) { return completedStatuses.includes(event.status); }
function sourceName(source: string) { return ({ LIFE: '生活', WORK: '工作', REMINDER: '提醒', LEARNING_PLAN: '学习计划', LEARNING_REVIEW: '学习复习', LEARNING_RECORD: '学习记录', TRADING_PLAN: '交易计划', CUSTOM: '自建日程' } as Record<string, string>)[source] || source; }
function statusName(status: string) { return ({ DRAFT: '草稿', NOT_STARTED: '未开始', PENDING: '待处理', IN_PROGRESS: '进行中', COMPLETED: '已完成', DONE: '已完成', SENT: '已发送' } as Record<string, string>)[status] || status; }
function openEvent(event: CalendarEvent) { void router.push(event.route || '/calendar'); }
function barStyle(event: CalendarEvent) {
  const start = Math.max(0, differenceInDays(new Date(event.startAt), rangeStart.value));
  const rawEnd = event.endAt ? differenceInDays(new Date(event.endAt), rangeStart.value) : start + Math.max(1, config.value.days * .72);
  const left = start / config.value.days * tickWidth.value;
  const width = Math.max(tickWidth.value * .7, (Math.max(start + .7, rawEnd) - start) / config.value.days * tickWidth.value);
  return { left: `${left}px`, width: `${Math.min(width, ticks.value.length * tickWidth.value - left)}px`, '--bar-color': event.color };
}
function shift(direction: number) { cursor.value = addDays(cursor.value, direction * Math.max(1, Math.floor(config.value.ticks * config.value.days * .72))); }
function today() { cursor.value = startOfDay(new Date()); }
function withinToday(start: Date, end: Date) { const today = startOfDay(new Date()); return today >= start && today < end; }
function startOfDay(value: Date) { return new Date(value.getFullYear(), value.getMonth(), value.getDate()); }
function addDays(value: Date, amount: number) { const date = new Date(value); date.setDate(date.getDate() + amount); return date; }
function differenceInDays(value: Date, base: Date) { return (startOfDay(value).getTime() - startOfDay(base).getTime()) / 86400000; }
function formatDate(date: Date) { return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`; }
function toLocal(date: Date) { return `${formatDate(date)}T00:00:00`; }
function two(value: number) { return String(value).padStart(2, '0'); }
watch([scale, cursor], () => void load());
onMounted(load);
</script>

<style scoped>
.workbench-page{min-width:0;color:#172033}.workbench-header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:20px}.eyebrow{margin:0 0 5px!important;color:#315fe8!important;font-size:10px!important;font-weight:800;letter-spacing:.16em}.workbench-header h2{margin:0;font-size:26px}.workbench-header p,.gantt-toolbar p,.today-panel header p{margin:5px 0 0;color:#7b899d;font-size:12px}.header-actions{display:flex;gap:9px}.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(150px,1fr));gap:14px;margin-bottom:18px}.summary-card{position:relative;min-height:116px;padding:18px;overflow:hidden;text-align:left;background:#fff;border:1px solid #e1e8f1;border-radius:18px;box-shadow:0 8px 24px rgb(32 52 85 / 5%)}.summary-card.active{border-color:#7c9cff;box-shadow:0 10px 30px rgb(49 95 232 / 12%)}.summary-card span,.summary-card small,.summary-card strong{position:relative;z-index:1;display:block}.summary-card span{font-weight:700}.summary-card strong{margin:8px 0 3px;font-size:28px}.summary-card small{color:#7b899d}.summary-card>i{position:absolute;right:-24px;bottom:-38px;width:100px;height:100px;opacity:.12;border-radius:50%}.workbench-body{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:18px}.gantt-panel,.today-panel{min-width:0;background:#fff;border:1px solid #e1e8f1;border-radius:22px;box-shadow:0 16px 42px rgb(32 52 85 / 6%)}.gantt-toolbar{display:flex;align-items:center;justify-content:space-between;gap:15px;padding:20px 22px;border-bottom:1px solid #e7edf4}.gantt-toolbar h3,.today-panel h3{margin:0}.gantt-controls{display:flex;align-items:center;gap:8px}.gantt-controls>span{color:#7b899d;font-size:11px}.gantt-controls :deep(.el-slider){width:70px}.gantt-scroll{max-height:720px;overflow:auto}.gantt-canvas{min-width:calc(var(--sidebar) + var(--timeline-width))}.gantt-head,.gantt-row{display:grid;grid-template-columns:var(--sidebar) var(--timeline-width)}.gantt-head{position:sticky;top:0;z-index:5;height:64px;background:#fff;border-bottom:1px solid #e1e8f1}.project-heading{display:flex;align-items:center;padding:0 20px;color:#315fe8;font-size:12px;font-weight:750;border-right:1px solid #e6ecf3}.date-heading{display:flex}.date-tick{display:flex;align-items:center;justify-content:center;flex-direction:column;flex:none;border-right:1px solid #edf1f5}.date-tick b{font-size:13px}.date-tick small{margin-top:3px;color:#8a98aa;font-size:10px}.date-tick.today{color:#fff;background:linear-gradient(135deg,#496cf3,#6045dd)}.date-tick.today small{color:#dce5ff}.gantt-row{min-height:82px;border-bottom:1px solid #edf1f5}.gantt-row.completed{opacity:.58;filter:saturate(.3)}.project-cell{display:flex;justify-content:center;flex-direction:column;gap:8px;padding:13px 20px;text-align:left;background:#fff;border:0;border-right:1px solid #e6ecf3}.project-cell b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.project-cell span{color:#718096;font-size:11px}.project-cell i{display:inline-block;width:7px;height:7px;margin-right:6px;border-radius:50%}.gantt-row.completed .project-cell b{text-decoration:line-through}.timeline-cell{position:relative;display:flex;overflow:hidden;background:#fbfcfe}.grid-line{height:100%;flex:none;border-right:1px solid #edf1f5}.gantt-bar{position:absolute;top:14px;z-index:2;display:grid;height:54px;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:8px;padding:0 14px;overflow:hidden;color:#fff;text-align:left;background:linear-gradient(105deg,var(--bar-color),color-mix(in srgb,var(--bar-color),#3219ae 22%));border:0;border-radius:12px;box-shadow:0 8px 20px color-mix(in srgb,var(--bar-color),transparent 72%)}.gantt-bar small{padding:3px 6px;background:rgb(255 255 255 / 18%);border-radius:5px;font-size:9px}.gantt-bar b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.gantt-bar em{font-size:10px;font-style:normal}.empty-row{padding:70px;color:#94a3b8;text-align:center}.today-panel{height:max-content;overflow:hidden}.today-panel>header{display:flex;align-items:center;justify-content:space-between;padding:20px;border-bottom:1px solid #e7edf4}.today-date{display:flex;flex-direction:column;gap:6px;margin:16px;padding:18px;color:#fff;background:linear-gradient(135deg,#2563eb,#4f46e5);border-radius:17px}.today-date strong{font-size:27px}.today-list{display:flex;flex-direction:column;gap:10px;padding:0 16px 18px}.today-list>button{display:grid;grid-template-columns:22px 1fr;gap:3px 9px;padding:13px;text-align:left;background:#f9fbfe;border:1px solid #e5ebf3;border-radius:13px}.today-list .check{grid-row:1/3;display:grid;width:19px;height:19px;color:#fff;background:#fff;border:1px solid #cbd5e1;border-radius:5px;place-items:center}.today-list button.completed{opacity:.58}.today-list button.completed .check{background:#059669}.today-list button.completed b{text-decoration:line-through}.today-list button small{color:#7b899d}
@media(max-width:1200px){.summary-grid{grid-template-columns:repeat(3,1fr)}.workbench-body{grid-template-columns:1fr}.today-panel{display:none}}
@media(max-width:768px){.workbench-header,.gantt-toolbar{align-items:flex-start;flex-direction:column}.header-actions{width:100%;flex-wrap:wrap}.summary-grid{display:flex;overflow-x:auto}.summary-card{min-width:160px}.gantt-controls{width:100%;flex-wrap:wrap}.gantt-controls :deep(.el-slider){width:90px}.gantt-scroll{max-height:calc(100dvh - 320px)}.gantt-canvas{--sidebar:230px!important}.workbench-header h2{font-size:22px}}
</style>
