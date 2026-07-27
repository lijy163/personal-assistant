import http from './http';

export interface FundFlowSnapshot { id:number; watchItemId:number; stockCode:string; market:string; mainNetInflow:number|null; mainNetRatio:number|null; superLargeNetInflow:number|null; superLargeNetRatio:number|null; largeNetInflow:number|null; largeNetRatio:number|null; mediumNetInflow:number|null; mediumNetRatio:number|null; smallNetInflow:number|null; smallNetRatio:number|null; latestPrice:number|null; changePercent:number|null; turnoverAmount:number|null; provider:string; periodType:string; quoteTime:string; collectedAt:string; }
export interface FundFlowRankingItem { watchItemId:number; stockCode:string; stockName:string; mainNetInflow:number|null; mainNetRatio:number|null; superLargeNetInflow:number|null; largeNetInflow:number|null; mediumNetInflow:number|null; smallNetInflow:number|null; changePercent:number|null; quoteTime:string; }
export interface FundFlowOverview { provider:string; latestQuoteTime:string|null; watchCount:number; coveredCount:number; coverageRate:number; totalMainNetInflow:number; inflowCount:number; outflowCount:number; ranking:FundFlowRankingItem[]; }
export interface FundFlowStatus { watchCount:number; coveredCount:number; missingCount:number; coverageRate:number; lastQuoteTime:string|null; lastRefreshTime:string|null; recentSuccess:number; recentFailed:number; recentFailures:Array<{watchItemId:number;stockName:string;errorMessage:string;collectedAt:string}>; }
export interface FundFlowRefresh { total:number; success:number; failed:number; refreshedAt:string; items:Array<{watchItemId:number;stockCode:string;stockName:string;success:boolean;snapshotCount:number;message:string}>; }

export const refreshStockFundFlow = () => http.post<unknown,{data:FundFlowRefresh}>('/stocks/fund-flow/refresh');
export const getStockFundFlowOverview = () => http.get<unknown,{data:FundFlowOverview}>('/stocks/fund-flow/overview');
export const getStockFundFlowStatus = () => http.get<unknown,{data:FundFlowStatus}>('/stocks/fund-flow/status');
export const getStockFundFlowTrend = (watchId:number,days=20) => http.get<unknown,{data:FundFlowSnapshot[]}>(`/stocks/${watchId}/fund-flow/trend`,{params:{days}});

export interface SectorFundFlow{scope:string;provider:string;quoteTime:string|null;sectors:Array<{industry:string;stockCount:number;mainNetInflow:number;averageMainNetRatio:number;averageChangePercent:number}>;}
export const getStockSectorFundFlow=()=>http.get<unknown,{data:SectorFundFlow}>('/stocks/fund-flow/sectors');
