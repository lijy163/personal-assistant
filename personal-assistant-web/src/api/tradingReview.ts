import http from './http';

export interface DailyReview {
  id: number; tradeDate: string; snapshotType: 'REALTIME' | 'FINAL'; tradingDay: boolean; status: string;
  shanghaiChange?: number; shenzhenChange?: number; chinextChange?: number; risingCount?: number; fallingCount?: number;
  flatCount?: number; limitUpCount?: number; limitDownCount?: number; brokenBoardCount?: number; brokenBoardRate?: number;
  maxStreak?: number; turnoverAmount?: number; turnoverChange?: number; sentimentScore?: number; marketStage?: string;
  suggestedPosition?: number; autoConclusion?: string; ruleVersion?: string; dimensionScores?: string; rawMetrics?: string;
  dataSource?: string; quoteTime?: string; collectedAt?: string; dataCompleteness: string; freshness?: string;
  collectionStatus: string; failureReason?: string; lastSuccessAt?: string; sectors?: string; coreStocks?: string;
  holdingCheck?: string; manualJudgment?: string;
}
export type ReviewPayload = Omit<DailyReview, 'id' | 'sentimentScore' | 'marketStage' | 'suggestedPosition' | 'autoConclusion' | 'ruleVersion' | 'dimensionScores' | 'rawMetrics' | 'dataSource' | 'quoteTime' | 'collectedAt' | 'dataCompleteness' | 'freshness' | 'collectionStatus' | 'failureReason' | 'lastSuccessAt'>;
export interface TradeLog { id:number; watchItemId?:number; sourceAlertEventId?:number; sourceMarketAlertEventId?:number; signalType?:string; signalReason?:string; stockCode:string; stockName:string; status:string; strategy?:string; buyLogic?:string; sellLogic?:string; planned:boolean; planExecutionNote?:string; questionBuyLogic?:string; questionSealFunds?:string; questionTakeoverFunds?:string; latestPrice?:number; openedAt?:string; closedAt?:string; notes?:string; }
export type TradePayload = Omit<TradeLog, 'id'>;
export interface Execution { id:number; tradeLogId:number; side:'BUY'|'SELL'; quantity:number; price:number; commission:number; stampDuty:number; transferFee:number; occurredAt:string; remark?:string; }
export type ExecutionPayload = Omit<Execution, 'id'|'tradeLogId'>;
export interface TradeMetrics { buyQuantity:number; sellQuantity:number; remainingQuantity:number; averageCost:number; realizedProfit:number; unrealizedProfit:number; totalFees:number; returnRate:number; holdingDays:number; }
export interface TradeDetail { trade:TradeLog; executions:Execution[]; metrics:TradeMetrics; }
export interface NextPlan { id:number; tradeDate:string; marketPremise?:string; targetPosition?:number; watchStocks?:string; plannedTrades?:string; riskControls?:string; reminderIds?:string; reportNote?:string; status:string; }
export type PlanPayload = Omit<NextPlan, 'id'>;
export interface TradingMistake { id:number; tradeLogId?:number; occurredDate:string; category:string; title:string; description?:string; rootCause?:string; correction?:string; status:string; repeatCount:number; }
export type MistakePayload = Omit<TradingMistake, 'id'>;
export interface TradingStats { closedTrades:number; winningTrades:number; losingTrades:number; winRate:number; averageReturn:number; averageWin:number; averageLoss:number; profitLossRatio:number; realizedProfit:number; unrealizedProfit:number; totalFees:number; averageMfe:number; averageMae:number; maxDrawdown:number; strategyAttribution:{name:string;trades:number;realizedProfit:number;averageReturn:number;winRate:number}[]; signalAttribution:{name:string;trades:number;realizedProfit:number;averageReturn:number;winRate:number}[]; holdingPeriodAttribution:{name:string;trades:number;realizedProfit:number;averageReturn:number;winRate:number}[]; frequentErrors:{category:string;count:number}[]; }
export interface CollectionResult { review:DailyReview; fresh:boolean; message:string; lastSuccessAt?:string; }
export interface TradingAlertRule {
  id:number; watchItemId?:number; planId?:number; stockCode:string; stockName:string; ruleType:string; direction:string;
  thresholdValue:number; referencePosition?:number; title:string; note?:string; enabled:boolean; onceOnly:boolean; status:string;
  validFrom?:string; validTo?:string; lastCheckedAt?:string; lastTriggeredAt?:string; lastObservedValue?:number;
}
export type TradingAlertRulePayload = Omit<TradingAlertRule, 'id'|'status'|'lastCheckedAt'|'lastTriggeredAt'|'lastObservedValue'>;
export interface TradingAlertEvent {
  id:number; ruleId:number; watchItemId?:number; stockCode:string; stockName:string; ruleType:string; direction:string;
  thresholdValue:number; observedValue:number; latestPrice?:number; changePercent?:number; title:string; content?:string;
  notificationStatus:string; notificationMessage?:string; triggeredAt:string;
}
export interface TradingAlertScanResult { checkedRules:number; triggeredEvents:number; skippedRules:number; }
export interface TradingMarketAlertRule {
  id:number; ruleType:string; snapshotType:string; metricKey:string; direction:string; thresholdValue:number;
  sectorName?:string; sectorLevel?:number; title:string; note?:string; enabled:boolean; onceOnly:boolean; status:string;
  validFrom?:string; validTo?:string; lastCheckedAt?:string; lastTriggeredAt?:string; lastObservedValue?:number; lastReviewId?:number;
}
export type TradingMarketAlertRulePayload = Omit<TradingMarketAlertRule, 'id'|'status'|'lastCheckedAt'|'lastTriggeredAt'|'lastObservedValue'|'lastReviewId'>;
export interface TradingMarketAlertEvent {
  id:number; ruleId:number; reviewId?:number; tradeDate:string; snapshotType:string; ruleType:string; metricKey:string; direction:string;
  thresholdValue:number; observedValue:number; sectorName?:string; title:string; content?:string; notificationStatus:string; notificationMessage?:string; triggeredAt:string;
}
export interface TradingMarketAlertScanResult { checkedRules:number; triggeredEvents:number; skippedRules:number; }
export interface ReviewAnalytics {
  fiveDayTrend:{tradeDate:string;sentimentScore?:number;risingCount?:number;fallingCount?:number;limitUpCount?:number;limitDownCount?:number;brokenBoardRate?:number;maxStreak?:number;turnoverAmount?:number;turnoverChange?:number}[];
  intradayTimeline:{quoteTime:string;sentimentScore?:number;marketStage?:string;risingCount?:number;fallingCount?:number;limitUpCount?:number;limitDownCount?:number;brokenBoardRate?:number;turnoverAmount?:number}[];
  advancement:{currentDate?:string;previousDate?:string;previousFirstBoards:number;firstToSecond:number;firstToSecondRate:number;previousSecondBoards:number;secondToThird:number;secondToThirdRate:number;status:string};
  execution:{duePlans:number;completedPlans:number;planCompletionRate:number;trades:number;plannedTrades:number;plannedTradeRate:number;alertEvents:number;alertLinkedTrades:number;alertTradeRate:number;averageAlertResponseMinutes:number;averagePositionDeviation:number;status:string};
}

export const listReviews=()=>http.get<unknown,{data:DailyReview[]}>('/trading-reviews/reviews');
export const saveReview=(data:ReviewPayload,id?:number)=>id?http.put(`/trading-reviews/reviews/${id}`,data):http.post('/trading-reviews/reviews',data);
export const removeReview=(id:number)=>http.delete(`/trading-reviews/reviews/${id}`);
export const refreshMarket=(tradeDate:string,snapshotType:string)=>http.post<unknown,{data:CollectionResult}>('/trading-reviews/market/refresh',null,{params:{tradeDate,snapshotType}});
export const getReviewAnalytics=(tradeDate:string)=>http.get<unknown,{data:ReviewAnalytics}>('/trading-reviews/analytics',{params:{tradeDate}});
export const listTrades=()=>http.get<unknown,{data:TradeLog[]}>('/trading-reviews/trades');
export const getTrade=(id:number)=>http.get<unknown,{data:TradeDetail}>(`/trading-reviews/trades/${id}`);
export const saveTrade=(data:TradePayload,id?:number)=>id?http.put(`/trading-reviews/trades/${id}`,data):http.post('/trading-reviews/trades',data);
export const removeTrade=(id:number)=>http.delete(`/trading-reviews/trades/${id}`);
export const addExecution=(tradeId:number,data:ExecutionPayload)=>http.post(`/trading-reviews/trades/${tradeId}/executions`,data);
export const removeExecution=(tradeId:number,id:number)=>http.delete(`/trading-reviews/trades/${tradeId}/executions/${id}`);
export const listPlans=()=>http.get<unknown,{data:NextPlan[]}>('/trading-reviews/plans');
export const savePlan=(data:PlanPayload,id?:number)=>id?http.put(`/trading-reviews/plans/${id}`,data):http.post('/trading-reviews/plans',data);
export const removePlan=(id:number)=>http.delete(`/trading-reviews/plans/${id}`);
export const listAlertRules=(params:Record<string,unknown>={})=>http.get<unknown,{data:TradingAlertRule[]}>('/trading-reviews/alerts/rules',{params});
export const saveAlertRule=(data:TradingAlertRulePayload,id?:number)=>id?http.put(`/trading-reviews/alerts/rules/${id}`,data):http.post('/trading-reviews/alerts/rules',data);
export const toggleAlertRule=(id:number,enabled:boolean)=>http.patch(`/trading-reviews/alerts/rules/${id}/enabled`,null,{params:{enabled}});
export const removeAlertRule=(id:number)=>http.delete(`/trading-reviews/alerts/rules/${id}`);
export const listAlertEvents=(params:Record<string,unknown>={})=>http.get<unknown,{data:TradingAlertEvent[]}>('/trading-reviews/alerts/events',{params});
export const scanAlertRules=()=>http.post<unknown,{data:TradingAlertScanResult}>('/trading-reviews/alerts/scan');
export const listMarketAlertRules=(params:Record<string,unknown>={})=>http.get<unknown,{data:TradingMarketAlertRule[]}>('/trading-reviews/market-alerts/rules',{params});
export const saveMarketAlertRule=(data:TradingMarketAlertRulePayload,id?:number)=>id?http.put(`/trading-reviews/market-alerts/rules/${id}`,data):http.post('/trading-reviews/market-alerts/rules',data);
export const toggleMarketAlertRule=(id:number,enabled:boolean)=>http.patch(`/trading-reviews/market-alerts/rules/${id}/enabled`,null,{params:{enabled}});
export const removeMarketAlertRule=(id:number)=>http.delete(`/trading-reviews/market-alerts/rules/${id}`);
export const listMarketAlertEvents=(params:Record<string,unknown>={})=>http.get<unknown,{data:TradingMarketAlertEvent[]}>('/trading-reviews/market-alerts/events',{params});
export const scanMarketAlertRules=()=>http.post<unknown,{data:TradingMarketAlertScanResult}>('/trading-reviews/market-alerts/scan');
export const listMistakes=()=>http.get<unknown,{data:TradingMistake[]}>('/trading-reviews/mistakes');
export const saveMistake=(data:MistakePayload,id?:number)=>id?http.put(`/trading-reviews/mistakes/${id}`,data):http.post('/trading-reviews/mistakes',data);
export const removeMistake=(id:number)=>http.delete(`/trading-reviews/mistakes/${id}`);
export const getTradingStats=()=>http.get<unknown,{data:TradingStats}>('/trading-reviews/stats');
