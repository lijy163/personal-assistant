import http from './http';

export interface CodexAgent {
  id: number; name: string; model?: string; reasoningEffort?: string; tokenPrefix: string; status: string;
  lastSeenAt?: string; revokedAt?: string; createdAt: string;
}

export interface CreatedCodexAgent {
  id: number; name: string; token: string; createdAt: string;
}

export interface CodexTask {
  id: number; agentId: number; agentName: string; projectKey: string; prompt: string;
  model?: string; reasoningEffort?: string;
  permissionMode: 'READ_ONLY' | 'WORKSPACE_WRITE'; status: string; threadId?: string;
  finalResponse?: string; errorMessage?: string; requestedAt: string; startedAt?: string;
  finishedAt?: string; updatedAt: string;
}

export interface CodexTaskEvent {
  id: number; eventType: string; content: string; createdAt: string;
}

export const listCodexAgents = () => http.get<unknown, { data: CodexAgent[] }>('/codex-agents');
export const createCodexAgent = (data: { name: string }) =>
  http.post<unknown, { data: CreatedCodexAgent }>('/codex-agents', data);
export const revokeCodexAgent = (id: number) => http.patch(`/codex-agents/${id}/revoke`);
export const updateCodexAgentModel = (id: number, data: { model?: string; reasoningEffort: string }) =>
  http.patch(`/codex-agents/${id}/model`, data);
export const listCodexTasks = () => http.get<unknown, { data: CodexTask[] }>('/codex-agents/tasks');
export const createCodexTask = (data: { agentId: number; projectKey: string; prompt: string; permissionMode: string }) =>
  http.post<unknown, { data: number }>('/codex-agents/tasks', data);
export const cancelCodexTask = (id: number) => http.post(`/codex-agents/tasks/${id}/cancel`);
export const listCodexTaskEvents = (id: number) =>
  http.get<unknown, { data: CodexTaskEvent[] }>(`/codex-agents/tasks/${id}/events`);
