package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.BankStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementExcelImportService {

    private final BankStatementMapper statementMapper;
    private final BankStatementService bankStatementService;
    private final ColumnMappingResolver columnMappingResolver;

    /**
     * 导入银行对账单 Excel (.xlsx)
     * 文件格式: 第1行=查询信息(跳过), 第2行=表头, 第3行起=数据
     */
    @Transactional
    public Map<String, Object> importExcel(Long accountId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw BusinessException.badRequest("仅支持 .xlsx / .xls 文件");
        }

        String batchId = "IMP_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        List<Map<String, Object>> errors = new ArrayList<>();
        List<BankStatementEntity> records = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            ExcelImportListener listener = new ExcelImportListener(accountId, batchId, errors, records, columnMappingResolver);
            EasyExcel.read(is, new HashMap<Integer, String>().getClass(), listener)
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw new BusinessException(500, "读取Excel失败: " + e.getMessage());
        }

        if (records.isEmpty()) {
            return Map.of(
                    "total", 0,
                    "success", 0,
                    "errors", errors,
                    "batchId", batchId,
                    "message", "未解析到有效数据行，请检查表头是否在第2行"
            );
        }

        // 批量插入
        int inserted = 0;
        for (BankStatementEntity stmt : records) {
            try {
                statementMapper.insert(stmt);
                inserted++;
            } catch (Exception e) {
                log.warn("插入对账单记录失败: {}", stmt, e);
                errors.add(Map.of("row", stmt.getExternalNo(), "message", "数据库插入失败: " + e.getMessage()));
            }
        }

        // 自动分类
        int classified = 0;
        for (BankStatementEntity stmt : records) {
            if (stmt.getId() != null) {
                try {
                    bankStatementService.classifySingle(stmt.getId());
                    classified++;
                } catch (Exception e) {
                    log.warn("自动分类失败 statementId={}: {}", stmt.getId(), e.getMessage());
                }
            }
        }

        log.info("Excel对账单导入完成: batchId={}, total={}, inserted={}, classified={}, errors={}",
                batchId, records.size(), inserted, classified, errors.size());

        return Map.of(
                "total", records.size(),
                "success", inserted,
                "classified", classified,
                "errors", errors,
                "batchId", batchId
        );
    }

    /**
     * EasyExcel 行级监听器 — 手动处理表头映射 + 数据行解析
     */
    private static class ExcelImportListener implements ReadListener<Map<Integer, String>> {

        private final Long accountId;
        private final String batchId;
        private final List<Map<String, Object>> errors;
        private final List<BankStatementEntity> records;
        private final ColumnMappingResolver resolver;

        private boolean headerRowProcessed = false;
        private boolean isFirstRow = true;      // 跳过第1行(查询信息)
        private boolean isSecondRow = false;    // 第2行=表头
        private String[] headers;
        private ColumnMappingResolver.MappingResult mapping;
        private int dataRowIndex = 0;

        ExcelImportListener(Long accountId, String batchId,
                            List<Map<String, Object>> errors,
                            List<BankStatementEntity> records,
                            ColumnMappingResolver resolver) {
            this.accountId = accountId;
            this.batchId = batchId;
            this.errors = errors;
            this.records = records;
            this.resolver = resolver;
        }

        @Override
        public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
            int rowIndex = context.readRowHolder().getRowIndex();

            if (isFirstRow) {
                isFirstRow = false;
                isSecondRow = true;
                return; // 跳过第1行
            }

            if (isSecondRow) {
                isSecondRow = false;
                // 第2行是表头
                headers = new String[rowMap.size()];
                for (int i = 0; i < rowMap.size(); i++) {
                    headers[i] = rowMap.getOrDefault(i, "").trim();
                }
                mapping = resolver.resolve(headers);
                if (!mapping.isValid()) {
                    throw new RuntimeException("必含列名缺失(交易日期/金额). 表头: " + String.join(",", headers));
                }
                return;
            }

            // 数据行
            dataRowIndex++;
            try {
                BankStatementEntity stmt = parseRow(rowMap, dataRowIndex);
                if (stmt != null) {
                    records.add(stmt);
                }
            } catch (Exception e) {
                errors.add(Map.of(
                        "row", dataRowIndex + 2,
                        "message", "解析失败: " + e.getMessage()
                ));
            }
        }

        private BankStatementEntity parseRow(Map<Integer, String> rowMap, int rowNum) {
            if (mapping == null) return null;

            BankStatementEntity stmt = new BankStatementEntity();
            stmt.setAccountId(accountId);
            stmt.setMatchStatus("UNMATCHED");
            stmt.setBatchId(batchId);
            stmt.setDirection("in"); // default

            // 原始数据JSON
            Map<String, String> rawMap = new LinkedHashMap<>();
            for (int i = 0; i < (headers != null ? headers.length : 0); i++) {
                rawMap.put(headers[i], rowMap.getOrDefault(i, ""));
            }
            stmt.setRawData(rawMap.toString());

            // --- 交易日期 ---
            Integer dateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE);
            if (dateIdx != null) {
                String dateStr = rowMap.getOrDefault(dateIdx, "").trim();
                if (StrUtil.isNotBlank(dateStr)) {
                    stmt.setTxDate(parseDate(dateStr));
                }
            }
            if (stmt.getTxDate() == null) return null;

            // --- 交易金额 ---
            Integer amtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT);
            if (amtIdx != null) {
                String amtStr = rowMap.getOrDefault(amtIdx, "").trim();
                if (StrUtil.isNotBlank(amtStr)) {
                    stmt.setAmount(new BigDecimal(amtStr.replaceAll("[^0-9.-]", "")));
                }
            }
            if (stmt.getAmount() == null) return null;

            // --- 方向: 来账=INCOME, 往账=EXPENSE ---
            Integer typeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_TYPE);
            if (typeIdx != null) {
                String typeStr = rowMap.getOrDefault(typeIdx, "").trim();
                if (typeStr.contains("来账") || typeStr.contains("收") || typeStr.contains("贷")
                        || typeStr.toLowerCase().contains("in")) {
                    stmt.setTxType("INCOME");
                    stmt.setDirection("in");
                } else if (typeStr.contains("往账") || typeStr.contains("付") || typeStr.contains("借")
                        || typeStr.toLowerCase().contains("out")) {
                    stmt.setTxType("EXPENSE");
                    stmt.setDirection("out");
                }
            }

            // --- 对方户名: 优先付款人名称(来账=收款方视角), 否则收款人名称 ---
            String counterparty = null;
            Integer payerIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYER_NAME);
            Integer payeeIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.PAYEE_NAME);
            if ("INCOME".equals(stmt.getTxType()) && payeeIdx != null) {
                counterparty = rowMap.getOrDefault(payeeIdx, "").trim();
            } else if ("EXPENSE".equals(stmt.getTxType()) && payerIdx != null) {
                counterparty = rowMap.getOrDefault(payerIdx, "").trim();
            }
            if (StrUtil.isBlank(counterparty)) {
                if (payerIdx != null) counterparty = rowMap.getOrDefault(payerIdx, "").trim();
                if (StrUtil.isBlank(counterparty) && payeeIdx != null)
                    counterparty = rowMap.getOrDefault(payeeIdx, "").trim();
            }
            stmt.setCounterAccount(StrUtil.isNotBlank(counterparty) ? counterparty : null);

            // --- 摘要: 三级级联: 摘要→用途→交易附言 ---
            String summary = null;
            Integer summaryIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY);
            if (summaryIdx != null) summary = rowMap.getOrDefault(summaryIdx, "").trim();

            if (StrUtil.isBlank(summary)) {
                // 回退: 用途[Purpose] 或 交易附言[Remark]
                for (int ci = 0; ci < (headers != null ? headers.length : 0); ci++) {
                    String h = headers[ci].toLowerCase();
                    if (h.contains("用途") || h.contains("purpose")) {
                        summary = rowMap.getOrDefault(ci, "").trim();
                        break;
                    }
                }
            }
            if (StrUtil.isBlank(summary)) {
                for (int ci = 0; ci < (headers != null ? headers.length : 0); ci++) {
                    String h = headers[ci].toLowerCase();
                    if (h.contains("交易附言") || h.contains("remark")) {
                        summary = rowMap.getOrDefault(ci, "").trim();
                        break;
                    }
                }
            }
            stmt.setSummary(StrUtil.isNotBlank(summary) ? summary : null);

            // --- 交易流水号 ---
            Integer extIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.EXTERNAL_NO);
            if (extIdx != null) {
                stmt.setExternalNo(rowMap.getOrDefault(extIdx, "").trim());
            }

            return stmt;
        }

        private LocalDate parseDate(String dateStr) {
            if (StrUtil.isBlank(dateStr)) return null;
            String clean = dateStr.trim();
            // yyyyMMdd (如 "20240710")
            if (clean.matches("\\d{8}")) {
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            // yyyy-MM-dd
            if (clean.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(clean);
            }
            // yyyy-MM-dd HH:mm:ss
            if (clean.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDate.parse(clean.substring(0, 10));
            }
            try {
                return LocalDate.parse(clean);
            } catch (DateTimeParseException e) {
                log.warn("无法解析日期: {}", dateStr);
                return null;
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            log.info("Excel解析完成, 共 {} 条数据行", dataRowIndex);
        }
    }
}
