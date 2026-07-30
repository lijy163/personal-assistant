import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '@/layouts/ResponsiveAppLayout.vue';
import BlogLayout from '@/layouts/BlogLayout.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/blog', component: BlogLayout, children: [
      { path: '', name: 'blogHome', component: () => import('@/views/blog/BlogHomeView.vue') },
      { path: 'posts/:slug', name: 'blogPost', component: () => import('@/views/blog/BlogPostView.vue') },
    ] },
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue') },
    {
      path: '/', component: AppLayout, redirect: '/dashboard', meta: { requiresAuth: true },
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DailyDashboardView.vue'), meta: { title: '仪表盘' } },
        { path: 'life', name: 'life', component: () => import('@/views/task/TaskListView.vue'), meta: { title: '生活事项', taskType: 'LIFE' } },
        { path: 'work', name: 'work', component: () => import('@/views/task/TaskListView.vue'), meta: { title: '工作事项', taskType: 'WORK' } },
        { path: 'learning/plans', name: 'learningPlans', component: () => import('@/views/learning/LearningPlanView.vue'), meta: { title: '学习计划' } },
        { path: 'learning/records', name: 'learningRecords', component: () => import('@/views/learning/LearningRecordView.vue'), meta: { title: '学习记录' } },
        { path: 'learning/summaries', name: 'learningSummaries', component: () => import('@/views/learning/LearningSummaryView.vue'), meta: { title: '学习总结' } },
        { path: 'learning/growth', name: 'learningGrowth', component: () => import('@/views/learning/GrowthDashboardView.vue'), meta: { title: '成长看板' } },
        { path: 'devlogs', name: 'devlogs', component: () => import('@/views/devlog/DevLogView.vue'), meta: { title: '开发记录' } },
        { path: 'devlog-tokens', name: 'devlogTokens', component: () => import('@/views/devlog/PatManagementView.vue'), meta: { title: '推送令牌' } },
        { path: 'finance', name: 'finance', component: () => import('@/views/finance/FinanceView.vue'), meta: { title: '个人账单' } },
        { path: 'inbox', name: 'inbox', component: () => import('@/views/inbox/InboxView.vue'), meta: { title: '统一收件箱' } },
        { path: 'reports', name: 'reports', component: () => import('@/views/report/ReportView.vue'), meta: { title: '自动报告' } },
        { path: 'stocks', name: 'stocks', component: () => import('@/views/stock/StockView.vue'), meta: { title: '股票关注' } },
        { path: 'trading-reviews', name: 'tradingReviews', component: () => import('@/views/trading/TradingReviewsView.vue'), meta: { title: '交易复盘' } },
        { path: 'gold', name: 'gold', component: () => import('@/views/gold/GoldView.vue'), meta: { title: '金价关注' } },
        { path: 'reminders', name: 'reminders', component: () => import('@/views/reminder/ReminderCenterView.vue'), meta: { title: '提醒中心' } },
        { path: 'scheduler', name: 'scheduler', component: () => import('@/views/scheduler/SchedulerView.vue'), meta: { title: '调度管理' } },
        { path: 'blog/manage', name: 'blogManage', component: () => import('@/views/blog/BlogManageView.vue'), meta: { title: '博客管理' } },
        { path: 'blog/manage/new', name: 'blogCreate', component: () => import('@/views/blog/BlogEditorView.vue'), meta: { title: '写新文章' } },
        { path: 'blog/manage/:id', name: 'blogEdit', component: () => import('@/views/blog/BlogEditorView.vue'), meta: { title: '编辑文章' } },
        { path: 'system', name: 'system', component: () => import('@/views/system/SystemSettingsView.vue'), meta: { title: '系统设置' } },
        { path: 'operations', name: 'operations', component: () => import('@/views/operations/OperationsView.vue'), meta: { title: '部署运维' } },
      ],
    },
  ],
});
router.beforeEach(to => { const token=localStorage.getItem('token'); if(to.meta.requiresAuth&&!token)return{name:'login'}; if(to.name==='login'&&token)return{name:'dashboard'}; });
export default router;
