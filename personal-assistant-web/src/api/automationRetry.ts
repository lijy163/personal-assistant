import http from './http';export const retryAutomationExecution=(id:number)=>http.post(`/automation/executions/${id}/retry`);
