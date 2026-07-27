import http from './http';

export interface WatchItem {
  id: number;
  stockCode: string;
  stockName: string;
  market: string;
  industry: string | null;
  latestPrice: number | null;
  changePercent: number | null;
  marketValue: number | null;
  quoteTime: string | null;
  tags: string | null;
  reason: string | null;
  remark: string | null;
  enabled: boolean;
}

export interface ApiConfig {
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

export interface CollectionResult {
  id: number;
  watchItemId: number;
  apiConfigId: number | null;
  success: boolean;
  summary: string | null;
  errorMessage: string | null;
  collectedAt: string;
}

export interface StockMapItem {
  id: number;
  stockCode: string;
  stockName: string;
  market: string;
  industry: string;
  latestPrice: number | null;
  changePercent: number | null;
  marketValue: number | null;
  weight: number;
  quoteTime: string | null;
  tags: string | null;
}

export interface StockIndustryNode {
  industry: string;
  count: number;
  averageChangePercent: number;
  totalMarketValue: number;
  children: StockMapItem[];
}

export interface StockMarketMapStats {
  total: number;
  up: number;
  flat: number;
  down: number;
  averageChangePercent: number;
}

export interface StockMarketMapResponse {
  market: string;
  scope: string;
  generatedAt: string;
  stats: StockMarketMapStats;
  industries: StockIndustryNode[];
}


export interface StockQuoteRefreshItem {
  watchItemId: number;
  stockCode: string;
  stockName: string;
  success: boolean;
  message: string;
}

export interface StockQuoteRefreshResponse {
  total: number;
  success: number;
  failed: number;
  refreshedAt: string;
  items: StockQuoteRefreshItem[];
}

export interface StockQuoteFailure {
  watchItemId: number;
  stockName: string;
  errorMessage: string | null;
  collectedAt: string;
}

export interface StockQuoteStatusResponse {
  watchCount: number;
  quotedCount: number;
  missingQuoteCount: number;
  lastQuoteTime: string | null;
  lastRefreshTime: string | null;
  recentSuccess: number;
  recentFailed: number;
  recentFailures: StockQuoteFailure[];
}
export interface WatchPayload {
  stockCode: string;
  stockName: string;
  market: string;
  industry?: string | null;
  latestPrice?: number | null;
  changePercent?: number | null;
  marketValue?: number | null;
  quoteTime?: string | null;
  tags?: string | null;
  reason?: string | null;
  remark?: string | null;
  enabled: boolean;
}

export const listWatchItems = (params: Record<string, unknown> = {}) =>
  http.get<unknown, { data: WatchItem[] }>('/stocks/watch-items', { params });

export const saveWatchItem = (data: WatchPayload) => http.post('/stocks/watch-items', data);

export const updateWatchItem = (id: number, data: WatchPayload) => http.put(`/stocks/watch-items/${id}`, data);

export const toggleWatchItem = (id: number, enabled: boolean) =>
  http.patch(`/stocks/watch-items/${id}/enabled`, null, { params: { enabled } });

export const listApiConfigs = () => http.get<unknown, { data: ApiConfig[] }>('/stocks/api-configs');

export const saveApiConfig = (data: Record<string, unknown>) => http.post('/stocks/api-configs', data);

export const testApiConfig = (id: number) => http.post<unknown, { data: boolean }>(`/stocks/api-configs/${id}/test`);

export const listCollectionResults = (watchId?: number) =>
  http.get<unknown, { data: CollectionResult[] }>('/stocks/collection-results', { params: { watchId } });

export const collectStocks = () => http.post<unknown, { data: number }>('/stocks/collect');

export const getStockMarketMap = (params: { market?: string; enabledOnly?: boolean } = {}) =>
  http.get<unknown, { data: StockMarketMapResponse }>('/stocks/market-map', { params });
export const refreshStockQuotes = (params: { market?: string; enabledOnly?: boolean } = {}) =>
  http.post<unknown, { data: StockQuoteRefreshResponse }>('/stocks/quotes/refresh', null, { params });
export const getStockQuoteStatus = (params: { market?: string; enabledOnly?: boolean } = {}) =>
  http.get<unknown, { data: StockQuoteStatusResponse }>('/stocks/quotes/status', { params });
