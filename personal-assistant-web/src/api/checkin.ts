import http from './http';

export interface CheckinHabit {
  id: number;
  name: string;
  icon?: string;
  color: string;
  category: string;
  note?: string;
  active: boolean;
  sortOrder: number;
  checked: boolean;
  streak: number;
  monthCount: number;
  lastCheckinDate?: string;
}

export interface CheckinRecord {
  id: number;
  habitId: number;
  habitName: string;
  icon?: string;
  color?: string;
  checkinDate: string;
  note?: string;
  source: string;
  activityLogId?: number;
  createdAt: string;
}

export interface CheckinOverview {
  date: string;
  habitCount: number;
  doneCount: number;
  currentStreak: number;
  monthCount: number;
  totalDays: number;
  habits: CheckinHabit[];
  records: CheckinRecord[];
}

export interface CheckinDaySummary {
  checkinDate: string;
  doneCount: number;
}

export interface CheckinResult {
  habitId: number;
  checkinDate: string;
  checked: boolean;
  streak: number;
  activityLogId?: number;
}

export interface CheckinHabitPayload {
  name: string;
  icon?: string;
  color?: string;
  category?: string;
  note?: string;
  active?: boolean;
  sortOrder?: number;
}

export interface HabitRemovalResult {
  archived: boolean;
}

export function getCheckinOverview(date?: string) {
  return http.get<unknown, { data: CheckinOverview }>('/checkins/overview', { params: { date } });
}

export function getCheckinCalendar(from?: string, to?: string) {
  return http.get<unknown, { data: CheckinDaySummary[] }>('/checkins/calendar', { params: { from, to } });
}

export function createCheckinHabit(payload: CheckinHabitPayload) {
  return http.post<unknown, { data: number }>('/checkins/habits', payload);
}

export function updateCheckinHabit(id: number, payload: CheckinHabitPayload) {
  return http.put<unknown, { data: null }>(`/checkins/habits/${id}`, payload);
}

export function removeCheckinHabit(id: number) {
  return http.delete<unknown, { data: HabitRemovalResult }>(`/checkins/habits/${id}`);
}

export function submitCheckin(habitId: number, date?: string, note?: string) {
  return http.post<unknown, { data: CheckinResult }>(`/checkins/habits/${habitId}/check`, null, {
    params: { date, note },
  });
}

export function cancelCheckin(habitId: number, date?: string) {
  return http.delete<unknown, { data: CheckinResult }>(`/checkins/habits/${habitId}/check`, {
    params: { date },
  });
}
