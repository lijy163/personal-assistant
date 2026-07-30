import http from './http';
export interface AuditLog { id:number; operation:string; resourceType?:string; resourceId?:string; result:string; ipAddress?:string; userAgent?:string; failureReason?:string; occurredAt:string; }
export function listAuditLogs(operation='',result=''){return http.get<unknown,{data:AuditLog[]}>('/security/audit-logs',{params:{operation:operation||undefined,result:result||undefined}});}
