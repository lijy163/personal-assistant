import http from './http';
export interface FinanceAccount{id:number;accountName:string;accountType:string;institution?:string;currency:string;enabled:boolean;}
export interface FinanceCategory{id:number;categoryName:string;direction:string;parentId?:number;enabled:boolean;}
export interface FinanceRule{id:number;ruleName:string;keyword:string;categoryId:number;priority:number;enabled:boolean;}
export interface RawTransaction{id:number;rowNumber:number;transactionTime?:string;platformTransactionNo?:string;merchant?:string;description?:string;direction?:string;amount?:number;transactionType?:string;categoryId?:number;duplicateFlag:boolean;validationError?:string;}
export interface ImportPreview{batchId:number;totalCount:number;duplicateCount:number;invalidCount:number;rows:RawTransaction[];}
export interface ImportBatch{id:number;accountId:number;platform:string;fileName:string;status:string;totalCount:number;duplicateCount:number;importedCount:number;createdAt:string;}
export interface FinanceTransaction{id:number;accountId:number;categoryId?:number;transactionTime:string;merchant?:string;description?:string;direction:string;amount:number;transactionType:string;confirmed:boolean;}
export interface MonthlySummary{month:string;income:number;expense:number;balance:number;categories:Array<{categoryName:string;amount:number}>;}
export const listFinanceAccounts=()=>http.get<unknown,{data:FinanceAccount[]}>('/finance/accounts');
export const saveFinanceAccount=(data:Record<string,unknown>)=>http.post('/finance/accounts',data);
export const listFinanceCategories=()=>http.get<unknown,{data:FinanceCategory[]}>('/finance/categories');
export const saveFinanceCategory=(data:Record<string,unknown>)=>http.post('/finance/categories',data);
export const listFinanceRules=()=>http.get<unknown,{data:FinanceRule[]}>('/finance/rules');
export const saveFinanceRule=(data:Record<string,unknown>)=>http.post('/finance/rules',data);
export const previewFinanceImport=(accountId:number,platform:string,file:File)=>{const data=new FormData();data.append('accountId',String(accountId));data.append('platform',platform);data.append('file',file);return http.post<unknown,{data:ImportPreview}>('/finance/imports/preview',data,{headers:{'Content-Type':'multipart/form-data'},timeout:60000});};
export const confirmFinanceImport=(id:number)=>http.post<unknown,{data:number}>(`/finance/imports/${id}/confirm`);
export const listFinanceImports=()=>http.get<unknown,{data:ImportBatch[]}>('/finance/imports');
export const listFinanceTransactions=(params:Record<string,unknown>={})=>http.get<unknown,{data:FinanceTransaction[]}>('/finance/transactions',{params});
export const categorizeFinanceTransaction=(id:number,categoryId:number)=>http.patch(`/finance/transactions/${id}/category`,null,{params:{categoryId}});
export const getFinanceMonthly=(month:string)=>http.get<unknown,{data:MonthlySummary}>('/finance/stats/monthly',{params:{month}});
