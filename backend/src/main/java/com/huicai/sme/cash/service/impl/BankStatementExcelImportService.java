package com.huicai.sme.cash.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.business.constant.StatementStatus;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.sme.cash.service.BankStatementService;
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
import com.huicai.base.business.util.ColumnMappingResolver;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * SPEC-P127：类级事务。
 *
 * <p>企业隔离开关 {@code app.enterprise_id} 由 {@code TenantGucSetter} 用
 * {@code SET LOCAL} 写入，而 <b>{@code SET LOCAL} 只在事务内有效</b>，
 * 且其 {@code apply()} 首行即 {@code if (!isActualTransactionActive()) return;}。
 * 无事务 ⇒ GUC 恒为 NULL ⇒ RLS 谓词 {@code enterprise_id = NULL} 恒不成立。
 *
 * <p>实测后果（生产实证：{@code t_bank_statement} 恒为 0 行，该功能从未成功导入过）：
 * <ul>
 *   <li>INSERT ⇒ {@code new row violates row-level security policy}</li>
 *   <li>SELECT（{@code previewExcel} 的 {@code countDuplicate}）⇒ <b>静默返 0 行</b>
 *       ⇒ 重复检测结构上必然失效</li>
 * </ul>
 */
@Transactional(rollbackFor = Exception.class)
public class BankStatementExcelImportService {

    /**
     * 取当前制单人 —— 有登录态用真实用户。
     *
     * <p><b>SPEC-P119</b>：P117 把 created_by 修成真实落库列后，本类多条创建
     * 路径此前无人写入该列，导致制审分离校验恒不触发（恒绿门禁形态）。
     *
     * <p><b>Q2 裁定：无登录态返回 null，不写兜底常量。</b>
     * Excel 导入 / 定时任务 / 系统初始化本就没有「录入人」概念，
     * 填 DEFAULT_USER_ID 等于凭空造一个假用户（与 P118 清理的问题同型）。
     * 代价是这些单据永远无法做制审分离校验 —— 属已登记的已知局限，
     * 校验对 created_by IS NULL 放行，与凭证层/业务单据层口径一致。
     */
    private Long currentOperatorId() {
        try {
            return com.huicai.base.system.util.SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

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
                                // D5：BigDecimal.valueOf 走 Double.toString 最短往返，
                                // new BigDecimal(val) 会展开二进制尾巴（0.1 → 0.1000000000000000055…）
                                cellValues.put(ci, BigDecimal.valueOf(val).toPlainString());
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
                        } catch (Exception e) {
                            // SPEC-P127：原为 catch (Exception ignored) {} —— 静默吞异常。
                            // 该查询是重复检测的唯一来源，吞掉异常会让 isDup 恒 false，
                            // 且外面连报错都没有，属「静默失效」典型形态。
                            log.warn("重复检测查询失败，按非重复处理: externalNo={}, error={}",
                                    stmt.getExternalNo(), e.getMessage());
                        }
                    }
                    stmt.setReviewStatus(isDup ? StatementStatus.DUPLICATE : StatementStatus.PENDING);

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
                                // D5：BigDecimal.valueOf 走 Double.toString 最短往返，
                                // new BigDecimal(val) 会展开二进制尾巴（0.1 → 0.1000000000000000055…）
                                cellValues.put(ci, BigDecimal.valueOf(val).toPlainString());
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
                        } catch (Exception e) {
                            // SPEC-P127：原为 catch (Exception ignored) {} —— 静默吞异常。
                            // 该查询是重复检测的唯一来源，吞掉异常会让 isDup 恒 false，
                            // 且外面连报错都没有，属「静默失效」典型形态。
                            log.warn("重复检测查询失败，按非重复处理: externalNo={}, error={}",
                                    stmt.getExternalNo(), e.getMessage());
                        }
                    }
                    stmt.setReviewStatus(isDup ? StatementStatus.DUPLICATE : StatementStatus.PENDING);

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
            // 兜底: 列有值但不匹配关键词时, 默认 INCOME
            if (stmt.getTxType() == null) {
                stmt.setTxType("INCOME"); stmt.setDirection("in");
            }
        } else {
            // 无交易类型列时, 根据金额正负推断
            if (stmt.getAmount() != null && stmt.getAmount().signum() >= 0) {
                stmt.setTxType("INCOME"); stmt.setDirection("in");
            } else if (stmt.getAmount() != null) {
                stmt.setTxType("EXPENSE"); stmt.setDirection("out");
            }
        }

        String counterparty = null;
        Integer payerIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYER_NAME);
        Integer payeeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYEE_NAME);
        // 按方向选取对方名称: INCOME(收款) → 付款人(付钱方); EXPENSE(付款) → 收款人(收钱方)
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

        Integer purposeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PURPOSE);
        if (purposeIdx != null) {
            String purpose = vals.getOrDefault(purposeIdx, "").trim();
            stmt.setPurpose(StrUtil.isNotBlank(purpose) ? purpose : null);
        }

        Integer remarkIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TRANSACTION_REMARK);
        if (remarkIdx != null) {
            String remark = vals.getOrDefault(remarkIdx, "").trim();
            stmt.setTransactionRemark(StrUtil.isNotBlank(remark) ? remark : null);
        }

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

    /**
     * SPEC-P127：方法级事务（D-1 选项 1）。
     *
     * <p>类级已标注，此处仍显式标注的原因：守卫用
     * {@code AnnotatedElementUtils.findMergedAnnotation(Method, ...)} 判定，
     * 该方法<b>不继承</b>类级注解；且「整批原子」是本方法的独立语义
     * （{@code rollbackFor = Exception.class}），显式写出比隐式继承更可读。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmImport(String batchId) {
        if (StrUtil.isBlank(batchId)) throw BusinessException.badRequest("batchId 不能为空");
        // D-3 选项 A：批次「仅成功后消费」—— 先 get 而非 remove。
        // 原实现用 remove，若中途抛异常批次已被消费，用户无法重试且无法排查。
        List<BankStatementEntity> records = batchCache.get(batchId);
        if (records == null) throw BusinessException.notFound("批次不存在或已过期");
        if (records.isEmpty()) return buildImportResult(0, 0, 0, 0, 0, List.of(), batchId);

        int inserted = 0;
        int duplicate = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (BankStatementEntity stmt : records) {
            if (StatementStatus.DUPLICATE.equals(stmt.getReviewStatus())) {
                duplicate++;
                continue;
            }
            stmt.setImportedAt(LocalDateTime.now());
            stmt.setCreatedBy(currentOperatorId());
            // D-2 选项 A：整批原子 —— 不再逐行 catch 吞异常。
            // 原实现 catch 后继续下一行，配合「无事务」会造成「部分行落库 + 报成功」；
            // 且 swallow 掉 RLS 拒绝，症状与「业务重复」无法区分（§4.5 第 48 条：
            // 写路径抛错反而是幸运，静默才是最危险）。
            try {
                statementMapper.insert(stmt);
                inserted++;
            } catch (Exception e) {
                failed++;
                log.error("插入银行对账单失败: txDate={}, amount={}, externalNo={}, error={}",
                        stmt.getTxDate(), stmt.getAmount(), stmt.getExternalNo(), e.getMessage(), e);
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("row", stmt.getExternalNo());
                err.put("message", "插入失败: " + e.getMessage());
                errors.add(err);
                // 抛出使整批回滚；错误信息带定位所需四要素
                throw new BusinessException(String.format(
                        "第 %d 行导入失败（流水号=%s, 日期=%s, 金额=%s）：%s。已整批回滚，未写入任何数据",
                        inserted + duplicate + failed, stmt.getExternalNo(),
                        stmt.getTxDate(), stmt.getAmount(), e.getMessage()));
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

        // 仅在全部成功后才消费批次
        batchCache.remove(batchId);

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