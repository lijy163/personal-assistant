import http from './http';
export interface SearchResult{type:string;id:number;title:string;snippet:string;occurredAt:string;route:string;}
export interface InboxItem{id:number;content:string;suggestedType:string;confirmedType?:string;confidence:number;reason:string;status:string;createdAt:string;}
export interface DailyDashboard{todayTaskCount:number;overdueTaskCount:number;pendingReminderCount:number;pendingInboxCount:number;unconfirmedTransactionCount:number;recentDevLogCount:number;activeLearningCount:number;fundFlowAlertCount:number;monthIncome:number;monthExpense:number;generatedAt:string;}
export interface GeneratedReport{id:number;reportType:string;periodStart:string;periodEnd:string;title:string;markdownContent:string;createdAt:string;}
export const globalSearch=(keyword:string)=>http.get<unknown,{data:SearchResult[]}>('/search',{params:{keyword}});
export const listInbox=(status?:string)=>http.get<unknown,{data:InboxItem[]}>('/inbox',{params:{status}});
export const createInbox=(content:string)=>http.post<unknown,{data:InboxItem}>('/inbox',{content});
export const confirmInbox=(id:number,confirmedType:string)=>http.patch(`/inbox/${id}/confirm`,{confirmedType});
export const archiveInbox=(id:number)=>http.patch(`/inbox/${id}/archive`);
export const getDailyDashboard=()=>http.get<unknown,{data:DailyDashboard}>('/dashboard/daily');
export const listReports=()=>http.get<unknown,{data:GeneratedReport[]}>('/reports');
export const generateReport=(type:string)=>http.post<unknown,{data:GeneratedReport}>('/reports/generate',null,{params:{type}});
