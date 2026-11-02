<template>
  <div class="checkin-page" v-loading="loading">
    <section class="hero-card">
      <div class="hero-copy">
        <div class="hero-date">{{ heroDateLabel }}</div>
        <h1>{{ isToday ? '今日打卡' : '补记打卡' }}</h1>
        <p class="hero-desc">
          已完成 <b>{{ overview.doneCount }}</b> / {{ overview.habitCount }} 项
          <span v-if="overview.currentStreak">· 连续 {{ overview.currentStreak }} 天</span>
        </p>
        <p class="hero-tip">打卡后会自动写入「每日活动记录」，撤销打卡会同步删除那条记录。</p>
      </div>
      <div class="hero-ring">
        <el-progress type="circle" :percentage="progress" :width="104" :stroke-width="8">
          <template #default>
            <div class="ring-text"><b>{{ progress }}%</b><small>完成度</small></div>
          </template>
        </el-progress>
      </div>
    </section>

    <section class="habit-section">
      <header class="section-head">
        <b>打卡项</b>
        <span>{{ isToday ? '点卡片完成打卡，再点一次撤销' : `正在为 ${shortDate(selectedDate)} 补记` }}</span>
        <div class="section-actions">
          <el-button v-if="!isToday" size="small" @click="selectDate(todayKey)">回到今天</el-button>
          <el-button type="primary" size="small" plain @click="openHabitDialog()">新建打卡项</el-button>
        </div>
      </header>

      <div v-if="activeHabits.length" class="habit-grid">
        <article
          v-for="habit in activeHabits"
          :key="habit.id"
          class="habit-card"
          :class="{ done: habit.checked, busy: busyId === habit.id }"
          @click="toggle(habit)"
        >
          <div class="habit-icon" :style="{ background: tint(habit.color), color: habit.color }">
            {{ habit.icon || habit.name.slice(0, 1) }}
          </div>
          <div class="habit-main">
            <b>{{ habit.name }}</b>
            <small>{{ habit.note || '每日一次' }}</small>
            <div class="habit-meta">
              <span>连续 {{ habit.streak }} 天</span>
              <span>本月 {{ habit.monthCount }} 次</span>
            </div>
          </div>
          <button type="button" class="habit-edit" title="编辑打卡项" @click.stop="openHabitDialog(habit)">⋯</button>
          <div class="habit-check" :class="{ checked: habit.checked }">
            <span v-if="habit.checked">✓</span>
          </div>
        </article>
        <button type="button" class="habit-card add-card" @click="openHabitDialog()">
          <span class="add-plus">＋</span>
          <span>新建打卡项</span>
        </button>
      </div>

      <div v-else class="empty-habits">
        <p>还没有打卡项，先挑一件想每天坚持的小事：</p>
        <div class="preset-row">
          <button v-for="preset in presets" :key="preset.name" type="button" class="preset-chip" @click="quickCreate(preset)">
            <span>{{ preset.icon }}</span>{{ preset.name }}
          </button>
        </div>
      </div>

      <div v-if="inactiveHabits.length" class="inactive-row">
        <span>已停用 {{ inactiveHabits.length }} 项</span>
        <button
          v-for="habit in inactiveHabits"
          :key="habit.id"
          type="button"
          class="inactive-chip"
          @click="openHabitDialog(habit)"
        >
          {{ habit.icon || '○' }} {{ habit.name }} · 启用
        </button>
      </div>
    </section>

    <section class="stats-strip">
      <div class="stat"><small>今日完成</small><b>{{ overview.doneCount }}</b><span>/ {{ overview.habitCount }} 项</span></div>
      <div class="stat"><small>当前连续</small><b>{{ overview.currentStreak }}</b><span>天</span></div>
      <div class="stat"><small>本月打卡</small><b>{{ overview.monthCount }}</b><span>次</span></div>
      <div class="stat"><small>累计打卡</small><b>{{ overview.totalDays }}</b><span>天</span></div>
    </section>

    <div class="main-grid">
      <aside class="panel calendar-column">
        <header class="panel-head">
          <b>月历回望</b>
          <span>点日期查看或补记当天打卡</span>
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
      </aside>

      <section class="panel records-column">
        <header class="panel-head">
          <b>{{ dayTitle }}</b>
          <span>{{ overview.records.length }} 条打卡</span>
          <router-link class="panel-link" to="/activity">去活动记录看看 →</router-link>
        </header>

        <div v-if="overview.records.length" class="record-list">
          <div v-for="record in overview.records" :key="record.id" class="record">
            <div class="record-icon" :style="{ background: tint(record.color), color: record.color || '#2563eb' }">
              {{ record.icon || '✓' }}
            </div>
            <div class="record-body">
              <b>{{ record.habitName }}</b>
              <p v-if="record.note">{{ record.note }}</p>
              <small>
                {{ timeLabel(record.createdAt) }} 打卡 ·
                <template v-if="record.activityLogId">已同步到活动记录</template>
                <template v-else>历史记录，未关联活动记录</template>
              </small>
            </div>
            <button type="button" class="record-undo" @click="cancelRecord(record)">撤销</button>
          </div>
        </div>

        <div v-else class="day-empty">
          <p>{{ isToday ? '今天还没有打卡' : `${shortDate(selectedDate)} 没有打卡` }}</p>
          <small>在上方点一张卡片即可{{ isToday ? '打卡' : '补记' }}</small>
        </div>
      </section>
    </div>

    <section class="panel heatmap-panel">
      <header class="panel-head">
        <b>近一年打卡网格</b>
        <span>颜色越深表示当天完成的打卡项越多</span>
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

    <el-dialog v-model="habitDialogVisible" :title="editingId ? '编辑打卡项' : '新建打卡项'" width="min(520px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="habitForm.name" maxlength="120" placeholder="例如：早起、运动 30 分钟" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="图标">
            <el-input v-model="habitForm.icon" maxlength="16" placeholder="🌱" />
          </el-form-item>
          <el-form-item label="同步分类">
            <el-select v-model="habitForm.category" placeholder="其他">
              <el-option v-for="item in activityCategories" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="图标候选">
          <div class="icon-row">
            <button
              v-for="icon in iconPresets"
              :key="icon"
              type="button"
              class="icon-chip"
              :class="{ active: habitForm.icon === icon }"
              @click="habitForm.icon = icon"
            >
              {{ icon }}
            </button>
          </div>
        </el-form-item>
        <el-form-item label="主题色">
          <div class="color-row">
            <button
              v-for="color in colorPresets"
              :key="color"
              type="button"
              class="color-chip"
              :class="{ active: habitForm.color === color }"
              :style="{ background: color }"
              @click="habitForm.color = color"
            />
          </div>
        </el-form-item>
        <el-form-item label="备注（可空，会显示在卡片上）">
          <el-input v-model="habitForm.note" maxlength="200" placeholder="例如：7 点前起床" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="habitForm.active" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <p class="form-tip">
          打卡会在每日活动记录里生成一条「打卡：{{ habitForm.name || '打卡项' }}」，分类取「同步分类」，标签带 #打卡。
        </p>
      </el-form>
      <template #footer>
        <el-button v-if="editingId" type="danger" plain :loading="deleting" @click="deleteHabit">删除</el-button>
        <el-button @click="habitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingHabit" @click="saveHabit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { activityCategories } from '@/api/activityLog';
import {
  cancelCheckin,
  createCheckinHabit,
  getCheckinCalendar,
  getCheckinOverview,
  removeCheckinHabit,
  submitCheckin,
  updateCheckinHabit,
  type CheckinDaySummary,
  type CheckinHabit,
  type CheckinOverview,
  type CheckinRecord,
} from '@/api/checkin';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';

interface HeatCell {
  key: string;
  level: number;
  title: string;
  future: boolean;
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const ICON_PRESETS = ['🌱', '☀️', '🏃', '📖', '💧', '🧘', '✍️', '🛏️', '💪', '🧹'];
const COLOR_PRESETS = ['#2563eb', '#0f766e', '#7c3aed', '#db2777', '#ea580c', '#0891b2', '#65a30d', '#475569'];
const PRESETS = [
  { name: '早起', icon: '☀️', category: 'REST', note: '7 点前起床' },
  { name: '运动 30 分钟', icon: '🏃', category: 'EXERCISE', note: '跑步或力量训练' },
  { name: '读书 20 页', icon: '📖', category: 'STUDY', note: '' },
  { name: '每日复盘', icon: '✍️', category: 'WORK', note: '写三行今日总结' },
  { name: '喝水 2L', icon: '💧', category: 'FOOD', note: '' },
  { name: '早睡', icon: '🛏️', category: 'REST', note: '23 点前躺下' },
];

const todayKey = toDateKey(new Date());

const loading = ref(false);
const initialized = ref(false);
const savingHabit = ref(false);
const deleting = ref(false);
const busyId = ref<number | null>(null);
const selectedDate = ref(todayKey);
const calendarValue = ref(parseDate(todayKey));
const dayCounts = ref<CheckinDaySummary[]>([]);
const overview = ref<CheckinOverview>({
  date: todayKey,
  habitCount: 0,
  doneCount: 0,
  currentStreak: 0,
  monthCount: 0,
  totalDays: 0,
  habits: [],
  records: [],
});
const habitDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const habitForm = reactive({ name: '', icon: '', color: COLOR_PRESETS[0], category: 'OTHER', note: '', active: true });

const iconPresets = ICON_PRESETS;
const colorPresets = COLOR_PRESETS;
const presets = PRESETS;

const isToday = computed(() => selectedDate.value === todayKey);
const activeHabits = computed(() => overview.value.habits.filter((habit) => habit.active));
const inactiveHabits = computed(() => overview.value.habits.filter((habit) => !habit.active));
const progress = computed(() => {
  if (!overview.value.habitCount) {
    return 0;
  }
  return Math.round((overview.value.doneCount / overview.value.habitCount) * 100);
});
const heroDateLabel = computed(() => `${selectedDate.value} ${WEEKDAYS[parseDate(selectedDate.value).getDay()]}`);
const dayTitle = computed(() => (isToday.value ? '今天的打卡明细' : `${shortDate(selectedDate.value)} 打卡明细`));

const dayCountMap = computed(() => {
  const map = new Map<string, number>();
  for (const item of dayCounts.value) {
    map.set(item.checkinDate, item.doneCount);
  }
  return map;
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
        title: `${key} · ${count} 项打卡`,
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

async function loadOverview() {
  if (!initialized.value) {
    loading.value = true;
  }
  try {
    const response = await getCheckinOverview(selectedDate.value);
    overview.value = response.data;
    initialized.value = true;
  } finally {
    loading.value = false;
  }
}

async function loadCalendar() {
  const to = todayKey;
  const from = toDateKey(addDays(new Date(), -364));
  const response = await getCheckinCalendar(from, to);
  dayCounts.value = response.data;
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadCalendar()]);
}

function selectDate(value: string) {
  if (!value || value === selectedDate.value) {
    return;
  }
  selectedDate.value = value;
  calendarValue.value = parseDate(value);
  void loadOverview();
}

async function toggle(habit: CheckinHabit) {
  if (busyId.value !== null) {
    return;
  }
  if (selectedDate.value > todayKey) {
    ElMessage.warning('未来日期还不能打卡');
    return;
  }
  busyId.value = habit.id;
  try {
    if (habit.checked) {
      await cancelCheckin(habit.id, selectedDate.value);
      ElMessage.success('已撤销打卡，活动记录同步删除');
    } else {
      await submitCheckin(habit.id, selectedDate.value);
      ElMessage.success('已打卡，同步写入每日活动记录');
    }
    await refreshAll();
  } finally {
    busyId.value = null;
  }
}

async function cancelRecord(record: CheckinRecord) {
  try {
    await ElMessageBox.confirm('撤销后这条打卡与它同步的活动记录都会删除，确认继续？', '撤销打卡', { type: 'warning' });
  } catch {
    return;
  }
  busyId.value = record.habitId;
  try {
    await cancelCheckin(record.habitId, record.checkinDate);
    ElMessage.success('已撤销');
    await refreshAll();
  } finally {
    busyId.value = null;
  }
}

function openHabitDialog(habit?: CheckinHabit) {
  editingId.value = habit ? habit.id : null;
  habitForm.name = habit ? habit.name : '';
  habitForm.icon = habit?.icon || '';
  habitForm.color = habit?.color || COLOR_PRESETS[0];
  habitForm.category = habit?.category || 'OTHER';
  habitForm.note = habit?.note || '';
  habitForm.active = habit ? habit.active : true;
  habitDialogVisible.value = true;
}

async function saveHabit() {
  const name = habitForm.name.trim();
  if (!name) {
    ElMessage.warning('先给打卡项起个名字');
    return;
  }
  savingHabit.value = true;
  try {
    const payload = {
      name,
      icon: habitForm.icon,
      color: habitForm.color,
      category: habitForm.category,
      note: habitForm.note,
      active: habitForm.active,
    };
    if (editingId.value) {
      await updateCheckinHabit(editingId.value, payload);
    } else {
      await createCheckinHabit(payload);
    }
    habitDialogVisible.value = false;
    ElMessage.success('已保存');
    await refreshAll();
  } finally {
    savingHabit.value = false;
  }
}

async function deleteHabit() {
  if (!editingId.value) {
    return;
  }
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这个打卡项？', '删除打卡项', { type: 'warning' });
  } catch {
    return;
  }
  deleting.value = true;
  try {
    const response = await removeCheckinHabit(editingId.value);
    habitDialogVisible.value = false;
    ElMessage.success(response.data.archived ? '已有打卡历史，已改为停用并保留记录' : '已删除');
    await refreshAll();
  } finally {
    deleting.value = false;
  }
}

async function quickCreate(preset: (typeof PRESETS)[number]) {
  await createCheckinHabit({
    name: preset.name,
    icon: preset.icon,
    category: preset.category,
    note: preset.note || undefined,
    active: true,
  });
  ElMessage.success(`已添加「${preset.name}」`);
  await refreshAll();
}

watch(calendarValue, (value) => {
  if (!value) {
    return;
  }
  selectDate(toDateKey(value));
});

onMounted(() => {
  void refreshAll();
});

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
  if (count <= 1) return 1;
  if (count <= 3) return 2;
  if (count <= 5) return 3;
  return 4;
}

function shortDate(value: string) {
  const date = parseDate(value);
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

function timeLabel(value: string) {
  return (value || '').slice(11, 16);
}

function tint(color?: string, alpha = 0.12) {
  const value = (color || '#2563eb').replace('#', '');
  if (value.length !== 6) {
    return `rgba(37, 99, 235, ${alpha})`;
  }
  const red = Number.parseInt(value.slice(0, 2), 16);
  const green = Number.parseInt(value.slice(2, 4), 16);
  const blue = Number.parseInt(value.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}
</script>

<style scoped>
.checkin-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-card,
.panel,
.stats-strip,
.habit-section {
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 16px;
}

.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px;
  background: linear-gradient(120deg, #f8fbff 0%, #fff 46%, #f6fbfa 100%);
}

.hero-date {
  color: #94a3b8;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.hero-copy h1 {
  margin: 4px 0 6px;
  color: #0f172a;
  font-size: 22px;
  font-weight: 600;
}

.hero-desc {
  margin: 0 0 12px;
  color: #475569;
  font-size: 13px;
}

.hero-desc b {
  color: #2563eb;
  font-size: 18px;
  font-weight: 600;
}

.hero-tip {
  margin: 10px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.hero-ring {
  flex: 0 0 auto;
}

.ring-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.ring-text b {
  color: #1e293b;
  font-size: 20px;
  font-weight: 600;
}

.ring-text small {
  color: #94a3b8;
  font-size: 11px;
}

.habit-section {
  padding: 16px;
}

.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.section-head b {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.section-head span {
  color: #94a3b8;
  font-size: 12px;
}

.section-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.habit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(248px, 1fr));
  gap: 12px;
}

.habit-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  text-align: left;
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 14px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.habit-card:hover {
  border-color: #bfd4f2;
  box-shadow: 0 6px 18px rgb(37 99 235 / 8%);
  transform: translateY(-1px);
}

.habit-card.done {
  background: #f6fbf8;
  border-color: #bfe3d2;
}

.habit-card.busy {
  opacity: 0.6;
  pointer-events: none;
}

.habit-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  font-size: 19px;
  border-radius: 12px;
}

.habit-main {
  min-width: 0;
  flex: 1;
}

.habit-main b {
  display: block;
  overflow: hidden;
  color: #1e293b;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.habit-main small {
  display: block;
  overflow: hidden;
  margin-top: 2px;
  color: #94a3b8;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.habit-meta {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
}

.habit-check {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  color: #fff;
  font-size: 13px;
  border: 1px dashed #cbd5e1;
  border-radius: 50%;
}

.habit-check.checked {
  background: #0f766e;
  border: 1px solid #0f766e;
}

.habit-edit {
  position: absolute;
  top: 8px;
  right: 10px;
  padding: 0 4px;
  color: #94a3b8;
  font: inherit;
  font-size: 14px;
  background: none;
  border: 0;
  opacity: 0;
  cursor: pointer;
}

.habit-card:hover .habit-edit {
  opacity: 1;
}

.add-card {
  justify-content: center;
  color: #64748b;
  font-size: 13px;
  background: #fafcff;
  border-style: dashed;
}

.add-plus {
  font-size: 18px;
  color: #2563eb;
}

.empty-habits {
  padding: 18px;
  color: #64748b;
  font-size: 13px;
  background: #fafcff;
  border: 1px dashed #dbe4f0;
  border-radius: 14px;
}

.empty-habits p {
  margin: 0 0 10px;
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  color: #475569;
  font: inherit;
  font-size: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  cursor: pointer;
}

.preset-chip:hover {
  color: #1d4ed8;
  border-color: #85b7eb;
}

.inactive-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  color: #94a3b8;
  font-size: 12px;
  border-top: 1px dashed #e2e8f0;
}

.inactive-chip {
  padding: 4px 10px;
  color: #64748b;
  font: inherit;
  font-size: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  cursor: pointer;
}

.inactive-chip:hover {
  color: #2563eb;
  border-color: #bfd4f2;
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
  color: #1e293b;
  font-size: 24px;
  font-weight: 500;
}

.stat span {
  color: #94a3b8;
  font-size: 12px;
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
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head b {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.panel-head span {
  color: #94a3b8;
  font-size: 12px;
}

.panel-link {
  margin-left: auto;
  color: #2563eb;
  font-size: 12px;
  text-decoration: none;
}

.panel-link:hover {
  text-decoration: underline;
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
  padding: 1px;
  border: 0;
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

.record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.record {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 12px;
}

.record-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  font-size: 17px;
  border-radius: 10px;
}

.record-body {
  min-width: 0;
  flex: 1;
}

.record-body b {
  color: #1e293b;
  font-size: 14px;
  font-weight: 500;
}

.record-body p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 13px;
}

.record-body small {
  display: block;
  margin-top: 4px;
  color: #94a3b8;
  font-size: 11px;
}

.record-undo {
  padding: 0;
  color: #94a3b8;
  font: inherit;
  font-size: 12px;
  background: none;
  border: 0;
  cursor: pointer;
}

.record-undo:hover {
  color: #dc2626;
}

.day-empty {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #94a3b8;
  font-size: 13px;
  background: #fafcff;
  border: 1px dashed #e2e8f0;
  border-radius: 12px;
}

.day-empty p {
  margin: 0;
}

.day-empty small {
  font-size: 12px;
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

.form-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px;
}

.icon-row,
.color-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.icon-chip {
  width: 32px;
  height: 32px;
  font-size: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
}

.icon-chip.active {
  border-color: #2563eb;
  background: #e6f1fb;
}

.color-chip {
  width: 26px;
  height: 26px;
  border: 2px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.color-chip.active {
  border-color: #0f172a;
  box-shadow: 0 0 0 2px #fff inset;
}

.form-tip {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .hero-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .main-grid {
    grid-template-columns: 1fr;
  }

  .stats-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .habit-grid {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
