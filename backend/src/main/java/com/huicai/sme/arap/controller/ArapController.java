package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.arap.service.BusinessDocAgingService;
import com.huicai.base.business.dto.BusinessDocQueryDTO;
import com.huicai.base.business.dto.BusinessDocVO;
import com.huicai.base.business.service.BusinessDocService;
import com.huicai.base.masterdata.service.CustomerService;
import com.huicai.base.masterdata.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "往来管理")
@RestController
@RequestMapping("/api/sme/arap/v1")
@RequiredArgsConstructor
public class ArapController {

    private final BusinessDocService docService;
    private final BusinessDocAgingService businessDocAgingService;
    private final CustomerService customerService;
    private final VendorService vendorService;

    @Operation(summary = "应收明细分页查询")
    @GetMapping("/receivables/page")
    public R<IPage<BusinessDocVO>> pageReceivable(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status) {
        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setDocTypes(List.of("INVOICE_OUT", "RECEIPT", "OTHER_RECEIVABLE"));
        if (customerId != null) {
            query.setKeyword(String.valueOf(customerId));
        }
        query.setPeriod(period);
        query.setStatus(status);
        return R.ok(docService.pageQuery(query));
    }

    @Operation(summary = "应付明细分页查询")
    @GetMapping("/payables/page")
    public R<IPage<BusinessDocVO>> pagePayable(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status) {
        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setDocTypes(List.of("INVOICE_IN", "PAYMENT", "EXPENSE", "OTHER_PAYABLE"));
        if (vendorId != null) {
            query.setKeyword(String.valueOf(vendorId));
        }
        query.setPeriod(period);
        query.setStatus(status);
        return R.ok(docService.pageQuery(query));
    }

    @Operation(summary = "获取应收单详情")
    @GetMapping("/receivables/{id}")
    public R<BusinessDocVO> getReceivable(@PathVariable Long id) {
        return R.ok(docService.getDetail(id));
    }

    @Operation(summary = "获取应付单详情")
    @GetMapping("/payables/{id}")
    public R<BusinessDocVO> getPayable(@PathVariable Long id) {
        return R.ok(docService.getDetail(id));
    }

    @Operation(summary = "确认应收")
    @PostMapping("/receivables/{id}/confirm")
    public R<Void> confirmReceivable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "红冲应收")
    @PostMapping("/receivables/{id}/reverse")
    public R<Void> reverseReceivable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "确认应付")
    @PostMapping("/payables/{id}/confirm")
    public R<Void> confirmPayable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "红冲应付")
    @PostMapping("/payables/{id}/reverse")
    public R<Void> reversePayable(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "应收账龄分析")
    @GetMapping("/receivables/aging")
    public R<List<Map<String, Object>>> receivableAging(@RequestParam(required = false) Long customerId) {
        return R.ok(businessDocAgingService.getReceivableAging(customerId));
    }

    @Operation(summary = "应付账龄分析")
    @GetMapping("/payables/aging")
    public R<List<Map<String, Object>>> payableAging(@RequestParam(required = false) Long vendorId) {
        return R.ok(businessDocAgingService.getPayableAging(vendorId));
    }

    @Operation(summary = "逾期应收")
    @GetMapping("/receivables/overdue")
    public R<Void> overdueReceivables() {
        return R.ok();
    }

    // ==================== Excel 导出 ====================

    @Operation(summary = "应收账龄导出")
    @GetMapping("/receivables/aging/export")
    public ResponseEntity<InputStreamResource> exportReceivableAging(@RequestParam(required = false) Long customerId) {
        List<Map<String, Object>> agingData = businessDocAgingService.getReceivableAging(customerId);
        String customerName = "全部";
        if (customerId != null) {
            try {
                var customer = customerService.getById(customerId);
                customerName = customer.getName();
            } catch (Exception ignored) {
                customerName = "客户(" + customerId + ")";
            }
        }
        return buildAgingExcel("应收账龄表", "客户", customerName, agingData, "receivable_aging");
    }

    @Operation(summary = "应付账龄导出")
    @GetMapping("/payables/aging/export")
    public ResponseEntity<InputStreamResource> exportPayableAging(@RequestParam(required = false) Long vendorId) {
        List<Map<String, Object>> agingData = businessDocAgingService.getPayableAging(vendorId);
        String vendorName = "全部";
        if (vendorId != null) {
            try {
                var vendor = vendorService.getById(vendorId);
                vendorName = vendor.getName();
            } catch (Exception ignored) {
                vendorName = "供应商(" + vendorId + ")";
            }
        }
        return buildAgingExcel("应付账龄表", "供应商", vendorName, agingData, "payable_aging");
    }

    private ResponseEntity<InputStreamResource> buildAgingExcel(String title, String partyLabel, String partyName,
                                                                  List<Map<String, Object>> agingData, String filePrefix) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title);

            // 样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);

            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setBorderTop(BorderStyle.THIN);
            amountStyle.setBorderBottom(BorderStyle.THIN);
            amountStyle.setBorderLeft(BorderStyle.THIN);
            amountStyle.setBorderRight(BorderStyle.THIN);
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // 列头
            String[] headers = {partyLabel + "名称", "未结算金额", "1-30天", "31-60天", "61-90天", "90天以上", "合计"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 汇总桶数据
            BigDecimal totalUnsettled = BigDecimal.ZERO;
            BigDecimal bucket0_30 = BigDecimal.ZERO;   // days_0_30
            BigDecimal bucket31_60 = BigDecimal.ZERO;  // days_31_60
            BigDecimal bucket61_90 = BigDecimal.ZERO;  // days_61_90
            BigDecimal bucketOver90 = BigDecimal.ZERO; // days_91_180 + days_181_365 + over_365

            for (Map<String, Object> item : agingData) {
                String bucket = (String) item.get("aging_bucket");
                Object amountObj = item.get("amount");
                BigDecimal amount = BigDecimal.ZERO;
                if (amountObj instanceof Number) {
                    amount = BigDecimal.valueOf(((Number) amountObj).doubleValue());
                }

                totalUnsettled = totalUnsettled.add(amount);

                switch (bucket) {
                    case "days_0_30":
                        bucket0_30 = bucket0_30.add(amount);
                        break;
                    case "days_31_60":
                        bucket31_60 = bucket31_60.add(amount);
                        break;
                    case "days_61_90":
                        bucket61_90 = bucket61_90.add(amount);
                        break;
                    default:
                        // days_91_180, days_181_365, over_365, current 都归入 90天以上
                        bucketOver90 = bucketOver90.add(amount);
                        break;
                }
            }

            BigDecimal totalAging = bucket0_30.add(bucket31_60).add(bucket61_90).add(bucketOver90);

            // 数据行
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(partyName);
            dataRow.getCell(0).setCellStyle(bodyStyle);

            dataRow.createCell(1).setCellValue(totalUnsettled.doubleValue());
            dataRow.getCell(1).setCellStyle(amountStyle);

            dataRow.createCell(2).setCellValue(bucket0_30.doubleValue());
            dataRow.getCell(2).setCellStyle(amountStyle);

            dataRow.createCell(3).setCellValue(bucket31_60.doubleValue());
            dataRow.getCell(3).setCellStyle(amountStyle);

            dataRow.createCell(4).setCellValue(bucket61_90.doubleValue());
            dataRow.getCell(4).setCellStyle(amountStyle);

            dataRow.createCell(5).setCellValue(bucketOver90.doubleValue());
            dataRow.getCell(5).setCellStyle(amountStyle);

            dataRow.createCell(6).setCellValue(totalAging.doubleValue());
            dataRow.getCell(6).setCellStyle(amountStyle);

            // 列宽
            sheet.setColumnWidth(0, 6000);
            for (int i = 1; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String filename = filePrefix + "_" + dateStr + ".xlsx";

            HttpHeaders headersResponse = new HttpHeaders();
            headersResponse.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headersResponse.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

            return ResponseEntity.ok()
                    .headers(headersResponse)
                    .body(new InputStreamResource(new ByteArrayInputStream(baos.toByteArray())));

        } catch (IOException e) {
            throw new RuntimeException("生成账龄导出 Excel 失败", e);
        }
    }
}
