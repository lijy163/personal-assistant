import http from './http';
export type TaskType='LIFE'|'WORK'|'LEARNING'; export type TaskPriority='LOW'|'MEDIUM'|'HIGH'|'URGENT'; export type TaskStatus='DRAFT'|'NOT_STARTED'|'IN_PROGRESS'|'COMPLETED'|'ARCHIVED';
export interface TaskItem { id:number;title:string;itemType:TaskType;priority:TaskPriority;status:TaskStatus;planTime:string|null;deadline:string|null;reminderEnabled:boolean;tags:string|null;remark:string|null;category:string|null;workType:string|null;projectName:string|null;statusChangedAt:string;createdAt:string;updatedAt:string; }
export interface TaskPayload { title:string;itemType:TaskType;priority:TaskPriority;status:TaskStatus;planTime:string|null;deadline:string|null;reminderEnabled:boolean;tags:string;remark:string;category:string;workType:string;projectName:string; }
export interface TaskQuery { type:TaskType;keyword?:string;status?:TaskStatus;priority?:TaskPriority;start?:string;end?:string; }
export interface WorkReview {id:number;taskId:number;content:string;resultType:string;createdAt:string;}
export function listTasks(params:TaskQuery){return http.get<unknown,{data:TaskItem[]}>('/tasks',{params});}
export function createTask(data:TaskPayload){return http.post<unknown,{data:number}>('/tasks',data);}
export function updateTask(id:number,data:TaskPayload){return http.put(`/tasks/${id}`,data);}
export function changeTaskStatus(id:number,status:TaskStatus){return http.patch(`/tasks/${id}/status`,{status});}
export function archiveTask(id:number){return http.patch(`/tasks/${id}/archive`);}
export function listReviews(id:number){return http.get<unknown,{data:WorkReview[]}>(`/tasks/${id}/reviews`);}
export function addReview(id:number,data:{content:string;resultType:string}){return http.post(`/tasks/${id}/reviews`,data);}