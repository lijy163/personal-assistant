import http from './http';

export interface DevLogSummary {
  id: number; title: string; projectName: string; branchName?: string; commitHash?: string;
  tags?: string; source: string; occurredAt: string; createdAt: string;
}

export interface DevLog extends DevLogSummary {
  repository?: string; taskGoal: string; coreChanges: string; technicalDecisions?: string;
  problemSolution?: string; verificationResult?: string; markdownContent: string;
}

export interface PersonalAccessToken {
  id: number; name: string; tokenPrefix: string; scope: string; expiresAt?: string;
  lastUsedAt?: string; revokedAt?: string; createdAt: string;
}

export interface CreatedPersonalAccessToken {
  id: number; name: string; token: string; scope: string; expiresAt?: string; createdAt: string;
}

export const listDevLogs = (params: { keyword?: string; projectName?: string } = {}) =>
  http.get<unknown, { data: DevLogSummary[] }>('/devlogs', { params });
export const getDevLog = (id: number) => http.get<unknown, { data: DevLog }>(`/devlogs/${id}`);
export const listPersonalAccessTokens = () =>
  http.get<unknown, { data: PersonalAccessToken[] }>('/personal-access-tokens');
export const createPersonalAccessToken = (data: { name: string; expiresAt?: string }) =>
  http.post<unknown, { data: CreatedPersonalAccessToken }>('/personal-access-tokens', data);
export const revokePersonalAccessToken = (id: number) =>
  http.patch(`/personal-access-tokens/${id}/revoke`);
