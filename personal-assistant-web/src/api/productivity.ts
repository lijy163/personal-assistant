import http from './http';
export interface SearchResult{type:string;id:number;title:string;snippet:string;occurredAt:string;route:string;}
export interface InboxAttachment{id:number;originalName:string;contentType:string;fileSize:number;fileKind:string;createdAt:string;}
export interface InboxItem{id:number;content:string;suggestedType:string;confirmedType?:string;confidence:number;reason:string;status:string;inputType:string;source:string;tags?:string;remark?:string;recordedAt?:string;createdAt:string;attachments:InboxAttachment[];}
export interface DailyInspiration{content:string;translation:string;imageUrl:string;date:string;source:string;}
export interface DailyDashboard{todayTaskCount:number;overdueTaskCount:number;pendingReminderCount:number;pendingInboxCount:number;unconfirmedTransactionCount:number;recentDevLogCount:number;activeLearningCount:number;fundFlowAlertCount:number;monthIncome:number;monthExpense:number;generatedAt:string;}
export interface GeneratedReport{id:number;reportType:string;periodStart:string;periodEnd:string;title:string;markdownContent:string;createdAt:string;}
export const globalSearch=(keyword:string)=>http.get<unknown,{data:SearchResult[]}>('/search',{params:{keyword}});
export const listInbox=(status?:string)=>http.get<unknown,{data:InboxItem[]}>('/inbox',{params:{status}});
export const createInbox=(content:string)=>http.post<unknown,{data:InboxItem}>('/inbox',{content});
export const collectInbox=(payload:{content:string;tags:string;remark:string;source:string;recordedAt?:string},files:File[])=>{const data=new FormData();Object.entries(payload).forEach(([key,value])=>{if(value)data.append(key,value)});files.forEach(file=>data.append('files',file));return http.post<unknown,{data:InboxItem}>('/inbox/collect',data,{headers:{'Content-Type':'multipart/form-data'},timeout:90000});};
export const downloadInboxAttachment=async(id:number,name:string)=>{const response=await http.get('/inbox/attachments/'+id,{responseType:'blob'});const url=URL.createObjectURL(response as unknown as Blob);const link=document.createElement('a');link.href=url;link.download=name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
export const confirmInbox=(id:number,confirmedType:string)=>http.patch(`/inbox/${id}/confirm`,{confirmedType});
export const archiveInbox=(id:number)=>http.patch(`/inbox/${id}/archive`);
export const getDailyDashboard=()=>http.get<unknown,{data:DailyDashboard}>('/dashboard/daily');
export const getDailyInspiration=()=>http.get<unknown,{data:DailyInspiration}>('/dashboard/daily-inspiration');
export const listReports=()=>http.get<unknown,{data:GeneratedReport[]}>('/reports');
export const generateReport=(type:string,reference?:string)=>http.post<unknown,{data:GeneratedReport}>('/reports/generate',null,{params:{type,reference}});
