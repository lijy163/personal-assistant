import http from './http';

export interface QuickNote { id: number; content: string; status: string; createdAt: string; updatedAt: string; }
export function createQuickNote(content: string) { return http.post<unknown, { data: number }>('/quick-notes', { content }); }
export function listPendingQuickNotes() { return http.get<unknown, { data: QuickNote[] }>('/quick-notes/pending'); }
export function archiveQuickNote(id: number) { return http.patch(`/quick-notes/${id}/archive`); }