<template>
  <div class="week-view">
    <div class="week-canvas">
      <div class="week-head">
        <div class="week-corner">全天</div>
        <button v-for="day in days" :key="day.date" :class="['day-title', { today: day.today }]" @click="$emit('open-day', day.date)">
          <span>周{{ day.weekName }}</span><b>{{ day.number }}</b>
        </button>
      </div>
      <div class="all-day-row">
        <div class="axis-label">全天</div>
        <div v-for="day in days" :key="day.date" class="all-day-cell" @dblclick="$emit('create', day.date, 9, 0)">
          <button v-for="event in day.topEvents.slice(0, 3)" :key="event.key" :class="['all-day-event', { completed: isCompleted(event) }]" :style="baseStyle(event)" @click.stop="$emit('open-event', event)"><i v-if="event.conflict" /><span>{{ event.title }}</span><small>{{ isCompleted(event) ? '已完成' : topEventTime(event) }}</small></button>
          <small v-if="day.topEvents.length > 3">+{{ day.topEvents.length - 3 }} 项</small>
        </div>
      </div>
      <div class="time-area" :style="{ height: canvasHeight + 'px' }">
        <div class="hour-axis"><span v-for="hour in hours" :key="hour" :style="{ top: hourTop(hour) + 'px' }">{{ two(hour) }}:00</span></div>
        <div class="columns">
          <div v-for="day in days" :key="day.date" :class="['day-column', { today: day.today }]" @dblclick="createAt(day.date, $event)">
            <span v-for="hour in hours" :key="hour" class="hour-line" :style="{ top: hourTop(hour) + 'px' }" />
            <div v-if="day.today && currentTop !== null" class="current-line" :style="{ top: currentTop + 'px' }"><i /></div>
            <button v-for="event in day.timedEvents" :key="event.key" :class="['week-event', { completed: isCompleted(event) }]" :style="eventStyle(event)" @click.stop="$emit('open-event', event)">
              <strong><i v-if="event.conflict" />{{ event.title }}</strong><small>{{ rangeText(event) }}</small><em v-if="event.conflict">冲突</em>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { CalendarEvent } from '@/api/calendar';

const props = defineProps<{ startDate: string; events: CalendarEvent[] }>();
const emit = defineEmits<{ 'open-day': [date: string]; 'open-event': [event: CalendarEvent]; create: [date: string, hour: number, minute: number] }>();
const START_HOUR = 6;
const END_HOUR = 23;
const weekNames = ['日', '一', '二', '三', '四', '五', '六'];
const hours = Array.from({ length: END_HOUR - START_HOUR + 1 }, (_, index) => START_HOUR + index);
const hourHeight = ref(42);
const canvasHeight = computed(() => (END_HOUR - START_HOUR) * hourHeight.value);
const now = ref(new Date());
const timer = window.setInterval(() => { now.value = new Date(); }, 60000);

function fitHeight() {
  hourHeight.value = window.innerWidth <= 600 ? 40 : 42;
}
onMounted(() => { fitHeight(); window.addEventListener('resize', fitHeight); });
onBeforeUnmount(() => { window.clearInterval(timer); window.removeEventListener('resize', fitHeight); });

const days = computed(() => Array.from({ length: 7 }, (_, index) => {
  const date = new Date(`${props.startDate}T00:00:00`);
  date.setDate(date.getDate() + index);
  const dateText = formatDate(date);
  const events = props.events.filter((event) => event.startAt.slice(0, 10) === dateText);
  return { date: dateText, number: date.getDate(), weekName: weekNames[index], today: dateText === formatDate(now.value), topEvents: events.filter(isTopEvent), timedEvents: events.filter((event) => !isTopEvent(event)) };
}));
const currentTop = computed(() => {
  const hour = now.value.getHours() + now.value.getMinutes() / 60;
  return hour < START_HOUR || hour > END_HOUR ? null : (hour - START_HOUR) * hourHeight.value;
});

function createAt(date: string, mouseEvent: MouseEvent) {
  const element = mouseEvent.currentTarget as HTMLElement;
  const offset = Math.max(0, Math.min(canvasHeight.value, mouseEvent.clientY - element.getBoundingClientRect().top));
  const minutes = Math.round((START_HOUR * 60 + offset / hourHeight.value * 60) / 30) * 30;
  emit('create', date, Math.floor(minutes / 60), minutes % 60);
}
function baseStyle(event: CalendarEvent) { return { '--event-color': event.color, background: `${event.color}22`, borderColor: event.color, color: '#334155' }; }
function eventStyle(event: CalendarEvent) {
  const start = minutes(event.startAt), end = event.endAt ? minutes(event.endAt) : start + 60;
  const visibleStart = Math.max(start, START_HOUR * 60), visibleEnd = Math.min(Math.max(end, visibleStart + 45), END_HOUR * 60);
  return { ...baseStyle(event), top: `${(visibleStart - START_HOUR * 60) / 60 * hourHeight.value + 3}px`, height: `${Math.max(38, (visibleEnd - visibleStart) / 60 * hourHeight.value - 6)}px` };
}
function isTopEvent(event: CalendarEvent) { return event.allDay || (!event.endAt && minutes(event.startAt) < START_HOUR * 60); }
function topEventTime(event: CalendarEvent) { return event.allDay ? '全天' : event.startAt.slice(11, 16); }
function hourTop(hour: number) { return (hour - START_HOUR) * hourHeight.value; }
function minutes(value: string) { return Number(value.slice(11, 13)) * 60 + Number(value.slice(14, 16)); }
function rangeText(event: CalendarEvent) { return `${event.startAt.slice(11, 16)}${event.endAt ? ` - ${event.endAt.slice(11, 16)}` : ''}`; }
function isCompleted(event: CalendarEvent) { return ['COMPLETED', 'DONE', 'SENT'].includes(event.status); }
function formatDate(date: Date) { return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`; }
function two(value: number) { return String(value).padStart(2, '0'); }
</script>

<style scoped>
.all-day-event.completed,.week-event.completed{opacity:.55;filter:saturate(.3)}.all-day-event.completed span,.week-event.completed strong{text-decoration:line-through}
.week-view{width:100%;height:100%;overflow:auto;background:#fff}.week-canvas{min-width:1040px}.week-head,.all-day-row{display:grid;grid-template-columns:72px repeat(7,minmax(138px,1fr))}.week-head{position:sticky;top:0;z-index:8;background:#fff;border-bottom:1px solid #edf1f6;box-shadow:0 4px 14px rgb(15 23 42 / 4%)}.week-corner,.axis-label{display:flex;align-items:center;justify-content:center;color:#94a3b8;font-size:11px;border-right:1px solid #edf1f6}.day-title{display:flex;align-items:center;justify-content:center;gap:9px;height:56px;color:#64748b;background:#fff;border:0;border-right:1px solid #edf1f6}.day-title:hover{background:#fafcff}.day-title span{font-size:13px}.day-title b{display:grid;width:31px;height:31px;color:#172033;font-size:16px;place-items:center;border-radius:50%}.day-title.today{color:#5275f5}.day-title.today b{color:#fff;background:linear-gradient(135deg,#6682ff,#496af4);box-shadow:0 7px 16px rgb(82 117 245 / 28%)}.all-day-row{min-height:48px;background:#fbfcfe;border-bottom:1px solid #dfe6ef}.all-day-cell{min-height:48px;padding:5px 6px;border-right:1px solid #edf1f6}.all-day-cell>small{display:block;margin-top:2px;color:#64748b;font-size:9px;text-align:center}.all-day-event{display:grid;width:100%;height:32px;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:4px;padding:4px 7px;overflow:hidden;text-align:left;border:0;border-left:4px solid var(--event-color);border-radius:7px;font-size:10px;box-shadow:inset 0 0 0 1px rgb(255 255 255 / 46%)}.all-day-event span{overflow:hidden;font-weight:650;white-space:nowrap;text-overflow:ellipsis}.all-day-event small{color:#64748b;font-size:9px}.all-day-event i,.week-event strong i{display:inline-block;width:6px;height:6px;flex:none;background:#ef4444;border-radius:50%}.time-area{position:relative;display:grid;grid-template-columns:72px 1fr}.hour-axis{position:relative;background:#fbfcfe;border-right:1px solid #dfe6ef}.hour-axis span{position:absolute;right:10px;color:#8da0ba;font-size:10px;transform:translateY(-50%)}.columns{display:grid;grid-template-columns:repeat(7,minmax(138px,1fr))}.day-column{position:relative;border-right:1px solid #edf1f6;background:#fff}.day-column.today{background:linear-gradient(180deg,rgb(82 117 245 / 5%),rgb(82 117 245 / 1.5%))}.hour-line{position:absolute;right:0;left:0;height:1px;background:#e8edf4;pointer-events:none}.week-event{position:absolute;right:6px;left:6px;z-index:2;display:flex;align-items:flex-start;flex-direction:column;padding:8px 9px;overflow:hidden;text-align:left;border:0;border-left:4px solid var(--event-color);border-radius:8px;box-shadow:0 5px 14px rgb(15 23 42 / 8%);transition:box-shadow .16s ease,transform .16s ease}.week-event:hover{z-index:5;box-shadow:0 10px 22px rgb(15 23 42 / 14%);transform:translateY(-1px)}.week-event strong{display:flex;width:100%;align-items:center;gap:4px;overflow:hidden;font-size:11px;line-height:1.3;white-space:nowrap;text-overflow:ellipsis}.week-event small{margin-top:3px;color:#64748b;font-size:9px}.week-event em{position:absolute;right:6px;bottom:4px;color:#dc2626;font-size:8px;font-style:normal}.current-line{position:absolute;right:0;left:0;z-index:4;height:2px;background:#ef4444;pointer-events:none}.current-line i{position:absolute;top:-4px;left:-5px;width:10px;height:10px;background:#ef4444;border:2px solid #fff;border-radius:50%;box-shadow:0 1px 5px rgb(239 68 68 / 35%)}@media(max-width:900px){.week-canvas{min-width:980px}}@media(max-width:600px){.week-canvas{min-width:900px}.week-head,.all-day-row{grid-template-columns:58px repeat(7,minmax(120px,1fr))}.time-area{grid-template-columns:58px 1fr}}
</style>
