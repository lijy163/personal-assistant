<template>
  <div class="finance-page">
    <el-tabs v-model="tab" @tab-change="loadTab">
      <el-tab-pane label="月度分析" name="overview">
        <div class="toolbar month-toolbar">
          <div>
            <h2>{{ monthLabel }}收支概览</h2>
            <span>按标准交易自动汇总，手动记录会即时计入</span>
          </div>
          <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" @change="loadAnalysis" />
        </div>
        <div class="summary">
          <el-card><small>本月收入</small><b class="income">{{ money(analysis?.income) }}</b></el-card>
          <el-card><small>本月支出</small><b class="expense">{{ money(analysis?.expense) }}</b></el-card>
          <el-card><small>本月结余</small><b :class="{ expense:(analysis?.balance||0)<0 }">{{ money(analysis?.balance) }}</b></el-card>
          <el-card><small>支出笔数 / 日均</small><b>{{ analysis?.expenseCount||0 }} 笔</b><span>{{ money(analysis?.averageDailyExpense) }} / 天</span></el-card>
        </div>
        <el-row :gutter="16">
          <el-col :xs="24" :lg="11">
            <el-card class="chart-card">
              <template #header><div class="card-title"><span>支出分类</span><el-tag v-if="analysis?.topExpenseCategory" type="warning">最高：{{ analysis.topExpenseCategory }}</el-tag></div></template>
              <BaseChart :option="categoryOption" />
            </el-card>
          </el-col>
          <el-col :xs="24" :lg="13">
            <el-card class="chart-card">
              <template #header><span>近 6 个月收支趋势</span></template>
              <BaseChart :option="trendOption" />
            </el-card>
          </el-col>
        </el-row>
        <el-card class="category-table">
          <template #header><span>分类明细</span></template>
          <el-table :data="analysis?.categories||[]" empty-text="本月暂无支出记录">
            <el-table-column prop="categoryName" label="分类" />
            <el-table-column label="金额"><template #default="{row}">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column prop="count" label="笔数" width="100" />
            <el-table-column label="占比" width="220"><template #default="{row}"><el-progress :percentage="Number(row.percentage)" :stroke-width="10" /></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="交易明细" name="transactions">
        <el-card>
          <template #header>
            <div class="toolbar">
              <span>标准交易</span>
              <div class="filters">
                <el-date-picker v-model="filters.month" type="month" value-format="YYYY-MM" clearable placeholder="全部月份" />
                <el-input v-model="filters.keyword" clearable placeholder="商户/摘要" @keyup.enter="loadTransactions" />
                <el-select v-model="filters.direction" clearable placeholder="收支"><el-option label="收入" value="INCOME" /><el-option label="支出" value="EXPENSE" /></el-select>
                <el-button @click="loadTransactions">查询</el-button>
                <el-button type="primary" @click="openTransaction()">手动记账</el-button>
              </div>
            </div>
          </template>
          <el-table v-loading="transactionLoading" :data="transactions" empty-text="暂无交易，点击“手动记账”添加第一笔">
            <el-table-column label="时间" width="170"><template #default="{row}">{{ time(row.transactionTime) }}</template></el-table-column>
            <el-table-column prop="merchant" label="商户" min-width="120" />
            <el-table-column prop="description" label="摘要" min-width="160" show-overflow-tooltip />
            <el-table-column label="方向" width="80"><template #default="{row}"><el-tag :type="row.direction==='INCOME'?'success':'warning'">{{ row.direction==='INCOME'?'收入':'支出' }}</el-tag></template></el-table-column>
            <el-table-column label="金额" width="120"><template #default="{row}"><strong :class="row.direction==='INCOME'?'income':'expense'">{{ money(row.amount) }}</strong></template></el-table-column>
            <el-table-column label="分类" width="160"><template #default="{row}"><el-select :model-value="row.categoryId" placeholder="待确认" @change="onCategoryChange(row.id,$event)"><el-option v-for="item in directionCategories(row.direction)" :key="item.id" :label="item.categoryName" :value="item.id" /></el-select></template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openTransaction(row)">编辑</el-button><el-button link type="danger" @click="removeTransaction(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="账单导入" name="imports">
        <el-card>
          <template #header>上传官方账单</template>
          <el-form inline>
            <el-form-item label="账户"><el-select v-model="upload.accountId" style="width:180px"><el-option v-for="item in accounts" :key="item.id" :label="item.accountName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="平台"><el-select v-model="upload.platform" style="width:130px"><el-option label="支付宝" value="ALIPAY" /><el-option label="微信" value="WECHAT" /><el-option label="银行卡" value="BANK" /></el-select></el-form-item>
            <el-form-item><el-upload :auto-upload="false" :show-file-list="false" accept=".csv,.xls,.xlsx" :on-change="selectFile"><el-button>选择 CSV/XLSX</el-button></el-upload></el-form-item>
            <el-button type="primary" :disabled="!upload.file||!upload.accountId" :loading="uploading" @click="preview">解析预览</el-button>
          </el-form>
          <el-alert title="仅导入官方导出的账单文件；不会读取支付密码、短信验证码或自动登录金融账户。" type="info" :closable="false" />
        </el-card>
        <el-card v-if="previewData">
          <template #header><div class="toolbar"><span>预览：共 {{ previewData.totalCount }}，重复 {{ previewData.duplicateCount }}，无效 {{ previewData.invalidCount }}</span><el-button type="primary" :disabled="previewData.invalidCount>0" @click="confirm">确认导入</el-button></div></template>
          <el-table :data="previewData.rows" max-height="480"><el-table-column prop="rowNumber" label="#" width="60" /><el-table-column prop="transactionTime" label="时间" width="180" /><el-table-column prop="merchant" label="商户" /><el-table-column prop="direction" label="收支" width="90" /><el-table-column prop="amount" label="金额" width="100" /><el-table-column label="状态" width="140"><template #default="{row}"><el-tag v-if="row.validationError" type="danger">{{ row.validationError }}</el-tag><el-tag v-else-if="row.duplicateFlag" type="warning">重复</el-tag><el-tag v-else type="success">可导入</el-tag></template></el-table-column></el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="账户与规则" name="settings">
        <el-row :gutter="16">
          <el-col :xs="24" :lg="12"><el-card><template #header><div class="toolbar"><span>资金账户</span><el-button type="primary" @click="accountVisible=true">新增</el-button></div></template><el-table :data="accounts"><el-table-column prop="accountName" label="名称" /><el-table-column prop="accountType" label="类型" /><el-table-column prop="institution" label="机构" /></el-table></el-card></el-col>
          <el-col :xs="24" :lg="12"><el-card><template #header><div class="toolbar"><span>分类规则</span><el-button type="primary" @click="ruleVisible=true">新增</el-button></div></template><el-table :data="rules"><el-table-column prop="ruleName" label="规则" /><el-table-column prop="keyword" label="关键词" /><el-table-column label="分类"><template #default="{row}">{{ categoryName(row.categoryId) }}</template></el-table-column></el-table></el-card></el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="transactionVisible" :title="editingId?'编辑交易':'手动记账'" width="min(680px,94vw)" destroy-on-close>
      <el-form ref="transactionFormRef" :model="transactionForm" :rules="transactionRules" label-width="84px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="收支" prop="direction"><el-segmented v-model="transactionForm.direction" :options="directionOptions" block @change="transactionForm.categoryId=undefined" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额" prop="amount"><el-input-number v-model="transactionForm.amount" :min="0.01" :precision="2" :step="10" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="账户" prop="accountId"><el-select v-model="transactionForm.accountId" style="width:100%"><el-option v-for="item in accounts.filter(x=>x.enabled)" :key="item.id" :label="item.accountName" :value="item.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类"><el-select v-model="transactionForm.categoryId" clearable placeholder="可稍后分类" style="width:100%"><el-option v-for="item in directionCategories(transactionForm.direction)" :key="item.id" :label="item.categoryName" :value="item.id" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="交易时间" prop="transactionTime"><el-date-picker v-model="transactionForm.transactionTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商户"><el-input v-model="transactionForm.merchant" maxlength="255" placeholder="例如：美团、工资" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="交易类型"><el-select v-model="transactionForm.transactionType" style="width:100%"><el-option label="普通收支" :value="transactionForm.direction" /><el-option label="转账" value="TRANSFER" /><el-option label="退款" value="REFUND" /><el-option label="还款" value="REPAYMENT" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="摘要"><el-input v-model="transactionForm.description" maxlength="2000" placeholder="这笔钱花在了哪里" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="transactionForm.note" type="textarea" :rows="2" maxlength="2000" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="transactionVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveTransaction">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="accountVisible" title="新增资金账户" width="480px"><el-form :model="accountForm" label-width="90px"><el-form-item label="名称"><el-input v-model="accountForm.accountName" /></el-form-item><el-form-item label="类型"><el-select v-model="accountForm.accountType"><el-option label="支付宝" value="ALIPAY" /><el-option label="微信" value="WECHAT" /><el-option label="银行卡" value="BANK" /><el-option label="现金" value="CASH" /></el-select></el-form-item><el-form-item label="机构"><el-input v-model="accountForm.institution" /></el-form-item></el-form><template #footer><el-button @click="accountVisible=false">取消</el-button><el-button type="primary" @click="saveAccount">保存</el-button></template></el-dialog>
    <el-dialog v-model="ruleVisible" title="新增分类规则" width="480px"><el-form :model="ruleForm" label-width="90px"><el-form-item label="名称"><el-input v-model="ruleForm.ruleName" /></el-form-item><el-form-item label="关键词"><el-input v-model="ruleForm.keyword" /></el-form-item><el-form-item label="分类"><el-select v-model="ruleForm.categoryId"><el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="ruleVisible=false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { EChartsOption } from 'echarts';
import type { FormInstance, FormRules, UploadFile } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import BaseChart from '@/components/BaseChart.vue';
import { categorizeFinanceTransaction, confirmFinanceImport, listFinanceAccounts, listFinanceCategories, listFinanceRules, listFinanceTransactions, previewFinanceImport, saveFinanceAccount, saveFinanceRule, type FinanceAccount, type FinanceCategory, type FinanceRule, type FinanceTransaction, type ImportPreview } from '@/api/finance';
import { createManualTransaction, deleteManualTransaction, getFinanceMonthlyAnalysis, updateManualTransaction, type FinanceTransactionPayload, type MonthlyAnalysis } from '@/api/financeEnhancement';

const tab=ref('overview'),month=ref(new Date().toISOString().slice(0,7)),analysis=ref<MonthlyAnalysis>();
const accounts=ref<FinanceAccount[]>([]),categories=ref<FinanceCategory[]>([]),rules=ref<FinanceRule[]>([]),transactions=ref<FinanceTransaction[]>([]);
const previewData=ref<ImportPreview>(),uploading=ref(false),transactionLoading=ref(false),saving=ref(false),accountVisible=ref(false),ruleVisible=ref(false),transactionVisible=ref(false),editingId=ref<number>(),transactionFormRef=ref<FormInstance>();
const filters=reactive({keyword:'',direction:'',month:new Date().toISOString().slice(0,7)}),upload=reactive<{accountId?:number;platform:string;file?:File}>({platform:'ALIPAY'});
const accountForm=reactive({accountName:'',accountType:'ALIPAY',institution:'',currency:'CNY',enabled:true}),ruleForm=reactive<{ruleName:string;keyword:string;categoryId?:number;priority:number;enabled:boolean}>({ruleName:'',keyword:'',priority:0,enabled:true});
const blankTransaction=():FinanceTransactionPayload=>({accountId:accounts.value[0]?.id||0,transactionTime:localNow(),direction:'EXPENSE',amount:0,transactionType:'EXPENSE',merchant:'',description:'',note:''});
const transactionForm=reactive<FinanceTransactionPayload>(blankTransaction());
const directionOptions=[{label:'支出',value:'EXPENSE'},{label:'收入',value:'INCOME'}];
const transactionRules:FormRules={accountId:[{required:true,message:'请选择账户'}],direction:[{required:true,message:'请选择收支方向'}],amount:[{required:true,message:'请输入金额'},{type:'number',min:0.01,message:'金额必须大于 0'}],transactionTime:[{required:true,message:'请选择交易时间'}]};
const monthLabel=computed(()=>{const [year,monthValue]=month.value.split('-');return `${year} 年 ${Number(monthValue)} 月`;});
const categoryOption=computed<EChartsOption>(()=>({tooltip:{trigger:'item',formatter:'{b}<br/>¥{c}（{d}%）'},legend:{type:'scroll',bottom:0},series:[{type:'pie',radius:['42%','68%'],center:['50%','44%'],label:{formatter:'{b}\n{d}%'},data:(analysis.value?.categories||[]).map(item=>({name:item.categoryName,value:item.amount}))}]}));
const trendOption=computed<EChartsOption>(()=>({tooltip:{trigger:'axis'},legend:{data:['收入','支出']},grid:{left:54,right:24,top:48,bottom:35},xAxis:{type:'category',data:(analysis.value?.trend||[]).map(item=>item.month.slice(5)+'月')},yAxis:{type:'value',axisLabel:{formatter:(value:number)=>`¥${value}`}},series:[{name:'收入',type:'bar',itemStyle:{color:'#16a34a'},data:(analysis.value?.trend||[]).map(item=>item.income)},{name:'支出',type:'bar',itemStyle:{color:'#ef4444'},data:(analysis.value?.trend||[]).map(item=>item.expense)}]}));

async function base(){[accounts.value,categories.value,rules.value]=await Promise.all([listFinanceAccounts().then(response=>response.data),listFinanceCategories().then(response=>response.data),listFinanceRules().then(response=>response.data)]);if(!upload.accountId&&accounts.value.length)upload.accountId=accounts.value[0].id;}
async function loadAnalysis(){analysis.value=(await getFinanceMonthlyAnalysis(month.value)).data;}
async function loadTransactions(){transactionLoading.value=true;try{transactions.value=(await listFinanceTransactions({keyword:filters.keyword||undefined,direction:filters.direction||undefined,month:filters.month||undefined})).data;}finally{transactionLoading.value=false;}}
async function loadTab(name:string|number){if(name==='overview')await loadAnalysis();if(name==='transactions')await loadTransactions();if(name==='settings')await base();}
function openTransaction(row?:FinanceTransaction){editingId.value=row?.id;Object.assign(transactionForm,blankTransaction(),row?{accountId:row.accountId,categoryId:row.categoryId,transactionTime:row.transactionTime.slice(0,19),direction:row.direction,amount:Number(row.amount),transactionType:row.transactionType,merchant:row.merchant||'',description:row.description||'',note:(row as FinanceTransaction&{note?:string}).note||''}:{});transactionVisible.value=true;}
async function saveTransaction(){if(!(await transactionFormRef.value?.validate()))return;saving.value=true;try{editingId.value?await updateManualTransaction(editingId.value,transactionForm):await createManualTransaction(transactionForm);ElMessage.success(editingId.value?'交易已更新':'记账成功');transactionVisible.value=false;await Promise.all([loadTransactions(),loadAnalysis()]);}finally{saving.value=false;}}
async function removeTransaction(row:FinanceTransaction){await ElMessageBox.confirm(`确认删除 ${money(row.amount)} 的交易记录？删除后不可恢复。`,'删除交易',{type:'warning'});await deleteManualTransaction(row.id);ElMessage.success('交易已删除');await Promise.all([loadTransactions(),loadAnalysis()]);}
function selectFile(file:UploadFile){upload.file=file.raw;}
async function preview(){if(!upload.accountId||!upload.file)return;uploading.value=true;try{previewData.value=(await previewFinanceImport(upload.accountId,upload.platform,upload.file)).data;}finally{uploading.value=false;}}
async function confirm(){if(!previewData.value)return;const count=(await confirmFinanceImport(previewData.value.batchId)).data;ElMessage.success(`成功导入 ${count} 条交易`);previewData.value=undefined;await Promise.all([loadTransactions(),loadAnalysis()]);}
async function onCategoryChange(id:number,value:unknown){await categorizeFinanceTransaction(id,Number(value));ElMessage.success('分类已确认');await Promise.all([loadTransactions(),loadAnalysis()]);}
async function saveAccount(){if(!accountForm.accountName.trim())return ElMessage.warning('请输入账户名称');await saveFinanceAccount(accountForm);accountVisible.value=false;await base();}
async function saveRule(){if(!ruleForm.categoryId||!ruleForm.ruleName.trim()||!ruleForm.keyword.trim())return ElMessage.warning('请完整填写规则');await saveFinanceRule({...ruleForm,categoryId:ruleForm.categoryId});ruleVisible.value=false;await base();}
function directionCategories(direction:string){return categories.value.filter(item=>item.direction===direction&&item.enabled);}
function categoryName(id:number){return categories.value.find(item=>item.id===id)?.categoryName||'-';}
function money(value?:number){return value==null?'¥0.00':`¥${Number(value).toFixed(2)}`;}
function time(value:string){return new Date(value).toLocaleString('zh-CN',{hour12:false});}
function localNow(){const now=new Date(Date.now()-new Date().getTimezoneOffset()*60000);return now.toISOString().slice(0,19);}
onMounted(async()=>{await base();await loadAnalysis();});
</script>

<style scoped>
.finance-page{display:flex;flex-direction:column;gap:16px}.toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px}.month-toolbar{margin-bottom:16px}.month-toolbar h2{font-size:20px;margin:0 0 5px}.month-toolbar span{color:#64748b;font-size:13px}.summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:16px}.summary small{display:block;color:#64748b}.summary b{display:block;font-size:25px;margin-top:10px}.summary span{color:#64748b;font-size:13px}.income{color:#16a34a}.expense{color:#dc2626}.card-title{display:flex;justify-content:space-between;align-items:center}.chart-card{height:430px}.category-table{margin-top:16px}.filters{display:flex;gap:8px;flex-wrap:wrap}.filters .el-input{width:180px}.filters .el-select{width:110px}.filters .el-date-editor{width:150px}.el-card+.el-card{margin-top:16px}@media(max-width:900px){.summary{grid-template-columns:repeat(2,1fr)}.toolbar{align-items:flex-start;flex-direction:column}.filters{width:100%}.chart-card{margin-bottom:16px}}@media(max-width:520px){.summary{grid-template-columns:1fr}.filters>*{width:100%!important}}
</style>
