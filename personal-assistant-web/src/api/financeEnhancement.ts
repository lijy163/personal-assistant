import http from './http';

export interface FinanceTransactionPayload {
  accountId:number;
  categoryId?:number;
  transactionTime:string;
  direction:string;
  amount:number;
  merchant?:string;
  description?:string;
  transactionType?:string;
  note?:string;
}

export interface FinanceTextParseRow {
  rowNumber:number; transactionTime:string; direction:string; amount:number; merchant:string;
  description:string; transactionType:string; categoryId?:number; sourceText:string; warning?:string;
}
export interface FinanceTextParsePreview { rows:FinanceTextParseRow[]; ignoredLineCount:number; }

export interface MonthlyAnalysis {
  month:string;
  income:number;
  expense:number;
  balance:number;
  transactionCount:number;
  expenseCount:number;
  averageDailyExpense:number;
  topExpenseCategory?:string;
  categories:Array<{categoryName:string;amount:number;count:number;percentage:number}>;
  trend:Array<{month:string;income:number;expense:number;balance:number}>;
}

export const parseFinanceText=(text:string)=>http.post<unknown,{data:FinanceTextParsePreview}>('/finance/manual-transactions/parse-text',{text});
export const createManualTransactionsBatch=(transactions:FinanceTransactionPayload[])=>http.post<unknown,{data:number}>('/finance/manual-transactions/batch',{transactions});
export const createManualTransaction=(data:FinanceTransactionPayload)=>http.post('/finance/manual-transactions',data);
export const updateManualTransaction=(id:number,data:FinanceTransactionPayload)=>http.put(`/finance/manual-transactions/${id}`,data);
export const deleteManualTransaction=(id:number)=>http.delete(`/finance/manual-transactions/${id}`);
export const deleteManualTransactionsBatch=(ids:number[])=>http.post<unknown,{data:number}>('/finance/manual-transactions/batch-delete',{ids});
export const getFinanceMonthlyAnalysis=(month:string)=>http.get<unknown,{data:MonthlyAnalysis}>('/finance/stats/monthly-analysis',{params:{month}});
