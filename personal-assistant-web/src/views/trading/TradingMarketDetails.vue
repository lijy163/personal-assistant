<template>
  <div class="market-details">
    <el-card class="market-overview">
      <template #header><div class="section-title"><b>市场全景</b><span>涨跌家数已改为上证 + 深证官方统计口径</span></div></template>
      <div class="metric-grid">
        <div><small>上涨</small><strong class="up">{{ review.risingCount ?? '-' }}</strong></div>
        <div><small>下跌</small><strong class="down">{{ review.fallingCount ?? '-' }}</strong></div>
        <div><small>平盘</small><strong>{{ review.flatCount ?? '-' }}</strong></div>
        <div><small>涨跌比</small><strong>{{ breadthRatio }}</strong></div>
        <div><small>涨停 / 跌停</small><strong>{{ review.limitUpCount ?? '-' }} / {{ review.limitDownCount ?? '-' }}</strong></div>
        <div><small>炸板 / 炸板率</small><strong>{{ review.brokenBoardCount ?? '-' }} / {{ percent(review.brokenBoardRate) }}</strong></div>
        <div><small>最高连板</small><strong>{{ review.maxStreak ?? '-' }} 板</strong></div>
        <div><small>两市成交额</small><strong>{{ amount(review.turnoverAmount) }}</strong></div>
        <div><small>成交额环比</small><strong :class="tone(review.turnoverChange)">{{ percent(review.turnoverChange) }}</strong></div>
        <div><small>全市场涨幅中位数</small><strong :class="tone(details.marketMedian.change)">{{ percent(details.marketMedian.change) }}</strong><span class="metric-note">样本 {{ details.marketMedian.sampleCount || '-' }} &middot; 覆盖 {{ percent(details.marketMedian.coverage) }}</span></div>
      </div>
    </el-card>

    <el-card v-if="review.snapshotType === 'REALTIME'">
      <template #header><div class="section-title"><b>盘中同时间基准</b><span>仅与历史交易日相近时刻成交额比较</span></div></template>
      <div class="metric-grid">
        <div><small>成交额环比</small><strong :class="tone(details.intradayBenchmark.turnoverChange)">{{ percent(details.intradayBenchmark.turnoverChange) }}</strong></div>
        <div><small>历史同时间成交额</small><strong>{{ amount(details.intradayBenchmark.turnoverAmount) }}</strong></div>
        <div><small>有效历史样本</small><strong>{{ details.intradayBenchmark.sampleCount || 0 }} 日</strong></div>
        <div><small>可比状态</small><strong>{{ details.intradayBenchmark.status === 'AVAILABLE' ? '可比较' : '历史不足' }}</strong></div>
      </div>
    </el-card>

    <el-card v-if="details.dataQuality.status || review.collectionStatus">
      <template #header><div class="section-title"><b>数据质量</b><span>数据来源、采集状态与降级情况</span></div></template>
      <div class="metric-grid">
        <div><small>来源</small><strong>{{ details.dataQuality.source || review.dataSource || '-' }}</strong></div>
        <div><small>状态</small><strong>{{ details.dataQuality.status || review.collectionStatus || '-' }}</strong></div>
        <div><small>采集时间</small><strong>{{ collectedAt }}</strong></div>
        <div><small>降级项</small><strong>{{ details.dataQuality.warningCount || details.warnings.length || 0 }} 项</strong></div>
      </div>
    </el-card>

    <el-alert v-if="details.warnings.length" :title="'部分数据已降级：'+details.warnings.join('；')" type="warning" :closable="false" show-icon/>

    <el-card v-if="details.streakLadder.length">
      <template #header><div class="section-title"><b>连板梯队</b><span>按当前涨停池连板高度统计</span></div></template>
      <div class="ladder-grid"><div v-for="item in details.streakLadder" :key="item.level"><small>{{ item.label }}</small><strong>{{ item.count }} 家</strong></div></div>
    </el-card>

    <el-card v-if="dimensionRows.length">
      <template #header><div class="section-title"><b>情绪评分明细</b><span>展示权重、得分与计算口径</span></div></template>
      <div class="dimension-grid"><div v-for="item in dimensionRows" :key="item.label"><span>{{ item.label }} · {{ item.weight }}%</span><strong>{{ item.score }}</strong><small>{{ item.reason }}</small></div></div>
    </el-card>

    <el-card v-if="details.indices.length">
      <template #header><b>指数与市场宽度</b></template>
      <el-table :data="details.indices" size="small">
        <el-table-column prop="name" label="市场"/>
        <el-table-column label="涨跌"><template #default="s"><span :class="tone(s.row.change)">{{ percent(s.row.change) }}</span></template></el-table-column>
        <el-table-column prop="rising" label="上涨"/>
        <el-table-column prop="falling" label="下跌"/>
        <el-table-column prop="flat" label="平盘"/>
        <el-table-column label="成交额"><template #default="s">{{ amount(s.row.turnover) }}</template></el-table-column>
      </el-table>
    </el-card>

    <div v-if="hasRankings" class="ranking-section">
      <el-radio-group v-model="sectorLevel" size="small" class="level-switch"><el-radio-button :value="1">&#19968;&#32423;&#34892;&#19994;</el-radio-button><el-radio-button :value="2">&#20108;&#32423;&#34892;&#19994;</el-radio-button><el-radio-button :value="3">&#19977;&#32423;&#34892;&#19994;</el-radio-button></el-radio-group>
      <div class="ranking-grid">
      <el-card v-for="group in rankingGroups" :key="group.key" class="ranking-card">
        <template #header><div class="section-title"><b>{{ group.title }}</b><span>{{ group.note }}</span></div></template>
        <div v-if="group.rows.length" class="ranking-list">
          <div v-for="(item,index) in group.rows" :key="item.code || item.name" class="ranking-row">
            <span class="rank">{{ index + 1 }}</span>
            <div class="sector-name"><b>{{ item.name }}</b><small>{{ item.levelLabel || (item.level===1?'一级行业':'') }}<template v-if="item.leader"> · 领涨 {{ item.leader }}</template></small></div>
            <div class="sector-value">
              <b v-if="group.key==='turnover'">{{ amount(item.turnover) }}</b>
              <b v-else-if="group.key==='limitUp'" class="up">{{ item.limitUpCount }} 家涨停</b>
              <b v-else :class="tone(item.change)">{{ percent(item.change) }}</b>
              <small v-if="group.key!=='limitUp'">涨 {{ item.rising }} / 跌 {{ item.falling }}</small>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="52"/>
      </el-card>
      </div>
    </div>
    <el-alert v-else title="该记录来自旧版采集，点击“重试自动获取”后可生成板块成交额、上涨、下跌和涨停榜。" type="info" :closable="false"/>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { DailyReview } from '@/api/tradingReview';

interface IndexMetric { name:string; change:number; turnover:number; rising:number; falling:number; flat:number }
interface SectorMetric { code?:string; name:string; level?:number; levelLabel?:string; change?:number; turnover?:number; rising?:number; falling?:number; flat?:number; leader?:string; leaderChange?:number; limitUpCount?:number }
interface MarketMedian { change?:number; sampleCount?:number; expectedCount?:number; coverage?:number }
interface IntradayBenchmark { sampleCount?:number; turnoverAmount?:number; turnoverChange?:number; status?:string }
interface DataQuality { source?:string; status?:string; warningCount?:number; collectedAt?:string }
interface StreakMetric { level:number; label:string; count:number }
interface DimensionMetric { label:string; score:number; weight:number; reason:string }
interface MarketDetails { indices:IndexMetric[]; streakLadder:StreakMetric[]; warnings:string[]; marketMedian:MarketMedian; intradayBenchmark:IntradayBenchmark; dataQuality:DataQuality; sectorRankings:{turnover:SectorMetric[];rising:SectorMetric[];falling:SectorMetric[];turnoverL2?:SectorMetric[];risingL2?:SectorMetric[];fallingL2?:SectorMetric[];turnoverL3?:SectorMetric[];risingL3?:SectorMetric[];fallingL3?:SectorMetric[];limitUp:SectorMetric[]} }

const props=defineProps<{review:DailyReview}>();
const empty=():MarketDetails=>({indices:[],streakLadder:[],warnings:[],marketMedian:{},intradayBenchmark:{},dataQuality:{},sectorRankings:{turnover:[],rising:[],falling:[],limitUp:[]}});
const details=computed<MarketDetails>(()=>{if(!props.review.rawMetrics)return empty();try{const value=JSON.parse(props.review.rawMetrics);return {indices:Array.isArray(value.indices)?value.indices:[],streakLadder:Array.isArray(value.streakLadder)?value.streakLadder:[],warnings:Array.isArray(value.warnings)?value.warnings:[],marketMedian:value.marketMedian||{},intradayBenchmark:value.intradayBenchmark||{},dataQuality:value.dataQuality||{},sectorRankings:{turnover:value.sectorRankings?.turnover||[],rising:value.sectorRankings?.rising||[],falling:value.sectorRankings?.falling||[],turnoverL2:value.sectorRankings?.turnoverL2||[],risingL2:value.sectorRankings?.risingL2||[],fallingL2:value.sectorRankings?.fallingL2||[],turnoverL3:value.sectorRankings?.turnoverL3||[],risingL3:value.sectorRankings?.risingL3||[],fallingL3:value.sectorRankings?.fallingL3||[],limitUp:value.sectorRankings?.limitUp||[]}};}catch{return empty();}});
const dimensionRows=computed<DimensionMetric[]>(()=>{if(!props.review.dimensionScores)return [];try{return Object.values(JSON.parse(props.review.dimensionScores)) as DimensionMetric[];}catch{return [];}});
const breadthRatio=computed(()=>props.review.fallingCount?`${(Number(props.review.risingCount||0)/props.review.fallingCount).toFixed(2)} : 1`:'-');
const sectorLevel=ref(1);
const primaryIndustryNames=new Set(["????","????","??","????","??","??","????","????","????","????","????","????","????","???","????","????","??","????","????","????","????","???","??","??","??","????","????","??","????","??","????"]);
const secondaryIndustryNames=new Set(["???","??","?????","????","???","???","?????","????","??","??","????","????","???","????","????","????","??","????","????","????","??","??","??","?????","????","????","????","??","??","????","????","????","????","?????","????"]);
const legacyRows=(rows:SectorMetric[])=>rows.filter(item=>sectorLevel.value===1?(item.level===1||primaryIndustryNames.has(item.name)):sectorLevel.value===2?(item.level===2||secondaryIndustryNames.has(item.name)):item.level===3||(!primaryIndustryNames.has(item.name)&&!secondaryIndustryNames.has(item.name)));
const rowsFor=(key:'turnover'|'rising'|'falling')=>{if(sectorLevel.value===2){const rows=key==='turnover'?details.value.sectorRankings.turnoverL2:key==='rising'?details.value.sectorRankings.risingL2:details.value.sectorRankings.fallingL2;return rows?.length?rows:legacyRows(details.value.sectorRankings[key]);}if(sectorLevel.value===3){const rows=key==='turnover'?details.value.sectorRankings.turnoverL3:key==='rising'?details.value.sectorRankings.risingL3:details.value.sectorRankings.fallingL3;return rows?.length?rows:legacyRows(details.value.sectorRankings[key]);}return legacyRows(details.value.sectorRankings[key]);};
const rankingGroups=computed(()=>[
  {key:'turnover',title:sectorLevel.value+'??????',note:'???????? ? ?????',rows:rowsFor('turnover')},
  {key:'rising',title:sectorLevel.value+'??????',note:'???????? ? ???',rows:rowsFor('rising')},
  {key:'falling',title:sectorLevel.value+'??????',note:'???????? ? ???',rows:rowsFor('falling')},
  {key:'limitUp',title:'???????',note:'????????? ? ?????',rows:details.value.sectorRankings.limitUp}
]);
const hasRankings=computed(()=>rankingGroups.value.some(group=>group.rows.length));
const collectedAt=computed(()=>{const value=details.value.dataQuality.collectedAt||props.review.collectedAt;return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'-';});
const percent=(value?:number)=>value==null?'-':`${Number(value).toFixed(2)}%`;
const amount=(value?:number)=>{const number=Number(value||0);return number>=1e8?`${(number/1e8).toFixed(2)} 亿`:number>=1e4?`${(number/1e4).toFixed(2)} 万`:number?number.toFixed(2):'-';};
const tone=(value?:number)=>Number(value||0)>0?'up':Number(value||0)<0?'down':'';
</script>

<style scoped>
.metric-note{display:block;margin-top:3px;color:#94a3b8;font-size:11px}.market-details{display:flex;flex-direction:column;gap:14px;margin-bottom:14px}.section-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-title span{font-size:12px;color:#94a3b8}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.metric-grid>div,.ladder-grid>div,.dimension-grid>div{padding:12px;border-radius:12px;background:#f8fafc}.metric-grid small,.metric-grid strong{display:block}.metric-grid small{color:#64748b}.metric-grid strong{margin-top:6px;font-size:18px}.ladder-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.ladder-grid small,.ladder-grid strong,.dimension-grid span,.dimension-grid strong,.dimension-grid small{display:block}.ladder-grid strong{margin-top:6px;font-size:20px}.dimension-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:10px}.dimension-grid span,.dimension-grid small{color:#64748b}.dimension-grid strong{margin:6px 0;font-size:20px}.ranking-section{display:flex;flex-direction:column;gap:10px}.level-switch{align-self:flex-start}.ranking-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.ranking-row{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #eef2f7}.ranking-row:last-child{border-bottom:0}.rank{width:24px;height:24px;display:grid;place-items:center;border-radius:8px;background:#eff6ff;color:#2563eb;font-size:12px}.sector-name b,.sector-name small,.sector-value b,.sector-value small{display:block}.sector-name small,.sector-value small{margin-top:3px;color:#94a3b8;font-size:11px}.sector-value{text-align:right}.up{color:#ef4444}.down{color:#16a34a}@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,1fr)}.dimension-grid{grid-template-columns:repeat(2,1fr)}.ranking-grid{grid-template-columns:1fr}}@media(max-width:600px){.metric-grid{grid-template-columns:repeat(2,1fr)}.section-title{align-items:flex-start;flex-direction:column}.market-details :deep(.el-table){min-width:620px}.market-details :deep(.el-card__body){overflow-x:auto}}
</style>
