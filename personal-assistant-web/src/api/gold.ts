import http from './http';

export interface GoldWatchItem {
  id: number;
  goldType: string;
  brandName: string | null;
  displayName: string;
  unit: string;
  latestPrice: number | null;
  changeAmount: number | null;
  changePercent: number | null;
  highPrice: number | null;
  lowPrice: number | null;
  openPrice: number | null;
  previousClose: number | null;
  buyPrice: number | null;
  sellPrice: number | null;
  quoteTime: string | null;
  sourceName: string | null;
  sourceUrl: string | null;
  remark: string | null;
  enabled: boolean;
}

export interface GoldWatchPayload {
  goldType: string;
  brandName?: string | null;
  displayName: string;
  unit: string;
  latestPrice?: number | null;
  changeAmount?: number | null;
  changePercent?: number | null;
  highPrice?: number | null;
  lowPrice?: number | null;
  openPrice?: number | null;
  previousClose?: number | null;
  buyPrice?: number | null;
  sellPrice?: number | null;
  quoteTime?: string | null;
  sourceName?: string | null;
  sourceUrl?: string | null;
  remark?: string | null;
  enabled: boolean;
}

export interface GoldApiConfig {
  id: number;
  apiName: string;
  purpose: string | null;
  endpoint: string;
  authType: string;
  maskedApiKey: string;
  rateLimitPerMinute: number;
  enabled: boolean;
  lastTestTime: string | null;
  lastTestSuccess: boolean | null;
  lastTestMessage: string | null;
}

export interface GoldCollectionResult {
  id: number;
  watchItemId: number;
  apiConfigId: number | null;
  success: boolean;
  summary: string | null;
  errorMessage: string | null;
  collectedAt: string;
}

export interface GoldQuoteRefreshResponse {
  total: number;
  success: number;
  failed: number;
  refreshedAt: string;
}

export interface GoldQuoteFailure {
  watchItemId: number;
  displayName: string;
  errorMessage: string | null;
  collectedAt: string;
}

export interface GoldQuoteStatusResponse {
  watchCount: number;
  quotedCount: number;
  missingQuoteCount: number;
  lastQuoteTime: string | null;
  lastRefreshTime: string | null;
  recentSuccess: number;
  recentFailed: number;
  recentFailures: GoldQuoteFailure[];
}

export const listGoldWatchItems = (params: Record<string, unknown> = {}) =>
  http.get<unknown, { data: GoldWatchItem[] }>('/gold/watch-items', { params });

export const saveGoldWatchItem = (data: GoldWatchPayload) => http.post('/gold/watch-items', data);

export const updateGoldWatchItem = (id: number, data: GoldWatchPayload) => http.put(`/gold/watch-items/${id}`, data);

export const toggleGoldWatchItem = (id: number, enabled: boolean) =>
  http.patch(`/gold/watch-items/${id}/enabled`, null, { params: { enabled } });

export const listGoldApiConfigs = () => http.get<unknown, { data: GoldApiConfig[] }>('/gold/api-configs');

export const saveGoldApiConfig = (data: Record<string, unknown>) => http.post('/gold/api-configs', data);

export const updateGoldApiConfig = (id: number, data: Record<string, unknown>) => http.put(`/gold/api-configs/${id}`, data);

export const testGoldApiConfig = (id: number) => http.post<unknown, { data: boolean }>(`/gold/api-configs/${id}/test`);

export const listGoldCollectionResults = (watchId?: number) =>
  http.get<unknown, { data: GoldCollectionResult[] }>('/gold/collection-results', { params: { watchId } });

export const collectGold = () => http.post<unknown, { data: number }>('/gold/collect');

export const refreshGoldQuotes = (params: { goldType?: string; enabledOnly?: boolean } = {}) =>
  http.post<unknown, { data: GoldQuoteRefreshResponse }>('/gold/quotes/refresh', null, { params });

export const getGoldQuoteStatus = (params: { goldType?: string; enabledOnly?: boolean } = {}) =>
  http.get<unknown, { data: GoldQuoteStatusResponse }>('/gold/quotes/status', { params });
