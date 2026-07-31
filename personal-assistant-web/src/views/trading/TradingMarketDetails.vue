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

    <div v-if="hasRankings" class="ranking-grid">
      <el-card v-for="group in rankingGroups" :key="group.key" class="ranking-card">
        <template #header><div class="section-title"><b>{{ group.title }}</b><span>{{ group.note }}</span></div></template>
        <div v-if="group.rows.length" class="ranking-list">
          <div v-for="(item,index) in group.rows" :key="item.code || item.name" class="ranking-row">
            <span class="rank">{{ index + 1 }}</span>
            <div class="sector-name"><b>{{ item.name }}</b><small v-if="item.leader">领涨 {{ item.leader }}</small></div>
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
    <el-alert v-else title="该记录来自旧版采集，点击“重试自动获取”后可生成板块成交额、上涨、下跌和涨停榜。" type="info" :closable="false"/>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { DailyReview } from '@/api/tradingReview';

interface IndexMetric { name:string; change:number; turnover:number; rising:number; falling:number; flat:number }
interface SectorMetric { code?:string; name:string; change?:number; turnover?:number; rising?:number; falling?:number; flat?:number; leader?:string; leaderChange?:number; limitUpCount?:number }
interface StreakMetric { level:number; label:string; count:number }
interface DimensionMetric { label:string; score:number; weight:number; reason:string }
interface MarketDetails { indices:IndexMetric[]; streakLadder:StreakMetric[]; warnings:string[]; sectorRankings:{turnover:SectorMetric[];rising:SectorMetric[];falling:SectorMetric[];limitUp:SectorMetric[]} }

const props=defineProps<{review:DailyReview}>();
const empty=():MarketDetails=>({indices:[],streakLadder:[],warnings:[],sectorRankings:{turnover:[],rising:[],falling:[],limitUp:[]}});
const details=computed<MarketDetails>(()=>{if(!props.review.rawMetrics)return empty();try{const value=JSON.parse(props.review.rawMetrics);return {indices:Array.isArray(value.indices)?value.indices:[],streakLadder:Array.isArray(value.streakLadder)?value.streakLadder:[],warnings:Array.isArray(value.warnings)?value.warnings:[],sectorRankings:{turnover:value.sectorRankings?.turnover||[],rising:value.sectorRankings?.rising||[],falling:value.sectorRankings?.falling||[],limitUp:value.sectorRankings?.limitUp||[]}};}catch{return empty();}});
const dimensionRows=computed<DimensionMetric[]>(()=>{if(!props.review.dimensionScores)return [];try{return Object.values(JSON.parse(props.review.dimensionScores)) as DimensionMetric[];}catch{return [];}});
const breadthRatio=computed(()=>props.review.fallingCount?`${(Number(props.review.risingCount||0)/props.review.fallingCount).toFixed(2)} : 1`:'-');
const rankingGroups=computed(()=>[
  {key:'turnover',title:'板块成交额',note:'资金活跃度',rows:details.value.sectorRankings.turnover},
  {key:'rising',title:'板块上涨榜',note:'按板块涨幅',rows:details.value.sectorRankings.rising},
  {key:'falling',title:'板块下跌榜',note:'按板块跌幅',rows:details.value.sectorRankings.falling},
  {key:'limitUp',title:'板块涨停榜',note:'按涨停家数',rows:details.value.sectorRankings.limitUp}
]);
const hasRankings=computed(()=>rankingGroups.value.some(group=>group.rows.length));
const percent=(value?:number)=>value==null?'-':`${Number(value).toFixed(2)}%`;
const amount=(value?:number)=>{const number=Number(value||0);return number>=1e8?`${(number/1e8).toFixed(2)} 亿`:number>=1e4?`${(number/1e4).toFixed(2)} 万`:number?number.toFixed(2):'-';};
const tone=(value?:number)=>Number(value||0)>0?'up':Number(value||0)<0?'down':'';
</script>

<style scoped>
.market-details{display:flex;flex-direction:column;gap:14px;margin-bottom:14px}.section-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-title span{font-size:12px;color:#94a3b8}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.metric-grid>div,.ladder-grid>div,.dimension-grid>div{padding:12px;border-radius:12px;background:#f8fafc}.metric-grid small,.metric-grid strong{display:block}.metric-grid small{color:#64748b}.metric-grid strong{margin-top:6px;font-size:18px}.ladder-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.ladder-grid small,.ladder-grid strong,.dimension-grid span,.dimension-grid strong,.dimension-grid small{display:block}.ladder-grid strong{margin-top:6px;font-size:20px}.dimension-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:10px}.dimension-grid span,.dimension-grid small{color:#64748b}.dimension-grid strong{margin:6px 0;font-size:20px}.ranking-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.ranking-row{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #eef2f7}.ranking-row:last-child{border-bottom:0}.rank{width:24px;height:24px;display:grid;place-items:center;border-radius:8px;background:#eff6ff;color:#2563eb;font-size:12px}.sector-name b,.sector-name small,.sector-value b,.sector-value small{display:block}.sector-name small,.sector-value small{margin-top:3px;color:#94a3b8;font-size:11px}.sector-value{text-align:right}.up{color:#ef4444}.down{color:#16a34a}@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,1fr)}.dimension-grid{grid-template-columns:repeat(2,1fr)}.ranking-grid{grid-template-columns:1fr}}@media(max-width:600px){.metric-grid{grid-template-columns:repeat(2,1fr)}.section-title{align-items:flex-start;flex-direction:column}.market-details :deep(.el-table){min-width:620px}.market-details :deep(.el-card__body){overflow-x:auto}}
</style>
