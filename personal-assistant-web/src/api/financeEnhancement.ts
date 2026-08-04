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

export const createManualTransaction=(data:FinanceTransactionPayload)=>http.post('/finance/manual-transactions',data);
export const updateManualTransaction=(id:number,data:FinanceTransactionPayload)=>http.put(`/finance/manual-transactions/${id}`,data);
export const deleteManualTransaction=(id:number)=>http.delete(`/finance/manual-transactions/${id}`);
export const getFinanceMonthlyAnalysis=(month:string)=>http.get<unknown,{data:MonthlyAnalysis}>('/finance/stats/monthly-analysis',{params:{month}});
