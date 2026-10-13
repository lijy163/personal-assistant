import http from './http';

export interface ActivityLog {
  id: number;
  content: string;
  logDate: string;
  occurredAt: string;
  category: string;
  tags?: string;
  source: string;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ActivityDaySummary {
  logDate: string;
  entryCount: number;
}

export interface ActivityTagSummary {
  tag: string;
  tagCount: number;
}

export interface ActivityLogPayload {
  content: string;
  logDate?: string;
  occurredAt?: string;
  category?: string;
  tags?: string;
  source?: string;
}

export interface ActivityLogQuery {
  from?: string;
  to?: string;
  keyword?: string;
  category?: string;
  tag?: string;
  limit?: number;
}

export interface ActivityCategoryOption {
  value: string;
  label: string;
}

export const activityCategories: ActivityCategoryOption[] = [
  { value: 'WORK', label: '工作' },
  { value: 'STUDY', label: '学习' },
  { value: 'EXERCISE', label: '运动' },
  { value: 'FOOD', label: '饮食' },
  { value: 'SOCIAL', label: '社交' },
  { value: 'REST', label: '休息' },
  { value: 'OTHER', label: '其他' },
];

export function listActivityLogs(query: ActivityLogQuery) {
  return http.get<unknown, { data: ActivityLog[] }>('/activity-logs', { params: query });
}

export function createActivityLog(payload: ActivityLogPayload) {
  return http.post<unknown, { data: ActivityLog }>('/activity-logs', payload);
}

export function listActivityTimeline(date?: string) {
  return http.get<unknown, { data: ActivityLog[] }>('/activity-logs/timeline', { params: { date } });
}

export function getActivityCalendar(from?: string, to?: string) {
  return http.get<unknown, { data: ActivityDaySummary[] }>('/activity-logs/calendar', { params: { from, to } });
}

export function listActivityTags(from?: string, to?: string) {
  return http.get<unknown, { data: ActivityTagSummary[] }>('/activity-logs/tags', { params: { from, to } });
}

export function updateActivityLog(id: number, payload: ActivityLogPayload & { pinned?: boolean }) {
  return http.put<unknown, { data: ActivityLog }>(`/activity-logs/${id}`, payload);
}

export function toggleActivityPin(id: number) {
  return http.patch<unknown, { data: ActivityLog }>(`/activity-logs/${id}/pin`);
}

export function deleteActivityLog(id: number) {
  return http.delete<unknown, { data: null }>(`/activity-logs/${id}`);
}
