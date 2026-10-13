<template>
  <div class="activity-page">
    <section class="composer-card">
      <el-input
        ref="composerInput"
        v-model="form.content"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 8 }"
        maxlength="5000"
        placeholder="今天做了什么？回车即可记录，Shift + 回车换行"
        @keydown.enter.exact.prevent="submit"
      />
      <div class="composer-foot">
        <button type="button" class="ghost-toggle" @click="showDetails = !showDetails">
          {{ showDetails ? '收起补充信息' : '补充信息：分类 / 标签 / 时间' }}
        </button>
        <div class="composer-actions">
          <span v-if="draftSaved" class="hint">草稿已保存</span>
          <span v-if="pendingQueue.length" class="hint">待同步 {{ pendingQueue.length }} 条</span>
          <el-button type="primary" :loading="saving" @click="submit">记录</el-button>
        </div>
      </div>
      <div v-show="showDetails" class="composer-details">
        <el-select v-model="form.category" placeholder="分类（可空）" clearable>
          <el-option v-for="item in activityCategories" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select
          v-model="form.tagList"
          class="tags-select"
          multiple
          filterable
          allow-create
          default-first-option
          :reserve-keyword="false"
          placeholder="标签（可空，回车新建）"
        >
          <el-option v-for="item in tagSummaries" :key="item.tag" :label="item.tag" :value="item.tag" />
        </el-select>
        <el-date-picker
          v-model="form.occurredAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="发生时间，默认此刻"
        />
        <el-date-picker
          v-model="form.logDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="归属日期，默认跟随发生时间"
        />
      </div>
    </section>

    <section class="stats-strip">
      <div class="stat"><small>今天</small><b>{{ todayCount }}</b><span>条</span></div>
      <div class="stat"><small>近 7 天</small><b>{{ weekCount }}</b><span>条</span></div>
      <div class="stat"><small>连续记录</small><b>{{ streak }}</b><span>天</span></div>
      <div class="stat"><small>近一年</small><b>{{ yearCount }}</b><span>条</span></div>
    </section>

    <section class="toolbar">
      <el-input
        v-model="filters.keyword"
        class="keyword-input"
        placeholder="搜索记录内容"
        clearable
        @keyup.enter="loadEntries"
        @clear="loadEntries"
      />
      <el-select v-model="filters.category" class="category-select" placeholder="全部分类" clearable @change="loadEntries">
        <el-option v-for="item in activityCategories" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button v-if="hasFilter" @click="resetFilters">清除筛选</el-button>
      <div class="toolbar-spacer" />
      <el-button v-if="selectedDate !== todayKey" @click="selectDate(todayKey)">回到今天</el-button>
    </section>

    <section v-if="tagSummaries.length" class="tag-strip">
      <button
        v-for="item in tagSummaries.slice(0, 14)"
        :key="item.tag"
        type="button"
        class="tag-chip"
        :class="{ active: filters.tag === item.tag }"
        @click="toggleTag(item.tag)"
      >
        #{{ item.tag }}<em>{{ item.tagCount }}</em>
      </button>
    </section>

    <div class="main-grid">
      <aside class="calendar-column">
        <div class="panel">
          <header class="panel-head">
            <b>月历回溯</b>
            <span>点日期查看当天记录</span>
          </header>
          <el-calendar v-model="calendarValue">
            <template #date-cell="{ data }">
              <div
                class="cal-cell"
                :class="{ selected: data.day === selectedDate, today: data.day === todayKey, marked: dayCountMap.get(data.day) }"
              >
                <span class="cal-day">{{ Number(data.day.slice(8, 10)) }}</span>
                <span v-if="dayCountMap.get(data.day)" class="cal-count">{{ dayCountMap.get(data.day) }}</span>
              </div>
            </template>
          </el-calendar>
        </div>
      </aside>

      <section class="timeline-column" v-loading="loading">
        <div v-if="hasFilter" class="filter-banner">
          <span>筛选结果 {{ entries.length }} 条</span>
          <button type="button" @click="resetFilters">回到按天浏览</button>
        </div>

        <article v-for="group in groups" :key="group.date" class="day-group">
          <header class="day-head">
            <b>{{ dayLabel(group.date) }}</b>
            <span>{{ group.items.length }} 条</span>
            <button type="button" class="ghost-toggle" @click="quickAddTo(group.date)">补记这天</button>
          </header>
          <div
            v-for="item in group.items"
            :key="item.id"
            class="entry"
            :class="{ pinned: item.pinned }"
          >
            <div class="entry-time">{{ timeLabel(item.occurredAt) }}</div>
            <div class="entry-body">
              <p class="entry-content">{{ item.content }}</p>
              <div class="entry-meta">
                <span class="cat">{{ categoryLabel(item.category) }}</span>
                <button
                  v-for="tag in splitTags(item.tags)"
                  :key="tag"
                  type="button"
                  class="tag-inline"
                  @click="toggleTag(tag)"
                >
                  #{{ tag }}
                </button>
              </div>
            </div>
            <div class="entry-actions">
              <button type="button" :title="item.pinned ? '取消置顶' : '置顶'" @click="togglePin(item)">
                {{ item.pinned ? '★' : '☆' }}
              </button>
              <button type="button" @click="openEdit(item)">编辑</button>
              <button type="button" class="danger" @click="remove(item)">删除</button>
            </div>
          </div>
        </article>

        <el-empty v-if="!loading && !groups.length" :description="hasFilter ? '没有匹配的记录' : '这一天还没有记录'" />
      </section>
    </div>

    <section class="panel heatmap-panel">
      <header class="panel-head">
        <b>近一年活动网格</b>
        <span>颜色越深表示当天记录越多</span>
      </header>
      <div class="heatmap-scroll">
        <div class="heatmap-months">
          <span v-for="(label, index) in monthLabels" :key="index" class="month-label">{{ label }}</span>
        </div>
        <div class="heatmap-weeks">
          <div v-for="(week, weekIndex) in weeks" :key="weekIndex" class="heatmap-week">
            <button
              v-for="cell in week"
              :key="cell.key"
              type="button"
              class="heatmap-cell"
              :class="[`level-${cell.level}`, { future: cell.future, selected: cell.key === selectedDate }]"
              :title="cell.title"
              @click="selectDate(cell.key)"
            />
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="editVisible" title="编辑记录" width="min(560px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="4" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" clearable placeholder="可空">
            <el-option v-for="item in activityCategories" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" placeholder="逗号分隔，可空" />
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker v-model="editForm.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="归属日期">
          <el-date-picker v-model="editForm.logDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import {
  activityCategories,
  createActivityLog,
  deleteActivityLog,
  getActivityCalendar,
  listActivityLogs,
  listActivityTags,
  listActivityTimeline,
  toggleActivityPin,
  updateActivityLog,
  type ActivityDaySummary,
  type ActivityLog,
  type ActivityLogPayload,
  type ActivityTagSummary,
} from '@/api/activityLog';

interface PendingEntry {
  content: string;
  category?: string;
  tags?: string;
  occurredAt?: string;
  logDate?: string;
}

interface HeatCell {
  key: string;
  level: number;
  title: string;
  future: boolean;
}

const DRAFT_KEY = 'personal-assistant-activity-draft-v1';
const QUEUE_KEY = 'personal-assistant-activity-offline-queue-v1';
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

const composerInput = ref();
const saving = ref(false);
const savingEdit = ref(false);
const loading = ref(false);
const showDetails = ref(false);
const draftSaved = ref(false);
const editVisible = ref(false);
const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine);
const pendingQueue = ref<PendingEntry[]>(readQueue());

const todayKey = toDateKey(new Date());
const selectedDate = ref(todayKey);
const calendarValue = ref(parseDate(todayKey));

const entries = ref<ActivityLog[]>([]);
const dayCounts = ref<ActivityDaySummary[]>([]);
const tagSummaries = ref<ActivityTagSummary[]>([]);

const form = reactive({
  content: '',
  category: '',
  tagList: [] as string[],
  occurredAt: '',
  logDate: '',
});
const filters = reactive({ keyword: '', category: '', tag: '' });
const editForm = reactive({ id: 0, content: '', category: '', tags: '', occurredAt: '', logDate: '' });

let draftTimer: number | undefined;

const dayCountMap = computed(() => {
  const map = new Map<string, number>();
  for (const item of dayCounts.value) {
    map.set(item.logDate, item.entryCount);
  }
  return map;
});

const hasFilter = computed(() => Boolean(filters.keyword.trim() || filters.category || filters.tag));

const groups = computed(() => {
  const map = new Map<string, ActivityLog[]>();
  for (const item of entries.value) {
    const list = map.get(item.logDate);
    if (list) {
      list.push(item);
    } else {
      map.set(item.logDate, [item]);
    }
  }
  return [...map.entries()]
    .sort((left, right) => (left[0] < right[0] ? 1 : -1))
    .map(([date, items]) => ({ date, items }));
});

const todayCount = computed(() => dayCountMap.value.get(todayKey) || 0);
const yearCount = computed(() => dayCounts.value.reduce((sum, item) => sum + item.entryCount, 0));
const weekCount = computed(() => {
  let sum = 0;
  for (let offset = 0; offset < 7; offset += 1) {
    sum += dayCountMap.value.get(toDateKey(addDays(new Date(), -offset))) || 0;
  }
  return sum;
});
const streak = computed(() => {
  let days = 0;
  const cursor = new Date();
  if (!dayCountMap.value.get(toDateKey(cursor))) {
    cursor.setDate(cursor.getDate() - 1);
  }
  while (dayCountMap.value.get(toDateKey(cursor))) {
    days += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return days;
});

const weeks = computed<HeatCell[][]>(() => {
  const counts = dayCountMap.value;
  const end = parseDate(todayKey);
  const start = addDays(end, -364);
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7));

  const result: HeatCell[][] = [];
  const cursor = new Date(start);
  while (cursor <= end) {
    const week: HeatCell[] = [];
    for (let index = 0; index < 7; index += 1) {
      const key = toDateKey(cursor);
      const count = counts.get(key) || 0;
      week.push({
        key,
        level: heatLevel(count),
        title: `${key} · ${count} 条`,
        future: cursor > end,
      });
      cursor.setDate(cursor.getDate() + 1);
    }
    result.push(week);
  }
  return result;
});

const monthLabels = computed(() =>
  weeks.value.map((week, index) => {
    const first = week[0];
    if (!first) {
      return '';
    }
    const month = Number(first.key.slice(5, 7));
    if (index === 0) {
      return `${month}月`;
    }
    return Number(weeks.value[index - 1][0].key.slice(5, 7)) === month ? '' : `${month}月`;
  }),
);

async function loadEntries() {
  loading.value = true;
  try {
    if (hasFilter.value) {
      entries.value = (
        await listActivityLogs({
          keyword: filters.keyword.trim() || undefined,
          category: filters.category || undefined,
          tag: filters.tag || undefined,
          limit: 300,
        })
      ).data;
    } else {
      entries.value = (await listActivityTimeline(selectedDate.value)).data;
    }
  } finally {
    loading.value = false;
  }
}

async function loadAggregates() {
  const to = toDateKey(new Date());
  const from = toDateKey(addDays(new Date(), -364));
  const [calendarResponse, tagResponse] = await Promise.all([
    getActivityCalendar(from, to),
    listActivityTags(from, to),
  ]);
  dayCounts.value = calendarResponse.data;
  tagSummaries.value = tagResponse.data;
}

function selectDate(value: string) {
  if (!value) {
    return;
  }
  selectedDate.value = value;
  filters.keyword = '';
  filters.category = '';
  filters.tag = '';
  calendarValue.value = parseDate(value);
  void loadEntries();
}

function quickAddTo(value: string) {
  showDetails.value = true;
  form.logDate = value;
  selectDate(value);
  void nextTick(() => {
    composerInput.value?.focus();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });
}

function toggleTag(tag: string) {
  filters.tag = filters.tag === tag ? '' : tag;
  void loadEntries();
}

function resetFilters() {
  filters.keyword = '';
  filters.category = '';
  filters.tag = '';
  void loadEntries();
}

async function submit() {
  const content = form.content.trim();
  if (!content) {
    ElMessage.warning('先写点什么再记录');
    return;
  }
  const payload: PendingEntry = {
    content,
    category: form.category || undefined,
    tags: form.tagList.join(',') || undefined,
    occurredAt: form.occurredAt || undefined,
    logDate: form.logDate || undefined,
  };

  if (!online.value) {
    pendingQueue.value = [...pendingQueue.value, payload];
    writeQueue(pendingQueue.value);
    resetComposer();
    ElMessage.success('当前离线，已存入待同步队列');
    return;
  }

  saving.value = true;
  try {
    await createActivityLog(payload as ActivityLogPayload);
    const target = payload.logDate || (payload.occurredAt ? payload.occurredAt.slice(0, 10) : todayKey);
    resetComposer();
    selectedDate.value = target;
    calendarValue.value = parseDate(target);
    filters.keyword = '';
    filters.category = '';
    filters.tag = '';
    ElMessage.success('已记录');
    await Promise.all([loadEntries(), loadAggregates()]);
  } finally {
    saving.value = false;
  }
}

async function syncQueue() {
  if (!online.value || !pendingQueue.value.length) {
    return;
  }
  const remaining = [...pendingQueue.value];
  while (remaining.length) {
    try {
      await createActivityLog(remaining[0] as ActivityLogPayload);
      remaining.shift();
      pendingQueue.value = [...remaining];
      writeQueue(pendingQueue.value);
    } catch {
      break;
    }
  }
  if (!remaining.length) {
    ElMessage.success('待同步记录已全部上传');
    await Promise.all([loadEntries(), loadAggregates()]);
  }
}

async function togglePin(item: ActivityLog) {
  await toggleActivityPin(item.id);
  await loadEntries();
}

function openEdit(item: ActivityLog) {
  editForm.id = item.id;
  editForm.content = item.content;
  editForm.category = item.category;
  editForm.tags = item.tags || '';
  editForm.occurredAt = (item.occurredAt || '').slice(0, 19);
  editForm.logDate = item.logDate;
  editVisible.value = true;
}

async function saveEdit() {
  if (!editForm.content.trim()) {
    ElMessage.warning('内容不能为空');
    return;
  }
  savingEdit.value = true;
  try {
    await updateActivityLog(editForm.id, {
      content: editForm.content.trim(),
      category: editForm.category || undefined,
      tags: editForm.tags || undefined,
      occurredAt: editForm.occurredAt || undefined,
      logDate: editForm.logDate || undefined,
    });
    editVisible.value = false;
    ElMessage.success('已保存');
    await Promise.all([loadEntries(), loadAggregates()]);
  } finally {
    savingEdit.value = false;
  }
}

async function remove(item: ActivityLog) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这条记录？', '删除记录', { type: 'warning' });
  } catch {
    return;
  }
  await deleteActivityLog(item.id);
  ElMessage.success('已删除');
  await Promise.all([loadEntries(), loadAggregates()]);
}

function resetComposer() {
  form.content = '';
  form.category = '';
  form.tagList = [];
  form.occurredAt = '';
  form.logDate = '';
  localStorage.removeItem(DRAFT_KEY);
  draftSaved.value = false;
}

function saveDraft() {
  clearTimeout(draftTimer);
  draftTimer = window.setTimeout(() => {
    const filled = Boolean(form.content || form.category || form.tagList.length || form.occurredAt || form.logDate);
    if (filled) {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(form));
    } else {
      localStorage.removeItem(DRAFT_KEY);
    }
    draftSaved.value = filled;
  }, 300);
}

function restoreDraft() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY);
    if (!raw) {
      return;
    }
    const draft = JSON.parse(raw) as Partial<typeof form>;
    form.content = draft.content || '';
    form.category = draft.category || '';
    form.tagList = Array.isArray(draft.tagList) ? draft.tagList : [];
    form.occurredAt = draft.occurredAt || '';
    form.logDate = draft.logDate || '';
    draftSaved.value = Boolean(form.content || form.category || form.tagList.length);
  } catch {
    localStorage.removeItem(DRAFT_KEY);
  }
}

function readQueue(): PendingEntry[] {
  try {
    const raw = localStorage.getItem(QUEUE_KEY);
    return raw ? (JSON.parse(raw) as PendingEntry[]) : [];
  } catch {
    return [];
  }
}

function writeQueue(list: PendingEntry[]) {
  localStorage.setItem(QUEUE_KEY, JSON.stringify(list));
}

function onOnline() {
  online.value = true;
  void syncQueue();
}

function onOffline() {
  online.value = false;
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, (month || 1) - 1, day || 1);
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function heatLevel(count: number) {
  if (count <= 0) return 0;
  if (count <= 2) return 1;
  if (count <= 5) return 2;
  if (count <= 9) return 3;
  return 4;
}

function dayLabel(value: string) {
  if (value === todayKey) {
    return '今天';
  }
  if (value === toDateKey(addDays(new Date(), -1))) {
    return '昨天';
  }
  const date = parseDate(value);
  return `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAYS[date.getDay()]}`;
}

function categoryLabel(value: string) {
  return activityCategories.find((item) => item.value === value)?.label || '其他';
}

function splitTags(value?: string) {
  return value ? value.split(',').filter(Boolean) : [];
}

function timeLabel(value: string) {
  return (value || '').slice(11, 16);
}

watch(form, saveDraft, { deep: true });
watch(calendarValue, (value) => {
  if (!value) {
    return;
  }
  const key = toDateKey(value);
  if (key === selectedDate.value) {
    return;
  }
  selectDate(key);
});

onMounted(async () => {
  restoreDraft();
  window.addEventListener('online', onOnline);
  window.addEventListener('offline', onOffline);
  await Promise.all([loadEntries(), loadAggregates()]);
  await syncQueue();
});

onBeforeUnmount(() => {
  clearTimeout(draftTimer);
  window.removeEventListener('online', onOnline);
  window.removeEventListener('offline', onOffline);
});
</script>

<style scoped>
.activity-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.composer-card,
.panel,
.stats-strip,
.toolbar,
.tag-strip {
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 16px;
}

.composer-card {
  padding: 16px;
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ghost-toggle {
  padding: 0;
  color: #2563eb;
  font: inherit;
  font-size: 13px;
  background: none;
  border: 0;
  cursor: pointer;
}

.ghost-toggle:hover {
  text-decoration: underline;
}

.hint {
  color: #94a3b8;
  font-size: 12px;
}

.composer-details {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) 200px 180px;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
}

.stats-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  overflow: hidden;
  background: #e6ebf2;
}

.stat {
  padding: 14px 16px;
  background: #fff;
}

.stat small {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.stat b {
  margin-right: 4px;
  font-size: 24px;
  font-weight: 500;
  color: #1e293b;
}

.stat span {
  color: #94a3b8;
  font-size: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
}

.keyword-input {
  width: 240px;
}

.category-select {
  width: 150px;
}

.toolbar-spacer {
  flex: 1;
}

.tag-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  color: #475569;
  font: inherit;
  font-size: 12px;
  background: #f1f5f9;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
}

.tag-chip em {
  color: #94a3b8;
  font-style: normal;
}

.tag-chip.active {
  color: #1d4ed8;
  background: #e6f1fb;
  border-color: #85b7eb;
}

.main-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.panel {
  padding: 14px 16px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head b {
  font-size: 14px;
  font-weight: 500;
}

.panel-head span {
  color: #94a3b8;
  font-size: 12px;
}

.calendar-column :deep(.el-calendar) {
  --el-calendar-cell-width: 38px;
}

.calendar-column :deep(.el-calendar__header) {
  padding: 6px 0 10px;
}

.calendar-column :deep(.el-calendar__body) {
  padding: 0;
}

.calendar-column :deep(.el-calendar-table td) {
  border: 0;
  padding: 1px;
}

.calendar-column :deep(.el-calendar-table .el-calendar-day) {
  height: 40px;
  padding: 0;
}

.cal-cell {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-radius: 8px;
  cursor: pointer;
}

.cal-cell .cal-day {
  color: #475569;
  font-size: 13px;
}

.cal-cell.marked {
  background: #e6f1fb;
}

.cal-cell.marked .cal-day {
  color: #185fa5;
  font-weight: 500;
}

.cal-cell.today .cal-day {
  color: #2563eb;
  font-weight: 700;
}

.cal-cell.selected {
  background: #2563eb;
}

.cal-cell.selected .cal-day,
.cal-cell.selected .cal-count {
  color: #fff;
}

.cal-count {
  color: #185fa5;
  font-size: 10px;
  line-height: 1;
}

.timeline-column {
  display: flex;
  min-height: 200px;
  flex-direction: column;
  gap: 14px;
}

.filter-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  color: #1d4ed8;
  font-size: 13px;
  background: #e6f1fb;
  border-radius: 12px;
}

.filter-banner button {
  color: #1d4ed8;
  font: inherit;
  background: none;
  border: 0;
  cursor: pointer;
}

.day-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
}

.day-head b {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.day-head span {
  color: #94a3b8;
  font-size: 12px;
}

.day-head .ghost-toggle {
  margin-left: auto;
  font-size: 12px;
}

.entry {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 12px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e6ebf2;
  border-left: 3px solid #e6ebf2;
  border-radius: 12px;
}

.entry.pinned {
  border-left-color: #f0b429;
  background: #fffdf6;
}

.entry-time {
  color: #94a3b8;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  padding-top: 2px;
}

.entry-content {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.entry-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.entry-meta .cat {
  padding: 2px 8px;
  color: #0f6e56;
  font-size: 12px;
  background: #e1f5ee;
  border-radius: 6px;
}

.tag-inline {
  padding: 0;
  color: #2563eb;
  font: inherit;
  font-size: 12px;
  background: none;
  border: 0;
  cursor: pointer;
}

.tag-inline:hover {
  text-decoration: underline;
}

.entry-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.entry:hover .entry-actions,
.entry:focus-within .entry-actions {
  opacity: 1;
}

.entry-actions button {
  padding: 0;
  color: #64748b;
  font: inherit;
  font-size: 12px;
  background: none;
  border: 0;
  cursor: pointer;
}

.entry-actions button:hover {
  color: #2563eb;
}

.entry-actions button.danger:hover {
  color: #dc2626;
}

.heatmap-panel {
  overflow: hidden;
}

.heatmap-scroll {
  overflow-x: auto;
  padding-bottom: 4px;
}

.heatmap-months {
  display: flex;
  gap: 3px;
  min-width: max-content;
  margin-bottom: 6px;
}

.month-label {
  width: 12px;
  color: #94a3b8;
  font-size: 10px;
  white-space: nowrap;
}

.heatmap-weeks {
  display: flex;
  gap: 3px;
  min-width: max-content;
}

.heatmap-week {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.heatmap-cell {
  width: 12px;
  height: 12px;
  padding: 0;
  background: #eef2f7;
  border: 0;
  border-radius: 3px;
  cursor: pointer;
}

.heatmap-cell.level-1 {
  background: #c7ddf7;
}

.heatmap-cell.level-2 {
  background: #85b7eb;
}

.heatmap-cell.level-3 {
  background: #378add;
}

.heatmap-cell.level-4 {
  background: #185fa5;
}

.heatmap-cell.future {
  visibility: hidden;
}

.heatmap-cell.selected {
  outline: 1.5px solid #2563eb;
  outline-offset: 1px;
}

@media (max-width: 900px) {
  .composer-details {
    grid-template-columns: 1fr;
  }

  .main-grid {
    grid-template-columns: 1fr;
  }

  .stats-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .keyword-input,
  .category-select {
    width: 100%;
  }

  .entry {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .entry-actions {
    grid-column: 2;
    opacity: 1;
  }
}
</style>
