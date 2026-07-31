<template>
  <div v-if="analytics" class="analytics">
    <div class="indicator-grid">
      <el-card><small>首板晋级二板</small><strong>{{ percent(analytics.advancement.firstToSecondRate) }}</strong><span>{{ analytics.advancement.firstToSecond }} / {{ analytics.advancement.previousFirstBoards }} 家</span></el-card>
      <el-card><small>二板晋级三板</small><strong>{{ percent(analytics.advancement.secondToThirdRate) }}</strong><span>{{ analytics.advancement.secondToThird }} / {{ analytics.advancement.previousSecondBoards }} 家</span></el-card>
      <el-card><small>计划完成率</small><strong>{{ percent(analytics.execution.planCompletionRate) }}</strong><span>{{ analytics.execution.completedPlans }} / {{ analytics.execution.duePlans }} 个到期计划</span></el-card>
      <el-card><small>计划内交易占比</small><strong>{{ percent(analytics.execution.plannedTradeRate) }}</strong><span>{{ analytics.execution.plannedTrades }} / {{ analytics.execution.trades }} 笔交易</span></el-card>
    </div>
    <el-alert v-if="analytics.advancement.status!=='AVAILABLE'" :title="analytics.advancement.status" description="系统会从本次版本开始积累每日涨停股票明细，至少两个收盘交易日后自动计算晋级率。" type="info" :closable="false"/>
    <div class="chart-grid">
      <el-card><template #header><div class="head"><b>近 5 日市场趋势</b><span>收盘评分、涨跌停与炸板率</span></div></template><BaseChart :option="trendOption"/></el-card>
      <el-card><template #header><div class="head"><b>盘中时间线</b><span>10:00 → 11:00 → 14:00 → 收盘</span></div></template><BaseChart v-if="analytics.intradayTimeline.length" :option="timelineOption"/><el-empty v-else description="暂无该日盘中快照；后续定时采集会自动积累"/></el-card>
    </div>
  </div>
</template>
<script setup lang="ts">
import type {EChartsOption} from 'echarts';import {computed,onMounted,ref,watch} from 'vue';import BaseChart from '@/components/BaseChart.vue';import {getReviewAnalytics,type ReviewAnalytics} from '@/api/tradingReview';
const props=defineProps<{tradeDate:string;refreshKey?:number}>();const analytics=ref<ReviewAnalytics>();
async function load(){if(props.tradeDate)analytics.value=(await getReviewAnalytics(props.tradeDate)).data;}watch(()=>[props.tradeDate,props.refreshKey],load);onMounted(load);
const trendOption=computed<EChartsOption>(()=>{const rows=analytics.value?.fiveDayTrend||[];return{tooltip:{trigger:'axis'},legend:{data:['情绪评分','涨停数','跌停数','炸板率']},xAxis:{type:'category',data:rows.map(v=>v.tradeDate.slice(5))},yAxis:[{type:'value',name:'评分/家数'},{type:'value',name:'炸板率%',max:100}],series:[{name:'情绪评分',type:'line',smooth:true,data:rows.map(v=>v.sentimentScore)},{name:'涨停数',type:'line',data:rows.map(v=>v.limitUpCount)},{name:'跌停数',type:'line',data:rows.map(v=>v.limitDownCount)},{name:'炸板率',type:'line',yAxisIndex:1,data:rows.map(v=>v.brokenBoardRate)}]};});
const timelineOption=computed<EChartsOption>(()=>{const rows=analytics.value?.intradayTimeline||[];return{tooltip:{trigger:'axis'},legend:{data:['情绪评分','上涨家数','下跌家数']},xAxis:{type:'category',data:rows.map(v=>new Date(v.quoteTime).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}))},yAxis:[{type:'value',name:'评分'},{type:'value',name:'家数'}],series:[{name:'情绪评分',type:'line',smooth:true,data:rows.map(v=>v.sentimentScore)},{name:'上涨家数',type:'line',yAxisIndex:1,data:rows.map(v=>v.risingCount)},{name:'下跌家数',type:'line',yAxisIndex:1,data:rows.map(v=>v.fallingCount)}]};});
const percent=(v?:number)=>v==null?'-':Number(v).toFixed(2)+'%';
</script>
<style scoped>.analytics{display:flex;flex-direction:column;gap:14px;margin-bottom:14px}.indicator-grid,.chart-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.indicator-grid small,.indicator-grid strong,.indicator-grid span{display:block}.indicator-grid strong{font-size:24px;margin:7px 0}.indicator-grid small,.indicator-grid span,.head span{color:#64748b}.chart-grid{grid-template-columns:repeat(2,1fr)}.head{display:flex;justify-content:space-between;gap:12px}@media(max-width:900px){.indicator-grid{grid-template-columns:repeat(2,1fr)}.chart-grid{grid-template-columns:1fr}}@media(max-width:600px){.indicator-grid{grid-template-columns:1fr 1fr}.head{flex-direction:column}}</style>
