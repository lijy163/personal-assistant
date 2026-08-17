<template>
  <div class="calendar-page">
    <section class="calendar-toolbar">
      <div><h2>{{ heading }}</h2><p>Asia/Shanghai · 聚合现有业务数据，不复制源记录</p></div>
      <div class="toolbar-actions">
        <el-button type="primary" @click="openCreate()">＋ 新增</el-button>
        <el-segmented v-model="view" :options="viewOptions" />
        <el-button-group><el-button @click="shift(-1)">‹</el-button><el-button @click="today">今天</el-button><el-button @click="shift(1)">›</el-button></el-button-group>
        <el-date-picker v-model="cursor" type="date" value-format="YYYY-MM-DD" :clearable="false" />
        <el-popover placement="bottom-end" width="320" trigger="click">
          <template #reference><el-button>筛选</el-button></template>
          <b>来源</b><el-checkbox-group v-model="sources" class="filter-grid"><el-checkbox v-for="item in sourceOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox></el-checkbox-group>
          <b>状态</b><el-checkbox-group v-model="statuses" class="filter-grid"><el-checkbox v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox></el-checkbox-group>
        </el-popover>
      </div>
    </section>

    <el-alert v-if="events.some(item => item.conflict)" title="检测到时间冲突，冲突事项已用红点标记" type="warning" show-icon :closable="false" />
    <section v-loading="loading" class="calendar-surface">
      <div v-if="view === 'YEAR'" class="year-grid">
        <article v-for="month in yearMonths" :key="month.key" class="mini-month">
          <h3 @click="openMonth(month.date)">{{ month.label }}</h3>
          <div class="mini-week"><span v-for="day in weekNames" :key="day">{{ day }}</span></div>
          <div class="mini-days"><button v-for="day in month.days" :key="day.key" :class="{ muted: !day.current, today: day.today, active: day.count > 0 }" @click="openDay(day.date)"><span>{{ day.number }}</span><i v-if="day.count">{{ day.count }}</i></button></div>
        </article>
      </div>
      <div v-else-if="view === 'MONTH'" class="month-view">
        <div class="month-week"><span v-for="day in weekNames" :key="day">周{{ day }}</span></div>
        <div class="month-grid">
          <article v-for="day in monthDays" :key="day.key" :class="['month-day', { muted: !day.current, today: day.today }]" @dblclick="openCreate(day.date)">
            <header><b>{{ day.number }}</b><small v-if="day.today">今天</small></header>
            <button v-for="event in day.events.slice(0, 5)" :key="event.key" :class="['event-chip', { completed: isCompleted(event) }]" :style="eventStyle(event)" @click.stop="openEvent(event)"><i v-if="event.conflict" />{{ timeLabel(event) }} {{ event.title }}<small v-if="isCompleted(event)">已完成</small></button>
            <button v-if="day.events.length > 5" class="more" @click="openDay(day.date)">还有 {{ day.events.length - 5 }} 项</button>
          </article>
        </div>
      </div>
      <CalendarWeekView v-else-if="view === 'WEEK'" :start-date="formatDate(weekStart)" :events="events" @open-day="openDay" @open-event="openEvent" @create="openCreate" />
      <div v-else class="day-view">
        <aside><strong>{{ selectedDate.getDate() }}</strong><span>{{ weekLong }}</span><small>{{ dayEvents.length }} 项安排</small></aside>
        <div class="timeline">
          <div v-if="!dayEvents.length" class="empty-day">双击空白处或点击“新增”安排今天</div>
          <button v-for="event in dayEvents" :key="event.key" :class="['timeline-event', { completed: isCompleted(event) }]" :style="eventStyle(event)" @click="openEvent(event)"><time>{{ timeRange(event) }}</time><b>{{ event.title }}</b><span>{{ sourceName(event.sourceType) }} · {{ statusName(event.status) }}</span><em v-if="event.conflict">时间冲突</em></button>
        </div>
      </div>
    </section>

    <el-dialog v-model="formVisible" :title="editing ? '日程详情' : '快速新增日程'" width="min(560px,94vw)" :before-close="beforeClose">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <div class="form-grid"><el-form-item label="开始时间"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item><el-form-item label="结束时间"><el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="重复"><el-select v-model="form.recurrenceRule" clearable><el-option label="每天" value="DAILY" /><el-option label="每周" value="WEEKLY" /><el-option label="每月" value="MONTHLY" /></el-select></el-form-item><el-form-item label="颜色"><el-color-picker v-model="form.color" /></el-form-item></div>
        <el-checkbox v-model="form.allDay">全天事项</el-checkbox><el-checkbox v-model="form.workdayOnly">仅工作日</el-checkbox>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button v-if="editing?.route && editing.route !== '/calendar'" @click="goSource">返回原业务</el-button><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { createCalendarEvent, listCalendarEvents, moveCalendarEvent, type CalendarEvent } from '@/api/calendar';
import CalendarWeekView from './CalendarWeekView.vue';

type View = 'YEAR' | 'MONTH' | 'WEEK' | 'DAY';
const router = useRouter();
const view = ref<View>('MONTH');
const cursor = ref(formatDate(new Date()));
const events = ref<CalendarEvent[]>([]);
const loading = ref(false);
const saving = ref(false);
const formVisible = ref(false);
const dirty = ref(false);
const editing = ref<CalendarEvent>();
const viewOptions = [{ label: '年', value: 'YEAR' }, { label: '月', value: 'MONTH' }, { label: '周', value: 'WEEK' }, { label: '日', value: 'DAY' }];
const weekNames = ['日', '一', '二', '三', '四', '五', '六'];
const sourceOptions = [['LIFE', '生活'], ['WORK', '工作'], ['REMINDER', '提醒'], ['LEARNING_PLAN', '学习计划'], ['LEARNING_REVIEW', '学习复习'], ['LEARNING_RECORD', '学习记录'], ['TRADING_PLAN', '交易计划'], ['CUSTOM', '自建']].map(([value, label]) => ({ value, label }));
const statusOptions = [['NOT_STARTED', '未开始'], ['PENDING', '待处理'], ['IN_PROGRESS', '进行中'], ['DONE', '已完成'], ['COMPLETED', '已完成'], ['SENT', '已发送'], ['DRAFT', '草稿']].map(([value, label]) => ({ value, label }));
const sources = ref(sourceOptions.map((item) => item.value));
const statuses = ref<string[]>([]);
const form = reactive({ title: '', description: '', startAt: '', endAt: '', allDay: false, color: '#5b7cfa', recurrenceRule: '', workdayOnly: false });

const selectedDate = computed(() => new Date(`${cursor.value}T00:00:00`));
const weekStart = computed(() => { const date = new Date(selectedDate.value); date.setDate(date.getDate() - date.getDay()); return date; });
const weekEnd = computed(() => { const date = new Date(weekStart.value); date.setDate(date.getDate() + 6); return date; });
const heading = computed(() => view.value === 'YEAR' ? `${selectedDate.value.getFullYear()}年` : view.value === 'MONTH' ? `${selectedDate.value.getFullYear()}年 ${selectedDate.value.getMonth() + 1}月` : view.value === 'WEEK' ? weekHeading(weekStart.value, weekEnd.value) : `${selectedDate.value.getMonth() + 1}月${selectedDate.value.getDate()}日`);
const weekLong = computed(() => `周${weekNames[selectedDate.value.getDay()]}`);
const dayEvents = computed(() => eventsFor(cursor.value));
const range = computed(() => {
  const date = selectedDate.value;
  if (view.value === 'YEAR') return [new Date(date.getFullYear(), 0, 1), new Date(date.getFullYear() + 1, 0, 1)];
  if (view.value === 'MONTH') return [new Date(date.getFullYear(), date.getMonth() - 1, 20), new Date(date.getFullYear(), date.getMonth() + 2, 10)];
  if (view.value === 'WEEK') { const end = new Date(weekStart.value); end.setDate(end.getDate() + 7); return [weekStart.value, end]; }
  return [new Date(date.getFullYear(), date.getMonth(), date.getDate()), new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1)];
});
const monthDays = computed(() => calendarDays(selectedDate.value.getFullYear(), selectedDate.value.getMonth()).map((day) => ({ ...day, events: eventsFor(day.date) })));
const yearMonths = computed(() => Array.from({ length: 12 }, (_, month) => ({ key: month, label: `${month + 1}月`, date: formatDate(new Date(selectedDate.value.getFullYear(), month, 1)), days: calendarDays(selectedDate.value.getFullYear(), month).map((day) => ({ ...day, count: eventsFor(day.date).length })) })));

async function load() {
  loading.value = true;
  try {
    const [start, end] = range.value;
    events.value = (await listCalendarEvents(toLocal(start), toLocal(end), sources.value, statuses.value)).data;
  } finally { loading.value = false; }
}
function calendarDays(year: number, month: number) {
  const first = new Date(year, month, 1), start = new Date(year, month, 1 - first.getDay());
  return Array.from({ length: 42 }, (_, index) => { const date = new Date(start); date.setDate(start.getDate() + index); return { key: formatDate(date), date: formatDate(date), number: date.getDate(), current: date.getMonth() === month, today: formatDate(date) === formatDate(new Date()) }; });
}
function eventsFor(date: string) { return events.value.filter((event) => event.startAt.slice(0, 10) === date).sort((first, second) => first.startAt.localeCompare(second.startAt)); }
function shift(step: number) {
  const date = new Date(selectedDate.value);
  if (view.value === 'YEAR') date.setFullYear(date.getFullYear() + step);
  else if (view.value === 'MONTH') date.setMonth(date.getMonth() + step);
  else if (view.value === 'WEEK') date.setDate(date.getDate() + step * 7);
  else date.setDate(date.getDate() + step);
  cursor.value = formatDate(date);
}
function today() { cursor.value = formatDate(new Date()); }
function openMonth(date: string) { cursor.value = date; view.value = 'MONTH'; }
function openDay(date: string) { cursor.value = date; view.value = 'DAY'; }
function openCreate(date = cursor.value, hour = 9, minute = 0) {
  editing.value = undefined;
  const endMinutes = hour * 60 + minute + 60;
  Object.assign(form, { title: '', description: '', startAt: `${date}T${two(hour)}:${two(minute)}:00`, endAt: `${date}T${two(Math.floor(endMinutes / 60))}:${two(endMinutes % 60)}:00`, allDay: false, color: '#5b7cfa', recurrenceRule: '', workdayOnly: false });
  dirty.value = false;
  formVisible.value = true;
}
function openEvent(event: CalendarEvent) {
  editing.value = event;
  Object.assign(form, { title: event.title, description: '', startAt: event.startAt, endAt: event.endAt || '', allDay: event.allDay, color: event.color, recurrenceRule: event.recurrenceRule || '', workdayOnly: event.workdayOnly });
  dirty.value = false;
  formVisible.value = true;
}
async function save() {
  if (!form.title.trim() || !form.startAt) { ElMessage.warning('请填写标题和开始时间'); return; }
  if (form.endAt && form.endAt <= form.startAt) { ElMessage.warning('结束时间必须晚于开始时间'); return; }
  saving.value = true;
  try {
    if (editing.value) await moveCalendarEvent(editing.value.sourceType, editing.value.sourceId, { startAt: form.startAt, endAt: form.endAt || undefined, allDay: form.allDay, color: form.color });
    else await createCalendarEvent({ ...form, endAt: form.endAt || undefined, recurrenceRule: form.recurrenceRule || undefined });
    ElMessage.success(editing.value ? '日程时间已调整' : '日程已创建');
    dirty.value = false;
    formVisible.value = false;
    await load();
  } finally { saving.value = false; }
}
function beforeClose(done: () => void) { if (!dirty.value) return done(); ElMessageBox.confirm('表单尚未保存，确定关闭吗？', '防误关闭').then(() => done()).catch(() => {}); }
function goSource() { if (editing.value) void router.push(editing.value.route); }
function eventStyle(event: CalendarEvent) { return { '--event-color': event.color, background: `${event.color}22`, borderColor: event.color, color: '#334155' }; }
function timeLabel(event: CalendarEvent) { return event.allDay ? '全天' : event.startAt.slice(11, 16); }
function timeRange(event: CalendarEvent) { return event.allDay ? '全天' : `${event.startAt.slice(11, 16)}${event.endAt ? ` - ${event.endAt.slice(11, 16)}` : ''}`; }
function sourceName(value: string) { return sourceOptions.find((item) => item.value === value)?.label || value; }
function statusName(value: string) { return statusOptions.find((item) => item.value === value)?.label || value; }
function isCompleted(event: CalendarEvent) { return ['COMPLETED', 'DONE', 'SENT'].includes(event.status); }
function formatDate(date: Date) { return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`; }
function toLocal(date: Date) { return `${formatDate(date)}T${two(date.getHours())}:${two(date.getMinutes())}:00`; }
function two(value: number) { return String(value).padStart(2, '0'); }
function weekHeading(start: Date, end: Date) {
  if (start.getFullYear() !== end.getFullYear()) return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${end.getFullYear()}年${end.getMonth() + 1}月${end.getDate()}日`;
  if (start.getMonth() !== end.getMonth()) return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${end.getMonth() + 1}月${end.getDate()}日`;
  return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${end.getDate()}日`;
}
watch([cursor, view, sources, statuses], () => void load(), { deep: true });
watch(form, () => { if (formVisible.value) dirty.value = true; }, { deep: true });
onMounted(load);
</script>

<style scoped>
.event-chip.completed,.timeline-event.completed{opacity:.56;filter:saturate(.32)}.event-chip.completed,.timeline-event.completed b{text-decoration:line-through}.event-chip small{float:right;margin-left:5px;font-size:9px;text-decoration:none}
.calendar-page{display:flex;flex-direction:column;gap:14px}.calendar-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px}.calendar-toolbar h2{margin:0;font-size:24px}.calendar-toolbar p{margin:5px 0 0;color:#8492a6;font-size:12px}.toolbar-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.calendar-surface{min-height:620px;background:#fff;border:1px solid #e8edf3;border-radius:18px;overflow:hidden}.filter-grid{display:grid;grid-template-columns:1fr 1fr;margin:10px 0 16px}.year-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:28px;padding:28px}.mini-month h3{margin:0 0 12px;cursor:pointer}.mini-week,.mini-days{display:grid;grid-template-columns:repeat(7,1fr);gap:3px}.mini-week span{color:#9aa5b1;font-size:11px;text-align:center}.mini-days button{position:relative;aspect-ratio:1;border:0;background:#f7f9fc;color:#52606d;border-radius:4px}.mini-days button.active{background:#e7edff}.mini-days button.today{color:#fff;background:#5275f5}.mini-days button.muted{opacity:.35}.mini-days i{position:absolute;right:2px;bottom:1px;color:#5275f5;font-size:8px;font-style:normal}.month-week,.month-grid{display:grid;grid-template-columns:repeat(7,1fr)}.month-week span{padding:13px;text-align:center;color:#7b8794;border-bottom:1px solid #edf0f4}.month-day{min-height:128px;padding:8px;border-right:1px solid #edf0f4;border-bottom:1px solid #edf0f4}.month-day.muted{background:#fafbfc;color:#aab2bd}.month-day.today header b{display:inline-grid;width:26px;height:26px;color:#fff;background:#5275f5;border-radius:50%;place-items:center}.month-day header{display:flex;align-items:center;justify-content:space-between;margin-bottom:5px}.month-day header small{color:#5275f5}.event-chip{display:block;width:100%;margin:3px 0;padding:4px 6px;overflow:hidden;text-align:left;white-space:nowrap;text-overflow:ellipsis;border:1px solid;border-left:4px solid var(--event-color);border-radius:5px}.event-chip i{display:inline-block;width:6px;height:6px;margin-right:4px;background:#e33;border-radius:50%}.more{color:#5275f5;background:none;border:0}.day-view{display:grid;grid-template-columns:150px 1fr;max-width:1050px;margin:auto;padding:32px}.day-view aside{display:flex;align-items:center;flex-direction:column;gap:4px;padding-top:10px}.day-view aside strong{color:#5275f5;font-size:42px}.day-view aside small{color:#8b97a5}.timeline{position:relative;display:flex;flex-direction:column;gap:16px;padding-left:34px;border-left:1px solid #e4e9f0}.timeline-event{position:relative;display:grid;grid-template-columns:130px 1fr auto;gap:10px;min-height:84px;padding:18px;text-align:left;border:0;border-left:5px solid var(--event-color);border-radius:8px}.timeline-event:before{position:absolute;top:28px;left:-43px;width:14px;height:14px;background:#fff;border:2px solid var(--event-color);border-radius:50%;content:''}.timeline-event span{color:#718096}.timeline-event em{color:#d43;font-style:normal}.empty-day{padding:80px;color:#98a2b3;text-align:center}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
@media(max-width:900px){.calendar-toolbar{align-items:flex-start;flex-direction:column}.year-grid{grid-template-columns:repeat(2,1fr);padding:16px}.month-day{min-height:100px}.event-chip{font-size:11px}.day-view{grid-template-columns:90px 1fr;padding:18px}.timeline-event{grid-template-columns:1fr}.timeline-event time{color:#5275f5}}
@media(max-width:600px){.toolbar-actions{display:grid;width:100%;grid-template-columns:1fr 1fr}.toolbar-actions>*{width:100%!important}.year-grid{grid-template-columns:1fr}.month-week{display:none}.month-grid{display:flex;flex-direction:column}.month-day{display:none}.month-day.current:has(.event-chip),.month-day.today{display:block;min-height:auto;border-right:0}.day-view{display:block;padding:12px}.day-view aside{align-items:baseline;flex-direction:row;margin-bottom:20px}.timeline{margin-left:7px}.form-grid{grid-template-columns:1fr}}
</style>
