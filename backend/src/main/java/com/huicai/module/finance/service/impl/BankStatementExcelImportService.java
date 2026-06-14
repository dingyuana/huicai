package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.BankStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementExcelImportService {

    private final BankStatementMapper statementMapper;
    private final BankStatementService bankStatementService;
    private final ColumnMappingResolver columnMappingResolver;

    private final Map<String, List<BankStatementEntity>> batchCache = new ConcurrentHashMap<>();

    /**
     * 解析 Excel 表头行, 返回所有列名原文.
     */
    public List<String> parseHeaders(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw BusinessException.badRequest("Excel 中没有工作表");

            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) throw BusinessException.badRequest("未找到表头行");

            Row headerRow = sheet.getRow(headerRowIdx);
            int lastCol = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>(lastCol);
            for (int i = 0; i < lastCol; i++) {
                Cell cell = headerRow.getCell(i);
                headers.add(cell != null ? cell.toString().trim() : "");
            }
            workbook.close();
            return headers;
        } catch (IOException e) {
            throw new BusinessException(500, "读取Excel失败: " + e.getMessage());
        }
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            int nonEmpty = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && StrUtil.isNotBlank(cell.toString().trim())) nonEmpty++;
            }
            if (nonEmpty >= 5) return i;
        }
        return -1;
    }

    /**
     * 第一步: 解析 Excel, 不写入数据库.
     * 银行对账单 Excel 格式: 第1行=查询信息(跳过), 第2行=表头, 第3行起=数据.
     */
    public Map<String, Object> previewExcel(Long accountId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件为空");
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")))
            throw BusinessException.badRequest("仅支持 .xlsx / .xls 文件");

        String batchId = "PRE_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> previews = new ArrayList<>();
        List<BankStatementEntity> records = new ArrayList<>();
        List<String> originalHeaders = List.of();

        try (InputStream is = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw BusinessException.badRequest("Excel 中没有工作表");

            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) throw BusinessException.badRequest("未找到表头行");

            Row headerRow = sheet.getRow(headerRowIdx);
            int lastCol = headerRow.getLastCellNum();
            String[] headers = new String[lastCol];
            for (int i = 0; i < lastCol; i++) {
                Cell cell = headerRow.getCell(i);
                headers[i] = (cell != null ? cell.toString().trim() : "");
            }
            originalHeaders = Arrays.asList(headers);

            ColumnMappingResolver.MappingResult mapping = columnMappingResolver.resolve(headers);
            if (!mapping.isValid()) {
                throw BusinessException.badRequest("必含列名缺失(交易日期/金额). 表头: " + String.join(",", headers));
            }

            // 解析数据行
            for (int rowIdx = headerRowIdx + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                Map<Integer, String> cellValues = new HashMap<>();
                for (int ci = 0; ci < lastCol; ci++) {
                    Cell cell = row.getCell(ci);
                    if (cell == null) { cellValues.put(ci, ""); continue; }
                    switch (cell.getCellType()) {
                        case STRING: cellValues.put(ci, cell.getStringCellValue().trim()); break;
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                java.util.Date d = cell.getDateCellValue();
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                                cellValues.put(ci, sdf.format(d));
                            } else {
                                double val = cell.getNumericCellValue();
                                cellValues.put(ci, new BigDecimal(val).toPlainString());
                            }
                            break;
                        case BOOLEAN: cellValues.put(ci, String.valueOf(cell.getBooleanCellValue())); break;
                        default: cellValues.put(ci, cell.toString().trim());
                    }
                }

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("rowIndex", rowIdx + 1);
                p.put("txDate", null);
                p.put("txType", null);
                p.put("direction", null);
                p.put("amount", null);
                p.put("counterAccount", null);
                p.put("summary", null);
                p.put("externalNo", null);
                p.put("isDuplicate", false);
                p.put("isError", false);
                p.put("errorMessage", null);

                try {
                    BankStatementEntity stmt = parseRow(cellValues, mapping, accountId, batchId);
                    if (stmt == null) {
                        String reason = "解析失败: 必含字段缺失(交易日期/金额)";
                        log.warn("预览解析失败 row={}, colValues={}", rowIdx + 1, cellValues);
                        p.put("isError", true);
                        p.put("errorMessage", reason);
                        errors.add(Map.of("row", rowIdx + 1, "message", reason));
                        previews.add(p);
                        continue;
                    }
                    boolean isDup = false;
                    if (StrUtil.isNotBlank(stmt.getExternalNo()) && stmt.getAmount() != null && stmt.getTxDate() != null) {
                        try {
                            isDup = statementMapper.countDuplicate(accountId, stmt.getTxDate(), stmt.getExternalNo(), stmt.getAmount()) > 0;
                        } catch (Exception ignored) {}
                    }
                    stmt.setReviewStatus(isDup ? "DUPLICATE" : "PENDING");

                    records.add(stmt);
                    p.put("txDate", stmt.getTxDate());
                    p.put("txType", stmt.getTxType());
                    p.put("direction", stmt.getDirection());
                    p.put("amount", stmt.getAmount());
                    p.put("counterAccount", stmt.getCounterAccount());
                    p.put("summary", stmt.getSummary());
                    p.put("externalNo", stmt.getExternalNo());
                    p.put("isDuplicate", isDup);
                    previews.add(p);
                } catch (Exception e) {
                    String reason = "解析失败: " + e.getMessage();
                    p.put("isError", true);
                    p.put("errorMessage", reason);
                    java.util.Map<String, Object> errItem = new java.util.LinkedHashMap<>();
                    errItem.put("row", rowIdx + 1);
                    errItem.put("message", reason);
                    errors.add(errItem);
                    previews.add(p);
                }
            }
            workbook.close();
        } catch (IOException e) {
            throw new BusinessException(500, "读取Excel失败: " + e.getMessage());
        }

        batchCache.put(batchId, records);

        int validCount = records.size();
        int totalCount = validCount + errors.size();
        return Map.of(
                "total", totalCount,
                "valid", validCount,
                "errors", errors,
                "batchId", batchId,
                "previews", previews,
                "headers", originalHeaders
        );
    }

    /**
     * 使用用户指定的列映射进行 Excel 预览.
     *
     * @param columnMapping fieldName→headerText 映射, 例如 {"TX_DATE":"交易日期","AMOUNT":"交易金额"}
     */
    public Map<String, Object> previewExcel(Long accountId, MultipartFile file, Map<String, String> columnMapping) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件为空");
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")))
            throw BusinessException.badRequest("仅支持 .xlsx / .xls 文件");

        String batchId = "PRE_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> previews = new ArrayList<>();
        List<BankStatementEntity> records = new ArrayList<>();
        List<String> originalHeaders = List.of();

        try (InputStream is = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw BusinessException.badRequest("Excel 中没有工作表");

            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) throw BusinessException.badRequest("未找到表头行");

            Row headerRow = sheet.getRow(headerRowIdx);
            int lastCol = headerRow.getLastCellNum();
            String[] headers = new String[lastCol];
            for (int i = 0; i < lastCol; i++) {
                Cell cell = headerRow.getCell(i);
                headers[i] = (cell != null ? cell.toString().trim() : "");
            }
            originalHeaders = Arrays.asList(headers);

            ColumnMappingResolver.MappingResult mapping = columnMappingResolver.resolveFromUserMapping(headers, columnMapping);
            if (!mapping.isValid()) {
                throw BusinessException.badRequest("列映射不完整: 缺少交易日期或金额字段");
            }

            for (int rowIdx = headerRowIdx + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                Map<Integer, String> cellValues = new HashMap<>();
                for (int ci = 0; ci < lastCol; ci++) {
                    Cell cell = row.getCell(ci);
                    if (cell == null) { cellValues.put(ci, ""); continue; }
                    switch (cell.getCellType()) {
                        case STRING: cellValues.put(ci, cell.getStringCellValue().trim()); break;
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                java.util.Date d = cell.getDateCellValue();
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                                cellValues.put(ci, sdf.format(d));
                            } else {
                                double val = cell.getNumericCellValue();
                                cellValues.put(ci, new BigDecimal(val).toPlainString());
                            }
                            break;
                        case BOOLEAN: cellValues.put(ci, String.valueOf(cell.getBooleanCellValue())); break;
                        default: cellValues.put(ci, cell.toString().trim());
                    }
                }

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("rowIndex", rowIdx + 1);
                p.put("txDate", null);
                p.put("txType", null);
                p.put("direction", null);
                p.put("amount", null);
                p.put("counterAccount", null);
                p.put("summary", null);
                p.put("externalNo", null);
                p.put("isDuplicate", false);
                p.put("isError", false);
                p.put("errorMessage", null);

                try {
                    BankStatementEntity stmt = parseRow(cellValues, mapping, accountId, batchId);
                    if (stmt == null) {
                        String reason = "解析失败: 必含字段缺失(交易日期/金额)";
                        log.warn("预览解析失败 row={}, colValues={}", rowIdx + 1, cellValues);
                        p.put("isError", true);
                        p.put("errorMessage", reason);
                        errors.add(Map.of("row", rowIdx + 1, "message", reason));
                        previews.add(p);
                        continue;
                    }
                    boolean isDup = false;
                    if (StrUtil.isNotBlank(stmt.getExternalNo()) && stmt.getAmount() != null && stmt.getTxDate() != null) {
                        try {
                            isDup = statementMapper.countDuplicate(accountId, stmt.getTxDate(), stmt.getExternalNo(), stmt.getAmount()) > 0;
                        } catch (Exception ignored) {}
                    }
                    stmt.setReviewStatus(isDup ? "DUPLICATE" : "PENDING");

                    records.add(stmt);
                    p.put("txDate", stmt.getTxDate());
                    p.put("txType", stmt.getTxType());
                    p.put("direction", stmt.getDirection());
                    p.put("amount", stmt.getAmount());
                    p.put("counterAccount", stmt.getCounterAccount());
                    p.put("summary", stmt.getSummary());
                    p.put("externalNo", stmt.getExternalNo());
                    p.put("isDuplicate", isDup);
                    previews.add(p);
                } catch (Exception e) {
                    String reason = "解析失败: " + e.getMessage();
                    p.put("isError", true);
                    p.put("errorMessage", reason);
                    java.util.Map<String, Object> errItem = new java.util.LinkedHashMap<>();
                    errItem.put("row", rowIdx + 1);
                    errItem.put("message", reason);
                    errors.add(errItem);
                    previews.add(p);
                }
            }
            workbook.close();
        } catch (IOException e) {
            throw new BusinessException(500, "读取Excel失败: " + e.getMessage());
        }

        batchCache.put(batchId, records);

        int validCount = records.size();
        int totalCount = validCount + errors.size();
        return Map.of(
                "total", totalCount,
                "valid", validCount,
                "errors", errors,
                "batchId", batchId,
                "previews", previews,
                "headers", originalHeaders
        );
    }

    private BankStatementEntity parseRow(Map<Integer, String> vals, ColumnMappingResolver.MappingResult mapping,
                                          Long accountId, String batchId) {
        BankStatementEntity stmt = new BankStatementEntity();
        stmt.setAccountId(accountId);
        stmt.setMatchStatus("UNMATCHED");
        stmt.setBatchId(batchId);
        stmt.setDirection("in");

        Integer dateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE);
        if (dateIdx != null) {
            String dateStr = vals.getOrDefault(dateIdx, "").trim();
            if (StrUtil.isNotBlank(dateStr)) stmt.setTxDate(parseBankDate(dateStr));
        }
        if (stmt.getTxDate() == null) {
            String rawCell = dateIdx != null ? vals.getOrDefault(dateIdx, "") : "(no mapping)";
            log.warn("parseRow: 日期缺失, colIdx={}, rawCell='{}'", dateIdx, rawCell);
            return null;
        }

        Integer amtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT);
        if (amtIdx != null) {
            String amtStr = vals.getOrDefault(amtIdx, "").trim();
            if (StrUtil.isNotBlank(amtStr))
                stmt.setAmount(new BigDecimal(amtStr.replaceAll("[^0-9.\\-]", "")));
        }
        if (stmt.getAmount() == null) {
            String rawCell = amtIdx != null ? vals.getOrDefault(amtIdx, "") : "(no mapping)";
            log.warn("parseRow: 金额缺失, colIdx={}, rawCell='{}'", amtIdx, rawCell);
            return null;
        }

        Integer typeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_TYPE);
        if (typeIdx != null) {
            String typeStr = vals.getOrDefault(typeIdx, "").trim();
            if (typeStr.contains("来账") || typeStr.contains("收") || typeStr.contains("贷") || typeStr.toLowerCase().contains("in")) {
                stmt.setTxType("INCOME"); stmt.setDirection("in");
            } else if (typeStr.contains("往账") || typeStr.contains("付") || typeStr.contains("借") || typeStr.toLowerCase().contains("out")) {
                stmt.setTxType("EXPENSE"); stmt.setDirection("out");
            }
        }

        String counterparty = null;
        Integer payerIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYER_NAME);
        Integer payeeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYEE_NAME);
        // 按方向选取对方名称: INCOME(收款) → 付款人(付钱给我方的人); EXPENSE(付款) → 收款人(我方付钱给的人)
        boolean isIncoming = "INCOME".equals(stmt.getTxType()) || "in".equals(stmt.getDirection());
        boolean isOutgoing = "EXPENSE".equals(stmt.getTxType()) || "out".equals(stmt.getDirection());
        if (isIncoming && payerIdx != null) {
            counterparty = vals.getOrDefault(payerIdx, "").trim();
        } else if (isOutgoing && payeeIdx != null) {
            counterparty = vals.getOrDefault(payeeIdx, "").trim();
        }
        if (StrUtil.isBlank(counterparty)) {
            if (payerIdx != null) counterparty = vals.getOrDefault(payerIdx, "").trim();
            if (StrUtil.isBlank(counterparty) && payeeIdx != null) counterparty = vals.getOrDefault(payeeIdx, "").trim();
        }
        // fallback: 直接通过 COUNTER_ACCOUNT 列映射识别对方名称
        if (StrUtil.isBlank(counterparty)) {
            Integer counterIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT);
            if (counterIdx != null) counterparty = vals.getOrDefault(counterIdx, "").trim();
        }
        stmt.setCounterAccount(StrUtil.isNotBlank(counterparty) ? counterparty : null);

        String summary = null;
        Integer summaryIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY);
        if (summaryIdx != null) summary = vals.getOrDefault(summaryIdx, "").trim();
        // cascade fallback
        if (StrUtil.isBlank(summary)) {
            for (int ci : vals.keySet()) {
                String h = ""; // not available directly, skip
            }
        }
        stmt.setSummary(StrUtil.isNotBlank(summary) ? summary : null);

        Integer extIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.EXTERNAL_NO);
        if (extIdx != null) stmt.setExternalNo(vals.getOrDefault(extIdx, "").trim());

        return stmt;
    }

    private LocalDate parseBankDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return null;
        String clean = dateStr.trim();
        // yyyyMMdd
        if (clean.matches("\\d{8}"))
            return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd"));
        // yyyy-MM-dd
        if (clean.matches("\\d{4}-\\d{2}-\\d{2}"))
            return LocalDate.parse(clean);
        // yyyy/MM/dd
        if (clean.matches("\\d{4}/\\d{2}/\\d{2}"))
            return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // yyyy.MM.dd
        if (clean.matches("\\d{4}\\.\\d{2}\\.\\d{2}"))
            return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        // yyyy年MM月dd日
        if (clean.matches("\\d{4}年\\d{1,2}月\\d{1,2}日"))
            return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy年M月d日"));
        // yyyyMMdd Chinese suffix (e.g. "20240710   ")
        if (clean.replaceAll("[^0-9]", "").matches("\\d{8}"))
            return LocalDate.parse(clean.replaceAll("[^0-9]", ""), DateTimeFormatter.ofPattern("yyyyMMdd"));
        try { return LocalDate.parse(clean); } catch (DateTimeParseException e) { return null; }
    }

    public Map<String, Object> confirmImport(String batchId) {
        if (StrUtil.isBlank(batchId)) throw BusinessException.badRequest("batchId 不能为空");
        List<BankStatementEntity> records = batchCache.remove(batchId);
        if (records == null) throw BusinessException.notFound("批次不存在或已过期");
        if (records.isEmpty()) return buildImportResult(0, 0, 0, 0, 0, List.of(), batchId);

        int inserted = 0;
        int duplicate = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (BankStatementEntity stmt : records) {
            if ("DUPLICATE".equals(stmt.getReviewStatus())) {
                duplicate++;
                continue;
            }
            try {
                stmt.setImportedAt(LocalDateTime.now());
                statementMapper.insert(stmt);
                inserted++;
            } catch (Exception e) {
                failed++;
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("row", stmt.getExternalNo());
                err.put("message", "插入失败: " + e.getMessage());
                errors.add(err);
            }
        }

        int classified = 0;
        for (BankStatementEntity stmt : records) {
            if (stmt.getId() != null) {
                try {
                    bankStatementService.classifySingle(stmt.getId());
                    classified++;
                } catch (Exception e) {
                    log.warn("分类失败 statementId={}: {}", stmt.getId(), e.getMessage());
                }
            }
        }

        return buildImportResult(records.size(), inserted, duplicate, failed, classified, errors, batchId);
    }

    private Map<String, Object> buildImportResult(int total, int success, int duplicate, int failed,
                                                   int classified, List<Map<String, Object>> errors, String batchId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("duplicate", duplicate);
        result.put("failed", failed);
        result.put("classified", classified);
        result.put("errors", errors);
        result.put("batchId", batchId);
        result.put("message", String.format("共解析 %d 条, 成功 %d 条, 重复跳过 %d 条, 失败 %d 条, 已分类 %d 条",
                total, success, duplicate, failed, classified));
        return result;
    }
}