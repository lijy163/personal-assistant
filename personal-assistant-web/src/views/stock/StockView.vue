<template>
  <div class="task-page stock-page">
    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="大盘云图" name="map">
        <el-card class="market-map-card">
          <template #header>
            <div class="task-card-header market-map-header">
              <span>自选股云图</span>
              <div class="market-map-actions">
                <el-select v-model="mapFilters.market" clearable placeholder="全部市场" style="width: 130px" @change="reloadMarketView">
                  <el-option v-for="m in markets" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
                <el-switch v-model="mapFilters.enabledOnly" active-text="仅启用" @change="reloadMarketView" />
                <el-button :loading="quoteRefreshing" type="primary" @click="refreshQuotes">刷新实时行情</el-button>
                <el-button :loading="mapLoading" @click="reloadMarketView">刷新云图</el-button>
              </div>
            </div>
          </template>

          <div class="quote-status-strip">
            <div class="quote-status-item"><small>关注数</small><b>{{ quoteStatus?.watchCount || 0 }}</b></div>
            <div class="quote-status-item"><small>已有行情</small><b>{{ quoteStatus?.quotedCount || 0 }}</b></div>
            <div class="quote-status-item"><small>缺失行情</small><b>{{ quoteStatus?.missingQuoteCount || 0 }}</b></div>
            <div class="quote-status-item"><small>上次行情</small><b>{{ formatTime(quoteStatus?.lastQuoteTime) }}</b></div>
            <div class="quote-status-item"><small>最近刷新</small><b>成 {{ quoteStatus?.recentSuccess || 0 }} / 败 {{ quoteStatus?.recentFailed || 0 }}</b></div>
          </div>

          <div class="market-map-summary">
            <div class="map-stat map-stat-total"><small>股票数</small><b>{{ marketMap?.stats.total || 0 }}</b></div>
            <div class="map-stat map-stat-up"><small>上涨</small><b>{{ marketMap?.stats.up || 0 }}</b></div>
            <div class="map-stat map-stat-flat"><small>平盘</small><b>{{ marketMap?.stats.flat || 0 }}</b></div>
            <div class="map-stat map-stat-down"><small>下跌</small><b>{{ marketMap?.stats.down || 0 }}</b></div>
            <div class="map-stat"><small>平均涨跌幅</small><b :class="changeClass(marketMap?.stats.averageChangePercent)">{{ formatPercent(marketMap?.stats.averageChangePercent) }}</b></div>
          </div>

          <div v-loading="mapLoading" class="market-map-wrap">
            <el-empty v-if="!marketMap?.industries.length" description="暂无云图数据，请先维护关注股票，或点击刷新实时行情" />
            <div v-else class="market-map-grid">
              <section v-for="industry in marketMap.industries" :key="industry.industry" class="industry-panel">
                <div class="industry-title">
                  <div>
                    <b>{{ industry.industry }}</b>
                    <small>{{ industry.count }} 只 · 均值 {{ formatPercent(industry.averageChangePercent) }}</small>
                  </div>
                  <span :class="changeClass(industry.averageChangePercent)">{{ formatPercent(industry.averageChangePercent) }}</span>
                </div>
                <div class="stock-tile-grid">
                  <button v-for="item in industry.children" :key="item.id" class="stock-tile" :class="tileClass(item.changePercent)" :style="tileStyle(item)" :title="tileTitle(item)" @click="selectedMapItem = item">
                    <strong>{{ item.stockName }}</strong>
                    <span>{{ formatPercent(item.changePercent) }}</span>
                    <small>{{ item.stockCode }}</small>
                  </button>
                </div>
              </section>
            </div>
          </div>

          <div class="market-map-help">
            第二版已支持手动刷新 + 调度刷新。调度任务类型使用 STOCK_COLLECT 时，会复用实时行情刷新逻辑，并把成功/失败写入采集结果。
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="资金流向" name="fundFlow">
        <StockFundFlowPanel />
      </el-tab-pane>

      <el-tab-pane label="关注列表" name="watch">
        <el-card>
          <template #header>
            <div class="task-card-header">
              <span>股票关注</span>
              <div>
                <el-button :loading="quoteRefreshing" type="primary" @click="refreshQuotes">刷新实时行情</el-button>
                <el-button @click="collect">立即采集</el-button>
                <el-button type="primary" @click="openWatch()">新增股票</el-button>
              </div>
            </div>
          </template>
          <div class="quote-status-inline">
            <el-tag type="info">关注 {{ quoteStatus?.watchCount || 0 }}</el-tag>
            <el-tag type="success">已有行情 {{ quoteStatus?.quotedCount || 0 }}</el-tag>
            <el-tag :type="quoteStatus?.missingQuoteCount ? 'warning' : 'info'">缺失 {{ quoteStatus?.missingQuoteCount || 0 }}</el-tag>
            <span>上次行情：{{ formatTime(quoteStatus?.lastQuoteTime) }}</span>
            <span>上次刷新：{{ formatTime(quoteStatus?.lastRefreshTime) }}</span>
          </div>
          <el-form inline>
            <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable /></el-form-item>
            <el-form-item label="市场"><el-select v-model="filters.market" clearable style="width: 120px"><el-option v-for="m in markets" :key="m.value" :label="m.label" :value="m.value" /></el-select></el-form-item>
            <el-form-item label="标签"><el-input v-model="filters.tag" clearable /></el-form-item>
            <el-button @click="loadWatches">查询</el-button>
          </el-form>
          <el-table :data="watches">
            <el-table-column prop="stockCode" label="代码" width="120" />
            <el-table-column prop="stockName" label="名称" min-width="120" />
            <el-table-column prop="market" label="市场" width="90" />
            <el-table-column prop="industry" label="行业" width="120" />
            <el-table-column label="涨跌幅" width="110"><template #default="{ row }"><span :class="changeClass(row.changePercent)">{{ formatPercent(row.changePercent) }}</span></template></el-table-column>
            <el-table-column label="最新价" width="110"><template #default="{ row }">{{ formatNumber(row.latestPrice, 2) }}</template></el-table-column>
            <el-table-column label="市值/权重" width="120"><template #default="{ row }">{{ formatCompact(row.marketValue) }}</template></el-table-column>
            <el-table-column label="行情时间" width="180"><template #default="{ row }">{{ formatTime(row.quoteTime) }}</template></el-table-column>
            <el-table-column prop="tags" label="标签" min-width="120" />
            <el-table-column prop="reason" label="关注理由" min-width="160" show-overflow-tooltip />
            <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch :model-value="row.enabled" @change="onToggle(row, $event)" /></template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openWatch(row)">编辑</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="接口配置" name="configs">
        <el-card>
          <template #header><div class="task-card-header"><span>行情接口</span><el-button type="primary" @click="configVisible = true">新增接口</el-button></div></template>
          <el-table :data="configs">
            <el-table-column prop="apiName" label="名称" />
            <el-table-column prop="purpose" label="用途" />
            <el-table-column prop="endpoint" label="地址" show-overflow-tooltip />
            <el-table-column prop="authType" label="认证" width="100" />
            <el-table-column prop="maskedApiKey" label="API Key（脱敏）" />
            <el-table-column label="最近测试"><template #default="{ row }"><el-tag v-if="row.lastTestSuccess !== null" :type="row.lastTestSuccess ? 'success' : 'danger'">{{ row.lastTestMessage }}</el-tag><span v-else>未测试</span></template></el-table-column>
            <el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="testConfig(row)">测试</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="采集结果" name="results">
        <el-card>
          <el-table :data="results">
            <el-table-column prop="collectedAt" label="采集时间" width="180" />
            <el-table-column label="股票"><template #default="{ row }">{{ watchName(row.watchItemId) }}</template></el-table-column>
            <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
            <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="失败原因" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="watchVisible" title="股票关注项" width="680px">
      <el-form :model="watchForm" label-width="100px">
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="股票代码"><el-input v-model="watchForm.stockCode" /></el-form-item></el-col><el-col :span="12"><el-form-item label="股票名称"><el-input v-model="watchForm.stockName" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="市场"><el-radio-group v-model="watchForm.market"><el-radio-button v-for="m in markets" :key="m.value" :value="m.value">{{ m.label }}</el-radio-button></el-radio-group></el-form-item></el-col><el-col :span="12"><el-form-item label="行业/板块"><el-input v-model="watchForm.industry" placeholder="如：半导体、银行、医药" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="最新价"><el-input-number v-model="watchForm.latestPrice" :precision="4" :min="0" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="涨跌幅%"><el-input-number v-model="watchForm.changePercent" :precision="4" controls-position="right" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="市值/权重"><el-input-number v-model="watchForm.marketValue" :precision="2" :min="0" controls-position="right" style="width: 100%" /></el-form-item></el-col></el-row>
        <el-form-item label="行情时间"><el-date-picker v-model="watchForm.quoteTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可手动记录，也可后续采集更新" style="width: 100%" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="watchForm.tags" placeholder="价值、科技、长期" /></el-form-item>
        <el-form-item label="关注理由"><el-input v-model="watchForm.reason" type="textarea" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="watchForm.remark" type="textarea" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="watchForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="watchVisible = false">取消</el-button><el-button type="primary" @click="saveWatch">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="configVisible" title="股票接口配置" width="640px">
      <el-form :model="configForm" label-width="100px">
        <el-form-item label="接口名称"><el-input v-model="configForm.apiName" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="configForm.purpose" /></el-form-item>
        <el-form-item label="接口地址"><el-input v-model="configForm.endpoint" placeholder="https://api.example.com/quote/{market}/{code}" /></el-form-item>
        <el-form-item label="认证方式"><el-select v-model="configForm.authType"><el-option label="无认证" value="NONE" /><el-option label="Bearer" value="BEARER" /><el-option label="查询参数 api_key" value="QUERY_KEY" /></el-select></el-form-item>
        <el-form-item label="API Key"><el-input v-model="configForm.apiKey" type="password" show-password /></el-form-item>
        <el-form-item label="每分钟限制"><el-input-number v-model="configForm.rateLimitPerMinute" :min="1" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="configForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="configVisible = false">取消</el-button><el-button type="primary" @click="saveConfig">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="mapDetailVisible" :title="selectedMapItem?.stockName" size="420px">
      <el-descriptions v-if="selectedMapItem" :column="1" border>
        <el-descriptions-item label="代码">{{ selectedMapItem.stockCode }}</el-descriptions-item>
        <el-descriptions-item label="市场">{{ marketName(selectedMapItem.market) }}</el-descriptions-item>
        <el-descriptions-item label="行业">{{ selectedMapItem.industry }}</el-descriptions-item>
        <el-descriptions-item label="最新价">{{ formatNumber(selectedMapItem.latestPrice, 4) }}</el-descriptions-item>
        <el-descriptions-item label="涨跌幅"><span :class="changeClass(selectedMapItem.changePercent)">{{ formatPercent(selectedMapItem.changePercent) }}</span></el-descriptions-item>
        <el-descriptions-item label="市值/权重">{{ formatCompact(selectedMapItem.marketValue) }}</el-descriptions-item>
        <el-descriptions-item label="行情时间">{{ formatTime(selectedMapItem.quoteTime) }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ selectedMapItem.tags || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import StockFundFlowPanel from './StockFundFlowPanel.vue';
import {
  collectStocks,
  getStockMarketMap,
  getStockQuoteStatus,
  listApiConfigs,
  listCollectionResults,
  listWatchItems,
  refreshStockQuotes,
  saveApiConfig,
  saveWatchItem,
  testApiConfig,
  toggleWatchItem,
  updateWatchItem,
  type ApiConfig,
  type CollectionResult,
  type StockMapItem,
  type StockMarketMapResponse,
  type StockQuoteStatusResponse,
  type WatchItem,
  type WatchPayload,
} from '@/api/stock';

const tab = ref('map');
const markets = [{ label: 'A 股', value: 'CN' }, { label: '美股', value: 'US' }, { label: '港股', value: 'HK' }];
const filters = reactive({ keyword: '', market: '', tag: '' });
const mapFilters = reactive<{ market: string; enabledOnly: boolean }>({ market: 'CN', enabledOnly: true });
const watches = ref<WatchItem[]>([]);
const configs = ref<ApiConfig[]>([]);
const results = ref<CollectionResult[]>([]);
const marketMap = ref<StockMarketMapResponse>();
const quoteStatus = ref<StockQuoteStatusResponse>();
const mapLoading = ref(false);
const quoteRefreshing = ref(false);
const watchVisible = ref(false);
const configVisible = ref(false);
const editing = ref<number>();
const selectedMapItem = ref<StockMapItem>();
const mapDetailVisible = computed({ get: () => Boolean(selectedMapItem.value), set: value => { if (!value) selectedMapItem.value = undefined; } });

const blankWatch = (): WatchPayload => ({ stockCode: '', stockName: '', market: 'CN', industry: '', latestPrice: null, changePercent: null, marketValue: null, quoteTime: null, tags: '', reason: '', remark: '', enabled: true });
const watchForm = reactive<WatchPayload>(blankWatch());
const configForm = reactive({ apiName: '', purpose: '行情摘要', endpoint: '', authType: 'NONE', apiKey: '', rateLimitPerMinute: 60, enabled: true });

async function loadWatches() { watches.value = (await listWatchItems(filters)).data; }
async function loadQuoteStatus() { quoteStatus.value = (await getStockQuoteStatus({ market: mapFilters.market || undefined, enabledOnly: mapFilters.enabledOnly })).data; }
async function loadMarketMap() { mapLoading.value = true; try { marketMap.value = (await getStockMarketMap({ market: mapFilters.market || undefined, enabledOnly: mapFilters.enabledOnly })).data; } finally { mapLoading.value = false; } }
async function reloadMarketView() { await Promise.all([loadMarketMap(), loadQuoteStatus()]); }
async function loadConfigs() { configs.value = (await listApiConfigs()).data; }

async function refreshQuotes() {
  quoteRefreshing.value = true;
  try {
    const result = (await refreshStockQuotes({ market: mapFilters.market || undefined, enabledOnly: mapFilters.enabledOnly })).data;
    ElMessage.success(`行情刷新完成：成功 ${result.success}，失败 ${result.failed}`);
    await Promise.all([loadWatches(), reloadMarketView()]);
    if (tab.value === 'results') results.value = (await listCollectionResults()).data;
  } finally { quoteRefreshing.value = false; }
}

function openWatch(row?: WatchItem) { editing.value = row?.id; Object.assign(watchForm, blankWatch(), row || {}); watchVisible.value = true; }
async function saveWatch() { if (!watchForm.stockCode || !watchForm.stockName) { ElMessage.warning('请填写代码和名称'); return; } editing.value ? await updateWatchItem(editing.value, watchForm) : await saveWatchItem(watchForm); watchVisible.value = false; await Promise.all([loadWatches(), reloadMarketView()]); }
function onToggle(row: WatchItem, value: string | number | boolean) { void toggleWatchItem(row.id, Boolean(value)).then(async () => { await Promise.all([loadWatches(), reloadMarketView()]); }); }
async function saveConfig() { if (!configForm.apiName || !configForm.endpoint) { ElMessage.warning('请填写名称和地址'); return; } await saveApiConfig(configForm); configVisible.value = false; await loadConfigs(); }
async function testConfig(row: ApiConfig) { const ok = (await testApiConfig(row.id)).data; ElMessage[ok ? 'success' : 'error'](ok ? '接口测试成功' : '接口测试失败'); await loadConfigs(); }
async function collect() { const count = (await collectStocks()).data; ElMessage.success(`已处理 ${count} 个关注项`); await Promise.all([loadWatches(), reloadMarketView()]); results.value = (await listCollectionResults()).data; }
function watchName(id: number) { const watch = watches.value.find(item => item.id === id); return watch ? `${watch.stockName} (${watch.stockCode})` : `#${id}`; }
async function onTabChange(name: string | number) { if (name === 'map') await reloadMarketView(); if (name === 'watch') await Promise.all([loadWatches(), loadQuoteStatus()]); if (name === 'configs') await loadConfigs(); if (name === 'results') results.value = (await listCollectionResults()).data; }
function changeClass(value?: number | null) { if (value == null || value === 0) return 'stock-change-flat'; return value > 0 ? 'stock-change-up' : 'stock-change-down'; }
function tileClass(value?: number | null) { if (value == null || value === 0) return 'stock-tile-flat'; return value > 0 ? 'stock-tile-up' : 'stock-tile-down'; }
function tileStyle(item: StockMapItem) { const weight = Math.max(Number(item.weight || item.marketValue || 100), 60); const grow = Math.min(Math.max(Math.log10(weight + 10) * 2.4, 1.2), 8); const basis = Math.min(Math.max(grow * 58, 92), 260); const intensity = Math.min(Math.abs(Number(item.changePercent || 0)) / 6, 1); return { flexGrow: grow, flexBasis: `${basis}px`, '--tile-alpha': String(0.48 + intensity * 0.44) }; }
function tileTitle(item: StockMapItem) { return `${item.stockName} ${item.stockCode}\n行业：${item.industry}\n涨跌幅：${formatPercent(item.changePercent)}\n市值/权重：${formatCompact(item.marketValue)}`; }
function formatPercent(value?: number | null) { if (value == null) return '-'; const sign = value > 0 ? '+' : ''; return `${sign}${Number(value).toFixed(2)}%`; }
function formatNumber(value?: number | null, digits = 2) { if (value == null) return '-'; return Number(value).toFixed(digits); }
function formatCompact(value?: number | null) { if (value == null) return '-'; const num = Number(value); if (num >= 100000000) return `${(num / 100000000).toFixed(2)}亿`; if (num >= 10000) return `${(num / 10000).toFixed(2)}万`; return num.toFixed(2); }
function formatTime(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN') : '-'; }
function marketName(value: string) { return markets.find(item => item.value === value)?.label || value; }
watch(() => filters.market, value => { if (value) mapFilters.market = value; });
onMounted(async () => { await Promise.all([loadWatches(), loadConfigs(), reloadMarketView()]); });
</script>