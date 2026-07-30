<template>
  <div>
    <div class="mobile-quick">
      <div><b>快速记录</b><span>随手记文字、拍照、文件或录音</span></div>
      <el-button type="primary" size="large" @click="$router.push('/inbox?collect=home')">开始记录</el-button>
    </div>
    <div class="dashboard-cards">
      <el-card v-for="item in cards" :key="item.label" shadow="hover" @click="item.route && $router.push(item.route)">
        <small>{{ item.label }}</small><b :class="item.type">{{ item.value }}</b><span>{{ item.help }}</span>
      </el-card>
    </div>
    <el-card class="gold-card">
      <template #header>
        <div class="gold-header">
          <div><b>实时金价</b><span v-if="goldQuotes">行情时间 {{ formatTime(goldQuotes.quoteTime) }}</span></div>
          <div><el-button link type="primary" @click="$router.push('/gold')">查看全部</el-button><el-button :loading="goldLoading" @click="loadGold">刷新</el-button></div>
        </div>
      </template>
      <el-alert v-if="goldError" title="实时金价暂时不可用，请稍后重试" type="warning" :closable="false" show-icon />
      <div v-else class="gold-quotes" v-loading="goldLoading">
        <div v-for="quote in displayedGoldQuotes" :key="quote.code" class="gold-quote" :class="{ jewelry: isJewelry(quote) }">
          <small>{{ quoteLabel(quote) }}</small>
          <strong>{{ goldPrice(quote.price, quote.converted) }}</strong><span>{{ quote.unit }}</span>
          <p>{{ quote.displayName }}</p>
        </div>
        <el-empty v-if="!goldLoading && !displayedGoldQuotes.length" description="暂无金价行情" :image-size="48" />
      </div>
      <div v-if="goldQuotes" class="gold-source">USD/CNY {{ goldQuotes.usdCny.toFixed(4) }} · 来源 {{ goldQuotes.source }}</div>
    </el-card>
    <el-row :gutter="16">
      <el-col :xs="24" :md="12"><el-card><template #header>本月财务</template><div class="finance"><div><small>收入</small><b class="income">¥{{ money(data?.monthIncome) }}</b></div><div><small>支出</small><b class="expense">¥{{ money(data?.monthExpense) }}</b></div><div><small>结余</small><b>¥{{ money((data?.monthIncome || 0) - (data?.monthExpense || 0)) }}</b></div></div></el-card></el-col>
      <el-col :xs="24" :md="12"><el-card><template #header>今日建议</template><el-alert v-if="data?.overdueTaskCount" :title="`有 ${data.overdueTaskCount} 项任务已逾期，建议先完成或重新安排。`" type="warning" :closable="false" /><el-alert v-else-if="data?.pendingInboxCount" :title="`收件箱还有 ${data.pendingInboxCount} 条待确认记录。`" type="info" :closable="false" /><el-empty v-else description="当前没有紧急待处理项" :image-size="60" /></el-card></el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getDailyDashboard, type DailyDashboard } from '@/api/productivity';
import { getGoldPublicQuotes, type GoldPublicQuote, type GoldPublicQuoteResponse } from '@/api/gold';

const data = ref<DailyDashboard>();
const goldQuotes = ref<GoldPublicQuoteResponse>();
const goldLoading = ref(false);
const goldError = ref(false);
const dashboardJewelryBrands = new Set(['周大福', '老庙']);
const cards = computed(() => [
  { label: '今日任务', value: data.value?.todayTaskCount || 0, help: '计划在今天完成', route: '/life', type: '' },
  { label: '逾期任务', value: data.value?.overdueTaskCount || 0, help: '需要重新安排', route: '/work', type: 'warning' },
  { label: '待触发提醒', value: data.value?.pendingReminderCount || 0, help: '提醒中心', route: '/reminders', type: '' },
  { label: '待确认收件箱', value: data.value?.pendingInboxCount || 0, help: '建议分类后确认', route: '/inbox', type: 'warning' },
  { label: '待确认账单', value: data.value?.unconfirmedTransactionCount || 0, help: '补充消费分类', route: '/finance', type: 'warning' },
  { label: '近 7 日开发记录', value: data.value?.recentDevLogCount || 0, help: '开发沉淀', route: '/devlogs', type: '' },
  { label: '进行中学习计划', value: data.value?.activeLearningCount || 0, help: '保持成长节奏', route: '/learning/plans', type: '' },
  { label: '资金流显著变化', value: data.value?.fundFlowAlertCount || 0, help: '仅作行情指标参考', route: '/stocks', type: 'warning' },
]);
const displayedGoldQuotes = computed(() => {
  const quotes = goldQuotes.value?.quotes || [];
  const dashboardJewelryQuotes = quotes.filter(quote =>
    isJewelry(quote) && dashboardJewelryBrands.has(quote.code.slice('JEWELRY_'.length)),
  );
  return [...quotes.filter(quote => !isJewelry(quote)), ...dashboardJewelryQuotes];
});
const money = (value?: number) => Number(value || 0).toFixed(2);
const goldPrice = (value: number, converted: boolean) => value.toFixed(converted ? 4 : 2);
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN');
const isJewelry = (quote: GoldPublicQuote) => quote.code.startsWith('JEWELRY_');
const quoteLabel = (quote: GoldPublicQuote) => isJewelry(quote) ? '品牌首饰零售价' : quote.converted ? '人民币折算参考' : '国际市场现货';
async function loadGold() { goldLoading.value = true; try { goldQuotes.value = (await getGoldPublicQuotes()).data; goldError.value = false; } catch { goldError.value = true; } finally { goldLoading.value = false; } }
onMounted(() => { void getDailyDashboard().then(response => { data.value = response.data; }); void loadGold(); });
</script>

<style scoped>
.mobile-quick{display:none}.dashboard-cards{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:16px}.dashboard-cards .el-card{cursor:pointer}.dashboard-cards small,.dashboard-cards span{display:block;color:#64748b}.dashboard-cards b{display:block;font-size:28px;margin:10px 0}.warning{color:#ea580c}.gold-card{margin-bottom:16px}.gold-header{display:flex;align-items:center;justify-content:space-between;gap:12px}.gold-header>div:first-child{display:flex;align-items:baseline;gap:12px}.gold-header span,.gold-source{font-size:12px;color:#94a3b8}.gold-quotes{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.gold-quote{padding:16px 18px;border:1px solid #f1d9a6;border-radius:12px;background:linear-gradient(135deg,#fffaf0,#fff)}.gold-quote.jewelry{background:linear-gradient(135deg,#fff7ed,#fffbeb)}.gold-quote small{display:block;color:#9a7b4f}.gold-quote strong{display:inline-block;margin:8px 8px 4px 0;font-size:28px;color:#b45309}.gold-quote span{color:#475569}.gold-quote p{margin:2px 0 0;font-weight:600;color:#1e293b}.gold-source{margin-top:10px;text-align:right}.finance{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.finance small,.finance b{display:block}.finance b{font-size:20px;margin-top:8px}.income{color:#16a34a}.expense{color:#dc2626}@media(max-width:900px){.dashboard-cards{grid-template-columns:repeat(2,1fr)}}@media(max-width:520px){.mobile-quick{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px;padding:16px;color:white;background:linear-gradient(135deg,#2563eb,#14b8a6);border-radius:16px}.mobile-quick div{display:flex;flex-direction:column;gap:4px}.mobile-quick span{font-size:12px;opacity:.85}.mobile-quick .el-button{color:#1d4ed8;background:white;border:0}.dashboard-cards{grid-template-columns:repeat(2,1fr);gap:10px}.dashboard-cards :deep(.el-card__body){padding:14px}.dashboard-cards b{font-size:22px}.gold-header,.gold-header>div:first-child{align-items:flex-start}.gold-header>div:first-child{flex-direction:column;gap:4px}.gold-quotes{grid-template-columns:1fr}.gold-quote strong{font-size:24px}.finance{grid-template-columns:1fr}}
</style>
