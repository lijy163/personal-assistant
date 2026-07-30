import http from './http';

export interface CalendarEvent {
  key: string; sourceType: string; sourceId: number; title: string; startAt: string; endAt?: string;
  allDay: boolean; status: string; color: string; route: string; recurrenceRule?: string;
  workdayOnly: boolean; overridden: boolean; conflict: boolean;
}
export interface CalendarEventInput { title: string; description?: string; startAt: string; endAt?: string; allDay: boolean; status?: string; color?: string; recurrenceRule?: string; workdayOnly: boolean; }
export function listCalendarEvents(start: string, end: string, sources: string[], statuses: string[]) { return http.get<unknown, { data: CalendarEvent[] }>('/calendar', { params: { start, end, sources: sources.join(','), statuses: statuses.join(',') } }); }
export function createCalendarEvent(data: CalendarEventInput) { return http.post<unknown, { data: number }>('/calendar', data); }
export function moveCalendarEvent(sourceType: string, sourceId: number, data: { startAt: string; endAt?: string; allDay: boolean; color?: string }) { return http.patch(`/calendar/${sourceType}/${sourceId}/time`, data); }
export function deleteCalendarEvent(id: number) { return http.delete(`/calendar/custom/${id}`); }
