<template>
  <div class="fund-flow" v-loading="loading">
    <el-alert title="资金流是供应商按成交单规模计算的行情指标，不等同于真实机构账户资金变化，也不构成投资建议。" type="warning" :closable="false" show-icon/>
    <el-card class="overview-card">
      <template #header><div class="header"><span>A 股自选股资金流</span><div><el-button @click="load">刷新页面</el-button><el-button type="primary" :loading="refreshing" @click="refresh">采集最近 20 日</el-button></div></div></template>
      <div class="stats">
        <div><small>主力净流入合计</small><b :class="changeClass(overview?.totalMainNetInflow)">{{ money(overview?.totalMainNetInflow) }}</b></div>
        <div><small>覆盖率</small><b>{{ Number(status?.coverageRate || 0).toFixed(2) }}%</b></div>
        <div><small>覆盖股票</small><b>{{ status?.coveredCount || 0 }} / {{ status?.watchCount || 0 }}</b></div>
        <div><small>流入 / 流出</small><b><span class="up">{{ overview?.inflowCount || 0 }}</span> / <span class="down">{{ overview?.outflowCount || 0 }}</span></b></div>
        <div><small>数据时间</small><b>{{ time(overview?.latestQuoteTime) }}</b></div>
        <div><small>供应商</small><b>{{ overview?.provider || '-' }}</b></div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12"><el-card><template #header>主力净流入排行</template><BaseChart :option="rankingOption"/></el-card></el-col>
      <el-col :span="12"><el-card><template #header><div class="header"><span>单股近 20 日趋势</span><el-select v-model="selectedWatchId" placeholder="选择股票" style="width:180px" @change="loadTrend"><el-option v-for="item in overview?.ranking || []" :key="item.watchItemId" :label="item.stockName" :value="item.watchItemId"/></el-select></div></template><BaseChart :option="trendOption"/></el-card></el-col>
    </el-row>
    <el-card class="structure-card"><template #header>最新资金结构</template><BaseChart :option="structureOption"/></el-card><el-card><template #header>自选股行业资金流（非全市场板块）</template><BaseChart :option="sectorOption"/></el-card>
    <el-card><template #header>资金流明细</template><el-table :data="overview?.ranking || []" @row-click="selectRow">
      <el-table-column prop="stockCode" label="代码" width="110"/><el-table-column prop="stockName" label="名称"/>
      <el-table-column label="主力净流入"><template #default="{row}"><span :class="changeClass(row.mainNetInflow)">{{ money(row.mainNetInflow) }}</span></template></el-table-column>
      <el-table-column label="主力占比"><template #default="{row}">{{ percent(row.mainNetRatio) }}</template></el-table-column>
      <el-table-column label="涨跌幅"><template #default="{row}"><span :class="changeClass(row.changePercent)">{{ percent(row.changePercent) }}</span></template></el-table-column>
      <el-table-column label="数据时间"><template #default="{row}">{{ time(row.quoteTime) }}</template></el-table-column>
    </el-table></el-card>
    <el-alert v-if="status?.recentFailed" class="failure" :title="`最近采集失败 ${status.recentFailed} 次：${status.recentFailures.map(item => item.stockName + ' ' + item.errorMessage).join('；')}`" type="error" :closable="false"/>
  </div>
</template>

<script setup lang="ts">
import type { EChartsOption } from 'echarts';
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import BaseChart from '@/components/BaseChart.vue';
import { getStockFundFlowOverview,getStockFundFlowStatus,getStockFundFlowTrend,getStockSectorFundFlow,refreshStockFundFlow,type FundFlowOverview,type FundFlowSnapshot,type FundFlowStatus,type SectorFundFlow } from '@/api/fundFlow';

const overview=ref<FundFlowOverview>();const status=ref<FundFlowStatus>();const sectors=ref<SectorFundFlow>();const trend=ref<FundFlowSnapshot[]>([]);const selectedWatchId=ref<number>();const loading=ref(false);const refreshing=ref(false);
const rankingOption=computed<EChartsOption>(()=>{const rows=[...(overview.value?.ranking||[])].slice(0,12).reverse();return{tooltip:{trigger:'axis',valueFormatter:(v)=>money(Number(v))},grid:{left:90,right:30,top:20,bottom:30},xAxis:{type:'value',axisLabel:{formatter:(v:number)=>compact(v)}},yAxis:{type:'category',data:rows.map(v=>v.stockName)},series:[{type:'bar',data:rows.map(v=>({value:v.mainNetInflow||0,itemStyle:{color:Number(v.mainNetInflow||0)>=0?'#ef4444':'#22c55e'}}))}]};});
const trendOption=computed<EChartsOption>(()=>({tooltip:{trigger:'axis'},legend:{data:['主力净流入','涨跌幅']},grid:{left:70,right:65,top:45,bottom:35},xAxis:{type:'category',data:trend.value.map(v=>v.quoteTime.slice(5,10))},yAxis:[{type:'value',axisLabel:{formatter:(v:number)=>compact(v)}},{type:'value',axisLabel:{formatter:'{value}%'}}],series:[{name:'主力净流入',type:'bar',data:trend.value.map(v=>v.mainNetInflow||0),itemStyle:{color:'#2563eb'}},{name:'涨跌幅',type:'line',yAxisIndex:1,data:trend.value.map(v=>v.changePercent),smooth:true,itemStyle:{color:'#f59e0b'}}]}));
const structureOption=computed<EChartsOption>(()=>({tooltip:{trigger:'axis'},legend:{data:['超大单','大单','中单','小单']},grid:{left:80,right:30,top:45,bottom:50},xAxis:{type:'category',data:(overview.value?.ranking||[]).slice(0,10).map(v=>v.stockName),axisLabel:{rotate:25}},yAxis:{type:'value',axisLabel:{formatter:(v:number)=>compact(v)}},series:['superLargeNetInflow','largeNetInflow','mediumNetInflow','smallNetInflow'].map((key,index)=>({name:['超大单','大单','中单','小单'][index],type:'bar',stack:'flow',data:(overview.value?.ranking||[]).slice(0,10).map(row=>row[key as keyof typeof row]||0)}))}));
const sectorOption=computed<EChartsOption>(()=>{const rows=[...(sectors.value?.sectors||[])].reverse();return{tooltip:{trigger:'axis'},grid:{left:90,right:30,top:20,bottom:30},xAxis:{type:'value',axisLabel:{formatter:(v:number)=>compact(v)}},yAxis:{type:'category',data:rows.map(v=>v.industry)},series:[{type:'bar',data:rows.map(v=>({value:v.mainNetInflow,itemStyle:{color:v.mainNetInflow>=0?'#ef4444':'#22c55e'}}))}]};});
async function load(){loading.value=true;try{[overview.value,status.value,sectors.value]=await Promise.all([getStockFundFlowOverview().then(r=>r.data),getStockFundFlowStatus().then(r=>r.data),getStockSectorFundFlow().then(r=>r.data)]);if(!selectedWatchId.value&&overview.value.ranking.length)selectedWatchId.value=overview.value.ranking[0].watchItemId;if(selectedWatchId.value)await loadTrend();}finally{loading.value=false;}}
async function loadTrend(){if(selectedWatchId.value)trend.value=(await getStockFundFlowTrend(selectedWatchId.value)).data;}
async function refresh(){refreshing.value=true;try{const result=(await refreshStockFundFlow()).data;ElMessage.success(`资金流刷新完成：成功 ${result.success}，失败 ${result.failed}`);await load();}finally{refreshing.value=false;}}
async function selectRow(row:{watchItemId:number}){selectedWatchId.value=row.watchItemId;await loadTrend();}
const money=(v?:number|null)=>v==null?'-':`${v>0?'+':''}${compact(v)}`;const compact=(v:number)=>Math.abs(v)>=1e8?`${(v/1e8).toFixed(2)}亿`:Math.abs(v)>=1e4?`${(v/1e4).toFixed(2)}万`:Number(v).toFixed(2);const percent=(v?:number|null)=>v==null?'-':`${v>0?'+':''}${Number(v).toFixed(2)}%`;const time=(v?:string|null)=>v?new Date(v).toLocaleString('zh-CN',{hour12:false}):'-';const changeClass=(v?:number|null)=>Number(v||0)>0?'up':Number(v||0)<0?'down':'';
onMounted(load);
</script>

<style scoped>
.fund-flow{display:flex;flex-direction:column;gap:16px}.header{display:flex;justify-content:space-between;align-items:center}.overview-card{margin-top:0}.stats{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}.stats>div{padding:14px;background:#f7f8fa;border-radius:8px}.stats small{display:block;color:#64748b;margin-bottom:8px}.stats b{font-size:17px}.up{color:#dc2626}.down{color:#16a34a}.structure-card{margin-top:0}.failure{margin-top:0}
</style>
