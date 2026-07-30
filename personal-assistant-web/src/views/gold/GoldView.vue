<template>
  <div class="gold-page">
    <section class="gold-hero">
      <div>
        <p class="eyebrow">Gold Watch</p>
        <h2>金价关注</h2>
        <p>国际现货黄金无需配置即可实时刷新，并自动折算人民币/克；品牌与银行报价可继续使用自定义关注项。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="dashboardFilter.goldType" clearable placeholder="全部类型" style="width: 160px" @change="reloadDashboard">
          <el-option v-for="type in goldTypes" :key="type.value" :label="type.label" :value="type.value" />
        </el-select>
        <el-switch v-model="dashboardFilter.enabledOnly" active-text="仅启用" @change="reloadDashboard" />
        <el-button :loading="refreshing" type="primary" @click="refreshQuotes">刷新实时金价</el-button>
      </div>
    </section>

    <section class="public-quotes">
      <div class="public-heading">
        <div><span class="live-dot"></span><b>免配置实时行情</b><small>数据每 60 秒自动刷新 · 下次刷新 {{ publicCountdown }} 秒</small></div>
        <el-button :loading="publicLoading" @click="loadPublicQuotes(true)">立即刷新</el-button>
      </div>
      <el-alert v-if="publicError" :title="publicError" type="warning" :closable="false" show-icon />
      <div class="public-card-grid" v-loading="publicLoading">
        <article v-for="quote in publicQuotes?.quotes || []" :key="quote.code" class="public-card">
          <small>{{ quote.code.startsWith('JEWELRY_') ? '品牌首饰零售价' : quote.converted ? '实时折算参考' : '国际市场现货' }}</small>
          <h3>{{ quote.displayName }}</h3>
          <div><strong>{{ formatNumber(quote.price, quote.converted ? 4 : 2) }}</strong><span>{{ quote.unit }}</span></div>
          <p>{{ quote.description }}</p>
        </article>
      </div>
      <footer v-if="publicQuotes"><span>行情时间 {{ formatTime(publicQuotes.quoteTime) }}</span><span>USD/CNY {{ formatNumber(publicQuotes.usdCny, 4) }}</span><span>来源 {{ publicQuotes.source }}</span></footer>
    </section>
    <div class="status-grid">
      <div class="status-card"><small>关注项</small><b>{{ quoteStatus?.watchCount || 0 }}</b><span>已配置金价</span></div>
      <div class="status-card"><small>已有行情</small><b>{{ quoteStatus?.quotedCount || 0 }}</b><span>有最新价和时间</span></div>
      <div class="status-card warning"><small>缺失行情</small><b>{{ quoteStatus?.missingQuoteCount || 0 }}</b><span>待接口或手动补齐</span></div>
      <div class="status-card"><small>最近刷新</small><b>{{ formatTime(quoteStatus?.lastRefreshTime) }}</b><span>成功 {{ quoteStatus?.recentSuccess || 0 }} / 失败 {{ quoteStatus?.recentFailed || 0 }}</span></div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="金价看板" name="dashboard">
        <div class="quote-card-grid" v-loading="loading">
          <el-empty v-if="!watches.length" description="暂无金价关注项，可以先新增伦敦金、同花顺黄金或品牌首饰金价" />
          <article v-for="item in watches" :key="item.id" class="quote-card" :class="typeClass(item.goldType)">
            <div class="quote-card-top">
              <div>
                <span class="quote-type">{{ typeName(item.goldType) }}</span>
                <h3>{{ item.displayName }}</h3>
                <p>{{ item.brandName || item.sourceName || '自定义来源' }}</p>
              </div>
              <el-tag :type="item.enabled ? 'success' : 'info'">{{ item.enabled ? '启用' : '停用' }}</el-tag>
            </div>
            <div class="quote-price-row">
              <strong>{{ formatNumber(item.latestPrice, 2) }}</strong>
              <span>{{ item.unit }}</span>
              <em :class="changeClass(item.changePercent)">{{ formatChange(item.changeAmount, item.changePercent) }}</em>
            </div>
            <div class="quote-metrics">
              <span>高 <b>{{ formatNumber(item.highPrice, 2) }}</b></span>
              <span>低 <b>{{ formatNumber(item.lowPrice, 2) }}</b></span>
              <span>开 <b>{{ formatNumber(item.openPrice, 2) }}</b></span>
              <span>昨收 <b>{{ formatNumber(item.previousClose, 2) }}</b></span>
              <span>买价 <b>{{ formatNumber(item.buyPrice, 2) }}</b></span>
              <span>卖价 <b>{{ formatNumber(item.sellPrice, 2) }}</b></span>
            </div>
            <footer>
              <span>{{ formatTime(item.quoteTime) }}</span>
              <el-button link type="primary" @click="openWatch(item)">编辑</el-button>
            </footer>
          </article>
        </div>
      </el-tab-pane>

      <el-tab-pane label="关注列表" name="watch">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>金价关注项</span>
              <div>
                <el-button :loading="refreshing" type="primary" @click="refreshQuotes">刷新实时金价</el-button>
                <el-button @click="collect">立即采集</el-button>
                <el-button type="primary" @click="openWatch()">新增金价</el-button>
              </div>
            </div>
          </template>
          <el-form inline>
            <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="名称/品牌/来源" /></el-form-item>
            <el-form-item label="类型"><el-select v-model="filters.goldType" clearable style="width: 170px"><el-option v-for="type in goldTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></el-form-item>
            <el-button @click="loadWatches">查询</el-button>
          </el-form>
          <el-table :data="watches">
            <el-table-column prop="displayName" label="名称" min-width="150" />
            <el-table-column label="类型" width="130"><template #default="{ row }">{{ typeName(row.goldType) }}</template></el-table-column>
            <el-table-column prop="brandName" label="品牌/渠道" width="130" />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column label="最新价" width="110"><template #default="{ row }">{{ formatNumber(row.latestPrice, 2) }}</template></el-table-column>
            <el-table-column label="涨跌" width="150"><template #default="{ row }"><span :class="changeClass(row.changePercent)">{{ formatChange(row.changeAmount, row.changePercent) }}</span></template></el-table-column>
            <el-table-column label="买/卖" width="150"><template #default="{ row }">{{ formatNumber(row.buyPrice, 2) }} / {{ formatNumber(row.sellPrice, 2) }}</template></el-table-column>
            <el-table-column label="高/低" width="150"><template #default="{ row }">{{ formatNumber(row.highPrice, 2) }} / {{ formatNumber(row.lowPrice, 2) }}</template></el-table-column>
            <el-table-column label="行情时间" width="180"><template #default="{ row }">{{ formatTime(row.quoteTime) }}</template></el-table-column>
            <el-table-column prop="sourceName" label="来源" min-width="120" />
            <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch :model-value="row.enabled" @change="onToggle(row, $event)" /></template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openWatch(row)">编辑</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="接口配置" name="configs">
        <el-card>
          <template #header><div class="card-header"><span>金价接口</span><el-button type="primary" @click="openConfig()">新增接口</el-button></div></template>
          <el-alert type="info" :closable="false" class="config-tip" title="接口地址支持占位符：{type}/{goldType}/{brand}/{unit}。返回 JSON 中会自动识别 price/latestPrice/current、buyPrice/sellPrice、high/low/open/prevClose 等字段。" />
          <el-table :data="configs">
            <el-table-column prop="apiName" label="名称" width="140" />
            <el-table-column prop="purpose" label="用途" width="160" />
            <el-table-column prop="endpoint" label="地址" show-overflow-tooltip />
            <el-table-column prop="authType" label="认证" width="100" />
            <el-table-column prop="maskedApiKey" label="API Key" width="140" />
            <el-table-column label="最近测试" width="220"><template #default="{ row }"><el-tag v-if="row.lastTestSuccess !== null" :type="row.lastTestSuccess ? 'success' : 'danger'">{{ row.lastTestMessage }}</el-tag><span v-else>未测试</span></template></el-table-column>
            <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" @click="testConfig(row)">测试</el-button><el-button link @click="openConfig(row)">编辑</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="采集结果" name="results">
        <el-card>
          <el-table :data="results">
            <el-table-column prop="collectedAt" label="采集时间" width="180" />
            <el-table-column label="金价"><template #default="{ row }">{{ watchName(row.watchItemId) }}</template></el-table-column>
            <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
            <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="失败原因" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="watchVisible" title="金价关注项" width="760px">
      <el-form :model="watchForm" label-width="100px">
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="类型"><el-select v-model="watchForm.goldType"><el-option v-for="type in goldTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="名称"><el-input v-model="watchForm.displayName" placeholder="如：同花顺黄金、周大福足金" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="品牌/渠道"><el-input v-model="watchForm.brandName" placeholder="如：周大福、浙商银行" /></el-form-item></el-col><el-col :span="12"><el-form-item label="单位"><el-input v-model="watchForm.unit" placeholder="USD/OZ、CNY/G、元/克" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="最新价"><el-input-number v-model="watchForm.latestPrice" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="涨跌额"><el-input-number v-model="watchForm.changeAmount" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="涨跌幅"><el-input-number v-model="watchForm.changePercent" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="最高"><el-input-number v-model="watchForm.highPrice" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="最低"><el-input-number v-model="watchForm.lowPrice" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="今开"><el-input-number v-model="watchForm.openPrice" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="昨收"><el-input-number v-model="watchForm.previousClose" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="买价"><el-input-number v-model="watchForm.buyPrice" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="卖价"><el-input-number v-model="watchForm.sellPrice" :precision="4" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item label="行情时间"><el-date-picker v-model="watchForm.quoteTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可手动记录，也可由接口刷新" style="width:100%" /></el-form-item>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="来源名称"><el-input v-model="watchForm.sourceName" placeholder="如：同花顺、上海黄金交易所" /></el-form-item></el-col><el-col :span="12"><el-form-item label="来源地址"><el-input v-model="watchForm.sourceUrl" /></el-form-item></el-col></el-row>
        <el-form-item label="备注"><el-input v-model="watchForm.remark" type="textarea" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="watchForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="watchVisible = false">取消</el-button><el-button type="primary" @click="saveWatch">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="configVisible" title="金价接口配置" width="680px">
      <el-form :model="configForm" label-width="100px">
        <el-form-item label="接口名称"><el-input v-model="configForm.apiName" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="configForm.purpose" /></el-form-item>
        <el-form-item label="接口地址"><el-input v-model="configForm.endpoint" placeholder="https://api.example.com/gold?type={type}&brand={brand}" /></el-form-item>
        <el-form-item label="认证方式"><el-select v-model="configForm.authType"><el-option label="无认证" value="NONE" /><el-option label="Bearer" value="BEARER" /><el-option label="查询参数 api_key" value="QUERY_KEY" /></el-select></el-form-item>
        <el-form-item label="API Key"><el-input v-model="configForm.apiKey" type="password" show-password /></el-form-item>
        <el-form-item label="每分钟限制"><el-input-number v-model="configForm.rateLimitPerMinute" :min="1" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="configForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="configVisible = false">取消</el-button><el-button type="primary" @click="saveConfig">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import {
  collectGold,
  getGoldQuoteStatus,
  getGoldPublicQuotes,
  listGoldApiConfigs,
  listGoldCollectionResults,
  listGoldWatchItems,
  refreshGoldQuotes,
  saveGoldApiConfig,
  updateGoldApiConfig,
  saveGoldWatchItem,
  testGoldApiConfig,
  toggleGoldWatchItem,
  updateGoldWatchItem,
  type GoldApiConfig,
  type GoldCollectionResult,
  type GoldQuoteStatusResponse,
  type GoldPublicQuoteResponse,
  type GoldWatchItem,
  type GoldWatchPayload,
} from '@/api/gold';

const tab = ref('dashboard');
const goldTypes = [
  { label: '伦敦金', value: 'LONDON_GOLD' },
  { label: '国内金价', value: 'DOMESTIC_GOLD' },
  { label: '品牌首饰金价', value: 'BRAND_JEWELRY' },
  { label: '银行/平台报价金', value: 'PLATFORM_GOLD' },
];
const filters = reactive({ keyword: '', goldType: '' });
const dashboardFilter = reactive<{ goldType: string; enabledOnly: boolean }>({ goldType: '', enabledOnly: true });
const watches = ref<GoldWatchItem[]>([]);
const configs = ref<GoldApiConfig[]>([]);
const results = ref<GoldCollectionResult[]>([]);
const quoteStatus = ref<GoldQuoteStatusResponse>();
const loading = ref(false);
const refreshing = ref(false);
const publicQuotes = ref<GoldPublicQuoteResponse>();
const publicLoading = ref(false);
const publicError = ref('');
const publicCountdown = ref(60);
let publicTimer: number | undefined;
const watchVisible = ref(false);
const configVisible = ref(false);
const editing = ref<number>();
const editingConfig = ref<number>();

const blankWatch = (): GoldWatchPayload => ({ goldType: 'PLATFORM_GOLD', brandName: '', displayName: '', unit: 'CNY/G', latestPrice: null, changeAmount: null, changePercent: null, highPrice: null, lowPrice: null, openPrice: null, previousClose: null, buyPrice: null, sellPrice: null, quoteTime: null, sourceName: '', sourceUrl: '', remark: '', enabled: true });
const watchForm = reactive<GoldWatchPayload>(blankWatch());
const blankConfig = () => ({ apiName: '', purpose: '金价行情', endpoint: '', authType: 'NONE', apiKey: '', rateLimitPerMinute: 60, enabled: true });
const configForm = reactive(blankConfig());

async function loadWatches() { watches.value = (await listGoldWatchItems({ ...filters, goldType: filters.goldType || undefined })).data; }
async function loadDashboard() { loading.value = true; try { watches.value = (await listGoldWatchItems({ goldType: dashboardFilter.goldType || undefined, enabled: dashboardFilter.enabledOnly ? true : undefined })).data; } finally { loading.value = false; } }
async function loadQuoteStatus() { quoteStatus.value = (await getGoldQuoteStatus({ goldType: dashboardFilter.goldType || undefined, enabledOnly: dashboardFilter.enabledOnly })).data; }
async function reloadDashboard() { await Promise.all([loadDashboard(), loadQuoteStatus()]); }
async function loadConfigs() { configs.value = (await listGoldApiConfigs()).data; }
async function loadResults() { results.value = (await listGoldCollectionResults()).data; }

async function loadPublicQuotes(showMessage = false) {
  publicLoading.value = true;
  try {
    publicQuotes.value = (await getGoldPublicQuotes()).data;
    publicError.value = '';
    publicCountdown.value = publicQuotes.value.refreshIntervalSeconds || 60;
    if (showMessage) ElMessage.success('实时金价已刷新');
  } catch (error) {
    publicError.value = '公开行情源暂时不可用，已保留页面上的最近一次结果，请稍后重试。';
  } finally { publicLoading.value = false; }
}

function startPublicRefresh() {
  publicTimer = window.setInterval(() => {
    publicCountdown.value -= 1;
    if (publicCountdown.value <= 0) void loadPublicQuotes();
  }, 1000);
}
async function refreshQuotes() {
  refreshing.value = true;
  try {
    const result = (await refreshGoldQuotes({ goldType: dashboardFilter.goldType || undefined, enabledOnly: dashboardFilter.enabledOnly })).data;
    ElMessage.success(`金价刷新完成：成功 ${result.success}，失败 ${result.failed}`);
    await Promise.all([reloadDashboard(), loadResults()]);
  } finally { refreshing.value = false; }
}

function openWatch(row?: GoldWatchItem) { editing.value = row?.id; Object.assign(watchForm, blankWatch(), row || {}); watchVisible.value = true; }
async function saveWatch() { if (!watchForm.displayName || !watchForm.unit) { ElMessage.warning('请填写名称和单位'); return; } editing.value ? await updateGoldWatchItem(editing.value, watchForm) : await saveGoldWatchItem(watchForm); watchVisible.value = false; await reloadDashboard(); }
function onToggle(row: GoldWatchItem, value: string | number | boolean) { void toggleGoldWatchItem(row.id, Boolean(value)).then(reloadDashboard); }
function openConfig(row?: GoldApiConfig) { editingConfig.value = row?.id; Object.assign(configForm, blankConfig(), row || {}, { apiKey: '' }); configVisible.value = true; }
async function saveConfig() { if (!configForm.apiName || !configForm.endpoint) { ElMessage.warning('请填写接口名称和地址'); return; } editingConfig.value ? await updateGoldApiConfig(editingConfig.value, configForm) : await saveGoldApiConfig(configForm); configVisible.value = false; await loadConfigs(); }
async function testConfig(row: GoldApiConfig) { const ok = (await testGoldApiConfig(row.id)).data; ElMessage[ok ? 'success' : 'error'](ok ? '接口测试成功' : '接口测试失败'); await loadConfigs(); }
async function collect() { const count = (await collectGold()).data; ElMessage.success(`已处理 ${count} 个金价关注项`); await Promise.all([reloadDashboard(), loadResults()]); }
async function onTabChange(name: string | number) { if (name === 'dashboard') await reloadDashboard(); if (name === 'watch') await loadWatches(); if (name === 'configs') await loadConfigs(); if (name === 'results') await loadResults(); }
function watchName(id: number) { const watch = watches.value.find(item => item.id === id); return watch ? watch.displayName : `#${id}`; }
function typeName(value: string) { return goldTypes.find(item => item.value === value)?.label || value; }
function typeClass(value: string) { return `gold-type-${value.toLowerCase().replace(/_/g, '-')}`; }
function changeClass(value?: number | null) { if (value == null || value === 0) return 'gold-change-flat'; return value > 0 ? 'gold-change-up' : 'gold-change-down'; }
function formatChange(amount?: number | null, percent?: number | null) { const a = amount == null ? '-' : `${amount > 0 ? '+' : ''}${Number(amount).toFixed(2)}`; const p = percent == null ? '-' : `${percent > 0 ? '+' : ''}${Number(percent).toFixed(2)}%`; return `${a} / ${p}`; }
function formatNumber(value?: number | null, digits = 2) { if (value == null) return '-'; return Number(value).toFixed(digits); }
function formatTime(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN') : '-'; }
watch(() => filters.goldType, value => { if (value) dashboardFilter.goldType = value; });
onMounted(async () => { await Promise.all([loadPublicQuotes(), reloadDashboard(), loadConfigs()]); startPublicRefresh(); });
onBeforeUnmount(() => { if (publicTimer) window.clearInterval(publicTimer); });
</script>

<style scoped>
.gold-page { display: grid; gap: 18px; }
.gold-hero { display: flex; justify-content: space-between; gap: 18px; align-items: center; padding: 28px; border-radius: 24px; color: #fff; background: radial-gradient(circle at 82% 10%, rgba(255, 224, 145, .55), transparent 28%), linear-gradient(135deg, #3b2410, #9a641b 48%, #d9a338); box-shadow: 0 18px 48px rgba(166, 111, 24, .25); }
.gold-hero h2 { margin: 4px 0 8px; font-size: 30px; }
.gold-hero p { margin: 0; color: rgba(255,255,255,.82); }
.eyebrow { letter-spacing: .16em; text-transform: uppercase; font-size: 12px; }
.hero-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 12px; align-items: center; }
.public-quotes { padding: 20px; border-radius: 22px; background: #fff; box-shadow: 0 12px 32px rgba(35,24,10,.08); }
.public-heading, .public-heading > div, .public-quotes > footer { display: flex; align-items: center; gap: 12px; }
.public-heading { justify-content: space-between; margin-bottom: 16px; }
.public-heading small, .public-quotes > footer { color: #8a7a64; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 5px rgba(34,197,94,.13); }
.public-card-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; min-height: 150px; }
.public-card { padding: 20px; border: 1px solid rgba(214,158,46,.22); border-radius: 18px; background: linear-gradient(135deg,#fffaf0,#fff); }
.public-card small, .public-card p { color: #8a7a64; }
.public-card h3 { margin: 7px 0 13px; }
.public-card strong { margin-right: 8px; font-size: 32px; color: #b45309; }
.public-card p { margin: 12px 0 0; font-size: 13px; line-height: 1.6; }
.public-quotes > footer { justify-content: flex-end; margin-top: 14px; font-size: 12px; }.status-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.status-card { padding: 18px; border-radius: 18px; background: #fff; box-shadow: 0 12px 32px rgba(35, 24, 10, .08); }
.status-card small, .quote-card small { color: #8a7a64; }
.status-card b { display: block; margin: 8px 0 4px; font-size: 24px; color: #2f2519; }
.status-card span { color: #8a7a64; font-size: 13px; }
.status-card.warning b { color: #b7791f; }
.quote-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; min-height: 220px; }
.quote-card { padding: 20px; border-radius: 22px; background: linear-gradient(180deg, #fffaf0, #fff); border: 1px solid rgba(214, 158, 46, .18); box-shadow: 0 14px 36px rgba(79, 52, 16, .08); }
.quote-card-top { display: flex; justify-content: space-between; gap: 12px; }
.quote-card h3 { margin: 8px 0 4px; font-size: 22px; }
.quote-card p { margin: 0; color: #8a7a64; }
.quote-type { display: inline-flex; padding: 4px 10px; border-radius: 999px; background: rgba(214, 158, 46, .14); color: #9a641b; font-size: 12px; }
.quote-price-row { display: flex; align-items: baseline; gap: 10px; margin: 22px 0 16px; }
.quote-price-row strong { font-size: 34px; color: #b45309; }
.quote-price-row em { font-style: normal; margin-left: auto; }
.quote-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; padding: 12px; border-radius: 14px; background: rgba(255, 247, 237, .85); }
.quote-metrics span { color: #8a7a64; font-size: 13px; }
.quote-metrics b { color: #2f2519; }
.quote-card footer { display: flex; justify-content: space-between; align-items: center; margin-top: 14px; color: #8a7a64; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.config-tip { margin-bottom: 14px; }
.gold-change-up { color: #dc2626; font-weight: 700; }
.gold-change-down { color: #16a34a; font-weight: 700; }
.gold-change-flat { color: #64748b; }
.gold-type-london-gold { background: linear-gradient(180deg, #fff7ed, #fff); }
.gold-type-domestic-gold { background: linear-gradient(180deg, #fefce8, #fff); }
.gold-type-brand-jewelry { background: linear-gradient(180deg, #fff1f2, #fff); }
.gold-type-platform-gold { background: linear-gradient(180deg, #eff6ff, #fff); }
@media (max-width: 960px) { .public-card-grid { grid-template-columns: 1fr; } .public-heading, .public-quotes > footer { align-items: flex-start; flex-direction: column; } .gold-hero { flex-direction: column; align-items: flex-start; } .status-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>