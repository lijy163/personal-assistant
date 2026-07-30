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
          <button v-for="event in day.allDayEvents.slice(0, 2)" :key="event.key" class="all-day-event" :style="baseStyle(event)" @click.stop="$emit('open-event', event)"><i v-if="event.conflict" />{{ event.title }}</button>
          <small v-if="day.allDayEvents.length > 2">+{{ day.allDayEvents.length - 2 }} 项</small>
        </div>
      </div>
      <div class="time-area" :style="{ height: canvasHeight + 'px' }">
        <div class="hour-axis"><span v-for="hour in hours" :key="hour" :style="{ top: hourTop(hour) + 'px' }">{{ two(hour) }}:00</span></div>
        <div class="columns">
          <div v-for="day in days" :key="day.date" :class="['day-column', { today: day.today }]" @dblclick="createAt(day.date, $event)">
            <span v-for="hour in hours" :key="hour" class="hour-line" :style="{ top: hourTop(hour) + 'px' }" />
            <div v-if="day.today && currentTop !== null" class="current-line" :style="{ top: currentTop + 'px' }"><i /></div>
            <button v-for="event in day.timedEvents" :key="event.key" class="week-event" :style="eventStyle(event)" @click.stop="$emit('open-event', event)">
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
const hourHeight = ref(36);
const canvasHeight = computed(() => (END_HOUR - START_HOUR) * hourHeight.value);
const now = ref(new Date());
const timer = window.setInterval(() => { now.value = new Date(); }, 60000);

function fitHeight() {
  hourHeight.value = Math.max(25, Math.min(48, (window.innerHeight - 300) / (END_HOUR - START_HOUR)));
}
onMounted(() => { fitHeight(); window.addEventListener('resize', fitHeight); });
onBeforeUnmount(() => { window.clearInterval(timer); window.removeEventListener('resize', fitHeight); });

const days = computed(() => Array.from({ length: 7 }, (_, index) => {
  const date = new Date(`${props.startDate}T00:00:00`);
  date.setDate(date.getDate() + index);
  const dateText = formatDate(date);
  const events = props.events.filter((event) => event.startAt.slice(0, 10) === dateText);
  return { date: dateText, number: date.getDate(), weekName: weekNames[index], today: dateText === formatDate(now.value), allDayEvents: events.filter((event) => event.allDay), timedEvents: events.filter((event) => !event.allDay) };
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
  const visibleStart = Math.max(start, START_HOUR * 60), visibleEnd = Math.min(Math.max(end, visibleStart + 30), END_HOUR * 60);
  return { ...baseStyle(event), top: `${(visibleStart - START_HOUR * 60) / 60 * hourHeight.value + 2}px`, height: `${Math.max(24, (visibleEnd - visibleStart) / 60 * hourHeight.value - 4)}px` };
}
function hourTop(hour: number) { return (hour - START_HOUR) * hourHeight.value; }
function minutes(value: string) { return Number(value.slice(11, 13)) * 60 + Number(value.slice(14, 16)); }
function rangeText(event: CalendarEvent) { return `${event.startAt.slice(11, 16)}${event.endAt ? ` - ${event.endAt.slice(11, 16)}` : ''}`; }
function formatDate(date: Date) { return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`; }
function two(value: number) { return String(value).padStart(2, '0'); }
</script>

<style scoped>
.week-view{width:100%;height:100%;overflow-x:auto;overflow-y:hidden}.week-canvas{min-width:980px;height:100%}.week-head,.all-day-row{display:grid;grid-template-columns:68px repeat(7,minmax(125px,1fr))}.week-head{background:#fff;border-bottom:1px solid #e8edf3}.week-corner,.axis-label{display:flex;align-items:center;justify-content:center;color:#94a3b8;font-size:11px;border-right:1px solid #e8edf3}.day-title{display:flex;align-items:center;justify-content:center;gap:7px;height:44px;color:#64748b;background:#fff;border:0;border-right:1px solid #edf0f4}.day-title b{display:grid;width:27px;height:27px;color:#172033;font-size:15px;place-items:center;border-radius:50%}.day-title.today{color:#5275f5}.day-title.today b{color:#fff;background:#5275f5}.all-day-row{min-height:34px;border-bottom:1px solid #dfe5ec}.all-day-cell{min-height:34px;padding:2px 4px;border-right:1px solid #edf0f4}.all-day-cell>small{display:block;color:#64748b;font-size:9px;text-align:center}.all-day-event{display:block;width:100%;height:16px;padding:0 4px;overflow:hidden;text-align:left;white-space:nowrap;text-overflow:ellipsis;border:1px solid;border-left:3px solid var(--event-color);border-radius:4px;font-size:9px}.all-day-event i,.week-event strong i{display:inline-block;width:5px;height:5px;margin-right:3px;background:#e33;border-radius:50%}.time-area{position:relative;display:grid;grid-template-columns:68px 1fr}.hour-axis{position:relative;border-right:1px solid #dfe5ec}.hour-axis span{position:absolute;right:8px;color:#94a3b8;font-size:10px;transform:translateY(-50%)}.columns{display:grid;grid-template-columns:repeat(7,minmax(125px,1fr))}.day-column{position:relative;border-right:1px solid #edf0f4;background:#fff}.day-column.today{background:linear-gradient(180deg,rgba(82,117,245,.045),rgba(82,117,245,.015))}.hour-line{position:absolute;right:0;left:0;height:1px;background:#edf0f4;pointer-events:none}.week-event{position:absolute;right:3px;left:3px;z-index:2;display:flex;align-items:flex-start;flex-direction:column;padding:3px 5px;overflow:hidden;text-align:left;border:1px solid;border-left:3px solid var(--event-color);border-radius:5px;box-shadow:0 2px 5px rgba(15,23,42,.05)}.week-event strong{max-width:100%;overflow:hidden;font-size:10px;line-height:1.2;white-space:nowrap;text-overflow:ellipsis}.week-event small{margin-top:1px;color:#64748b;font-size:8px}.week-event em{position:absolute;right:3px;bottom:1px;color:#dc2626;font-size:7px;font-style:normal}.current-line{position:absolute;right:0;left:0;z-index:4;height:2px;background:#ef4444;pointer-events:none}.current-line i{position:absolute;top:-3px;left:-4px;width:8px;height:8px;background:#ef4444;border-radius:50%}@media(max-width:900px){.week-canvas{min-width:900px}}@media(max-width:600px){.week-view{overflow:auto}.week-canvas{min-width:840px}.week-head,.all-day-row{grid-template-columns:54px repeat(7,minmax(112px,1fr))}.time-area{grid-template-columns:54px 1fr}}
</style>
