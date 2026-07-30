import http from './http';
export interface AiInvocation{id:number;scene:string;sourceType?:string;sourceId?:number;modelName?:string;redactedInput:string;resultContent?:string;status:string;confirmed:boolean;failureReason?:string;createdAt:string;}
export const listAiConfigs=()=>http.get<unknown,{data:Array<Record<string,unknown>>}>('/ai/configs');
export const saveAiConfig=(data:Record<string,unknown>)=>http.post('/ai/configs',data);
export const aiAssist=(data:Record<string,unknown>)=>http.post<unknown,{data:AiInvocation}>('/ai/assist',data);
export const listAiInvocations=()=>http.get<unknown,{data:AiInvocation[]}>('/ai/invocations');
export const confirmAiInvocation=(id:number)=>http.post(`/ai/invocations/${id}/confirm`);
