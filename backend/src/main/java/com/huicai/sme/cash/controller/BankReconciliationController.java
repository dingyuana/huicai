package com.huicai.sme.cash.controller;

import com.huicai.common.response.R;
import com.huicai.sme.cash.service.BankReconciliationService;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "银行对账")
@RestController
@RequestMapping("/api/sme/cash/v1/bank-reconciliation")
@RequiredArgsConstructor
public class BankReconciliationController {

    private final BankReconciliationService service;

    @Operation(summary = "余额调节表")
    @GetMapping("/adjustment")
    public R<Map<String, Object>> adjustment(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.generateAdjustment(accountId, period));
    }

    @Operation(summary = "导出余额调节表 Excel")
    @GetMapping("/adjustment/export")
    public ResponseEntity<InputStreamResource> exportAdjustment(
            @RequestParam Long accountId, @RequestParam String period) throws Exception {
        Map<String, Object> data = service.generateAdjustment(accountId, period);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("余额调节表");

            // 样式
            CellStyle labelStyle = wb.createCellStyle();
            labelStyle.setAlignment(HorizontalAlignment.RIGHT);
            labelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Font labelFont = wb.createFont();
            labelFont.setBold(true);
            labelFont.setFontHeightInPoints((short) 12);
            labelStyle.setFont(labelFont);

            CellStyle valueStyle = wb.createCellStyle();
            valueStyle.setAlignment(HorizontalAlignment.LEFT);
            valueStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Font valueFont = wb.createFont();
            valueFont.setFontHeightInPoints((short) 12);
            valueStyle.setFont(valueFont);

            // 列宽
            sheet.setColumnWidth(0, 30 * 256);
            sheet.setColumnWidth(1, 25 * 256);

            int rowIdx = 0;

            // 行1: 企业账面余额
            rowIdx = writeRow(sheet, rowIdx, "企业账面余额", getBigDecimal(data, "enterpriseBalance"), labelStyle, valueStyle);
            // 行2: 加: 企业已收银行未收
            rowIdx = writeRow(sheet, rowIdx, "加: 企业已收银行未收", getBigDecimal(data, "enterpriseReceipts"), labelStyle, valueStyle);
            // 行3: 减: 企业已付银行未付
            rowIdx = writeRow(sheet, rowIdx, "减: 企业已付银行未付", getBigDecimal(data, "enterprisePayments"), labelStyle, valueStyle);
            // 行4: 调整后企业余额
            rowIdx = writeRow(sheet, rowIdx, "调整后企业余额", getBigDecimal(data, "adjustedEnterpriseBalance"), labelStyle, valueStyle);
            // 空行
            rowIdx++;
            // 行6: 银行对账单余额
            rowIdx = writeRow(sheet, rowIdx, "银行对账单余额", getBigDecimal(data, "bankBalance"), labelStyle, valueStyle);
            // 行7: 加: 银行已收企业未收
            rowIdx = writeRow(sheet, rowIdx, "加: 银行已收企业未收", getBigDecimal(data, "bankReceipts"), labelStyle, valueStyle);
            // 行8: 减: 银行已付企业未付
            rowIdx = writeRow(sheet, rowIdx, "减: 银行已付企业未付", getBigDecimal(data, "bankPayments"), labelStyle, valueStyle);
            // 行9: 调整后银行余额
            writeRow(sheet, rowIdx, "调整后银行余额", getBigDecimal(data, "adjustedBankBalance"), labelStyle, valueStyle);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);

            String filename = "adjustment_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(new java.io.ByteArrayInputStream(baos.toByteArray())));
        }
    }

    private int writeRow(Sheet sheet, int rowIdx, String label, BigDecimal value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        if (value != null) {
            valueCell.setCellValue(value.doubleValue());
        }
        valueCell.setCellStyle(valueStyle);
        return rowIdx + 1;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        return BigDecimal.ZERO;
    }

    @Operation(summary = "对账汇总 (含 PENDING_CONFIRM 统计)")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.summarize(accountId, period));
    }

    @Operation(summary = "未达账项 (4方向分类: BANK_RECEIPT/PAYMENT_ENTERPRISE_NOT, ENTERPRISE_RECEIPT/PAYMENT_BANK_NOT)")
    @GetMapping("/unmatched")
    public R<List<Map<String, Object>>> unmatched(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.unmatchedItems(accountId, period));
    }

    // ─── P4.1: 5维评分 ───

    @Operation(summary = "单笔 5 维评分 (金额/日期/名称/摘要/参考号)")
    @GetMapping("/score")
    public R<BankReconciliationService.ScoreResult> score(
            @RequestParam Long accountId,
            @RequestParam Long statementId,
            @RequestParam Long journalId) {
        return R.ok(service.calculateScore(accountId, statementId, journalId));
    }

    // ─── P4.2: 评分路由 ───

    @Operation(summary = "批量自动匹配 (≥85 自动 MATCHED, 60-84 PENDING_CONFIRM, <60 UNMATCHED)")
    @PostMapping("/run-matching")
    public R<List<BankReconciliationService.MatchResult>> runMatching(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.runMatching(accountId, period));
    }

    // ─── P4.4: 对账锁定 ───

    @Operation(summary = "获取对账锁")
    @PostMapping("/lock")
    public R<Boolean> lock(
            @RequestParam Long accountId,
            @RequestParam String period,
            @RequestParam String operator,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        return R.ok(service.lockReconciliation(accountId, period, operator, ttlSeconds));
    }

    @Operation(summary = "释放对账锁")
    @PostMapping("/unlock")
    public R<Void> unlock(
            @RequestParam Long accountId,
            @RequestParam String period,
            @RequestParam String operator) {
        service.unlockReconciliation(accountId, period, operator);
        return R.ok();
    }

    @Operation(summary = "P14-1 人工确认匹配 (PENDING_CONFIRM → MATCHED)")
    @PostMapping("/confirm")
    public R<BankReconciliationService.ConfirmResult> confirm(
            @RequestParam Long statementId,
            @RequestParam Long journalId,
            @RequestParam(required = false) String operator) {
        return R.ok(service.confirmMatch(statementId, journalId, operator == null ? "system" : operator));
    }

    @Operation(summary = "P14-1 人工驳回匹配 (PENDING_CONFIRM → UNMATCHED)")
    @PostMapping("/reject")
    public R<BankReconciliationService.ConfirmResult> reject(
            @RequestParam Long statementId,
            @RequestParam Long journalId,
            @RequestParam(required = false) String operator) {
        return R.ok(service.rejectMatch(statementId, journalId, operator == null ? "system" : operator));
    }
}
