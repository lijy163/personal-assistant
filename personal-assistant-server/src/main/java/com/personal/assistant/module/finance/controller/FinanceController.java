package com.personal.assistant.module.finance.controller;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.common.security.SecurityContextHelper;
import com.personal.assistant.module.finance.dto.*;
import com.personal.assistant.module.finance.entity.*;
import com.personal.assistant.module.finance.service.FinanceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {
    private final FinanceService service;public FinanceController(FinanceService service){this.service=service;}private Long uid(){return SecurityContextHelper.currentUserId();}
    @GetMapping("/accounts") public ApiResponse<List<FinanceAccount>> accounts(){return ApiResponse.success(service.listAccounts(uid()));}
    @PostMapping("/accounts") public ApiResponse<Long> account(@Valid @RequestBody FinanceAccountRequest request){return ApiResponse.success(service.saveAccount(uid(),null,request));}
    @PutMapping("/accounts/{id}") public ApiResponse<Long> account(@PathVariable Long id,@Valid @RequestBody FinanceAccountRequest request){return ApiResponse.success(service.saveAccount(uid(),id,request));}
    @GetMapping("/categories") public ApiResponse<List<FinanceCategory>> categories(){return ApiResponse.success(service.listCategories(uid()));}
    @PostMapping("/categories") public ApiResponse<Long> category(@Valid @RequestBody FinanceCategoryRequest request){return ApiResponse.success(service.saveCategory(uid(),request));}
    @PutMapping("/categories/{id}") public ApiResponse<Long> updateCategory(@PathVariable Long id,@Valid @RequestBody FinanceCategoryRequest request){return ApiResponse.success(service.updateCategory(uid(),id,request));}
    @GetMapping("/rules") public ApiResponse<List<FinanceCategoryRule>> rules(){return ApiResponse.success(service.listRules(uid()));}
    @PostMapping("/rules") public ApiResponse<Long> rule(@Valid @RequestBody FinanceRuleRequest request){return ApiResponse.success(service.saveRule(uid(),request));}
    @PutMapping("/rules/{id}") public ApiResponse<Long> updateRule(@PathVariable Long id,@Valid @RequestBody FinanceRuleRequest request){return ApiResponse.success(service.updateRule(uid(),id,request));}
    @PostMapping(value="/imports/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<FinanceImportPreview> preview(@RequestParam Long accountId,@RequestParam String platform,@RequestPart MultipartFile file)throws IOException{return ApiResponse.success(service.preview(uid(),accountId,platform,file.getOriginalFilename()==null?"bill.csv":file.getOriginalFilename(),file.getBytes()));}
    @GetMapping("/imports") public ApiResponse<List<FinanceImportBatch>> batches(){return ApiResponse.success(service.listBatches(uid()));}
    @GetMapping("/imports/{id}") public ApiResponse<FinanceImportPreview> batch(@PathVariable Long id){return ApiResponse.success(service.batch(uid(),id));}
    @PostMapping("/imports/{id}/confirm") public ApiResponse<Integer> confirm(@PathVariable Long id){return ApiResponse.success(service.confirm(uid(),id));}
    @GetMapping("/transactions") public ApiResponse<List<FinanceTransaction>> transactions(@RequestParam(required=false)String keyword,@RequestParam(required=false)String direction,@RequestParam(required=false)@DateTimeFormat(pattern="yyyy-MM")YearMonth month){return ApiResponse.success(service.listTransactions(uid(),keyword,direction,month));}
    @PatchMapping("/transactions/{id}/category") public ApiResponse<Void> categorize(@PathVariable Long id,@RequestParam Long categoryId){service.categorize(uid(),id,categoryId);return ApiResponse.success();}
    @GetMapping("/stats/monthly") public ApiResponse<FinanceMonthlySummary> monthly(@RequestParam @DateTimeFormat(pattern="yyyy-MM")YearMonth month){return ApiResponse.success(service.monthly(uid(),month));}
    @GetMapping(value="/transactions/export",produces="text/csv") public void export(@RequestParam(required=false)@DateTimeFormat(pattern="yyyy-MM")YearMonth month,HttpServletResponse response)throws IOException{response.setCharacterEncoding(StandardCharsets.UTF_8.name());response.setHeader("Content-Disposition","attachment; filename=finance-transactions.csv");response.getWriter().write("\uFEFF交易时间,收支,金额,商户,摘要\n");for(FinanceTransaction row:service.listTransactions(uid(),null,null,month))response.getWriter().printf("%s,%s,%s,\"%s\",\"%s\"%n",row.getTransactionTime(),row.getDirection(),row.getAmount(),escape(row.getMerchant()),escape(row.getDescription()));}
    private String escape(String value){return value==null?"":value.replace("\"","\"\"");}
}
