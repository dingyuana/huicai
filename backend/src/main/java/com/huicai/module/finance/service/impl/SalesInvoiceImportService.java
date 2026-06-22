package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesInvoiceImportService {

    private static final long DEFAULT_USER_ID = 1L;
    private static final long DEFAULT_VOUCHER_TYPE_ID = 1L;

    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final CustomerMapper customerMapper;
    private final SubjectMapper subjectMapper;
    private final OutputInvoiceMapper outputInvoiceMapper;
    private final ReceivableMapper receivableMapper;
    private final ColumnMappingResolver columnMappingResolver;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, List<ParsedInvoiceRow>> batchCache = new ConcurrentHashMap<>();

    public Map<String, Object> previewInvoices(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }

        String batchId = "PRE_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ParsedInvoiceRow> allRows = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is);
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw BusinessException.badRequest("Excel 中没有工作表");
            }

            org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.badRequest("Excel 中没有表头行");
            }

            int lastCol = headerRow.getLastCellNum();
            String[] headers = new String[lastCol];
            for (int i = 0; i < lastCol; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.getCell(i);
                headers[i] = (cell != null ? cell.toString().trim() : "");
            }

            ColumnMappingResolver.MappingResult mapping = columnMappingResolver.resolve(headers);
            if (!mapping.isValid()) {
                throw BusinessException.badRequest("必含列名缺失(发票日期/金额). 表头: " + String.join(",", headers));
            }

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                Map<Integer, String> rowMap = new HashMap<>();
                for (int ci = 0; ci < lastCol; ci++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(ci);
                    if (cell == null) {
                        rowMap.put(ci, "");
                    } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        java.util.Date dateVal = cell.getDateCellValue();
                        if (dateVal != null) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            rowMap.put(ci, sdf.format(dateVal));
                        } else {
                            rowMap.put(ci, "");
                        }
                    } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        double numVal = cell.getNumericCellValue();
                        long longVal = (long) numVal;
                        rowMap.put(ci, numVal == longVal ? String.valueOf(longVal) : String.valueOf(numVal));
                    } else {
                        rowMap.put(ci, cell.toString().trim());
                    }
                }

                try {
                    ParsedInvoiceRow parsed = parseInvoiceRow(rowMap, mapping, rowIdx + 1);
                    if (parsed != null) {
                        allRows.add(parsed);
                    }
                } catch (Exception e) {
                    errors.add(Map.of("row", rowIdx + 1, "message", "解析失败: " + e.getMessage()));
                }
            }

            workbook.close();
        } catch (IOException e) {
            throw new BusinessException(500, "读取Excel失败: " + e.getMessage());
        }

        batchCache.put(batchId, allRows);

        List<Map<String, Object>> previews = new ArrayList<>();
        for (int i = 0; i < allRows.size(); i++) {
            ParsedInvoiceRow r = allRows.get(i);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("rowIndex", i + 1);
            p.put("invoiceNo", r.invoiceNo);
            p.put("buyerTaxId", r.buyerTaxId);
            p.put("buyerName", r.buyerName);
            p.put("invoiceDate", r.invoiceDate != null ? r.invoiceDate.toString() : null);
            p.put("goodsName", r.goodsName);
            p.put("amount", r.amount);
            p.put("taxAmount", r.taxAmount);
            p.put("totalAmount", r.totalAmount);
            p.put("isPositive", r.isPositive);
            previews.add(p);
        }

        Set<String> existingSet = findExistingInvoiceNos(allRows);

        log.info("销售发票预览完成: batchId={}, total={}, errors={}, existing={}",
                batchId, allRows.size(), errors.size(), existingSet.size());

        for (Map<String, Object> p : previews) {
            String invNo = (String) p.get("invoiceNo");
            if (invNo != null && existingSet.contains(invNo)) {
                p.put("existing", true);
            }
        }

        return Map.of(
                "total", allRows.size(),
                "valid", allRows.size() - errors.size(),
                "errors", errors,
                "batchId", batchId,
                "previews", previews,
                "existing", existingSet.size(),
                "existingList", new ArrayList<>(existingSet)
        );
    }

    private ParsedInvoiceRow parseInvoiceRow(Map<Integer, String> rowMap, ColumnMappingResolver.MappingResult mapping, int rowNum) {
        ParsedInvoiceRow row = new ParsedInvoiceRow();
        row.rowNum = rowNum;

        Integer invIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.INVOICE_NO);
        if (invIdx != null) {
            row.invoiceNo = rowMap.getOrDefault(invIdx, "").trim();
            // 如果发票号码为空(匹配到空列如"发票代码"), 尝试回退查找其他可能列
            if (StrUtil.isBlank(row.invoiceNo)) {
                // 检查第3列(数电发票号码)和已知有值的列
                for (int ci : new int[]{3, 2, 0}) {
                    String val = rowMap.getOrDefault(ci, "").trim();
                    if (StrUtil.isNotBlank(val) && val.matches("\\d{8,}")) {
                        row.invoiceNo = val;
                        break;
                    }
                }
            }
        }

        Integer sellerTaxIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SELLER_TAX_ID);
        if (sellerTaxIdx != null) row.sellerTaxId = rowMap.getOrDefault(sellerTaxIdx, "").trim();

        Integer sellerNameIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SELLER_NAME);
        if (sellerNameIdx != null) row.sellerName = rowMap.getOrDefault(sellerNameIdx, "").trim();

        Integer buyerTaxIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.BUYER_TAX_ID);
        if (buyerTaxIdx != null) row.buyerTaxId = rowMap.getOrDefault(buyerTaxIdx, "").trim();

        Integer buyerNameIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.BUYER_NAME);
        if (buyerNameIdx != null) row.buyerName = rowMap.getOrDefault(buyerNameIdx, "").trim();

        Integer dateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE);
        if (dateIdx != null) {
            String dateStr = rowMap.getOrDefault(dateIdx, "").trim();
            row.invoiceDate = parseInvoiceDate(dateStr);
        }
        if (row.invoiceDate == null) throw new RuntimeException("缺少有效开票日期");

        Integer goodsIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.GOODS_NAME);
        if (goodsIdx != null) row.goodsName = rowMap.getOrDefault(goodsIdx, "").trim();

        Integer amtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT);
        if (amtIdx != null) {
            String amtStr = rowMap.getOrDefault(amtIdx, "").trim();
            if (StrUtil.isNotBlank(amtStr))
                row.amount = new BigDecimal(amtStr.replaceAll("[^0-9.-]", ""));
        }
        if (row.amount == null) throw new RuntimeException("缺少金额");

        Integer taxAmtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TAX_AMOUNT);
        if (taxAmtIdx != null) {
            String taxStr = rowMap.getOrDefault(taxAmtIdx, "").trim();
            if (StrUtil.isNotBlank(taxStr))
                row.taxAmount = new BigDecimal(taxStr.replaceAll("[^0-9.-]", ""));
        }
        if (row.taxAmount == null) row.taxAmount = BigDecimal.ZERO;

        Integer rateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TAX_RATE);
        if (rateIdx != null) {
            String rateStr = rowMap.getOrDefault(rateIdx, "").trim();
            if (StrUtil.isNotBlank(rateStr)) {
                try { row.taxRate = new BigDecimal(rateStr.replace("%", "").trim()); } catch (Exception ignored) {}
            }
        }

        Integer totalIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TOTAL_AMOUNT);
        if (totalIdx != null) {
            String totalStr = rowMap.getOrDefault(totalIdx, "").trim();
            if (StrUtil.isNotBlank(totalStr))
                row.totalAmount = new BigDecimal(totalStr.replaceAll("[^0-9.-]", ""));
        }
        if (row.totalAmount == null) {
            row.totalAmount = row.amount.add(row.taxAmount);
        }

        Integer positiveIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.IS_POSITIVE);
        if (positiveIdx != null) {
            String posStr = rowMap.getOrDefault(positiveIdx, "").trim();
            row.isPositive = posStr.contains("是") || posStr.contains("正数") || posStr.toLowerCase().contains("y");
        } else {
            row.isPositive = true;
        }

        // 解析备注/摘要字段（用于红冲关联）
        Integer summaryIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY);
        if (summaryIdx != null) {
            row.remark = rowMap.getOrDefault(summaryIdx, "").trim();
        } else {
            // 退而求其次用商品名称当备注
            Integer goodsIdx2 = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.GOODS_NAME);
            if (goodsIdx2 != null) {
                row.remark = rowMap.getOrDefault(goodsIdx2, "").trim();
            }
        }

        // 红冲发票提取原始发票号
        if (!row.isPositive && StrUtil.isNotBlank(row.remark)) {
            row.originalInvoiceNo = extractOriginalInvoiceNo(row.remark);
        }

        return row;
    }

    /**
     * 从备注文本中提取被红冲的原始发票号.
     * 支持格式: "被红冲蓝字发票号码：12345678" / "红冲自 INV-2026-001" /
     *           "原发票号: 12345678" / 直接包含发票号模式
     */
    private String extractOriginalInvoiceNo(String remark) {
        if (StrUtil.isBlank(remark)) return null;
        // 匹配 "发票号码[：:]\s*(\S+)" 或 "号码[：:]\s*(\S+)" 或 "发票号[：:]\s*(\S+)"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:发票号码|发票号|号码|原发票)[：:]\\s*(\\S+)");
        java.util.regex.Matcher m = p.matcher(remark);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 匹配 "红冲自\s*(\S+)"
        p = java.util.regex.Pattern.compile("红冲自\\s*(\\S+)");
        m = p.matcher(remark);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private LocalDate parseInvoiceDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return null;
        String clean = dateStr.trim();
        if (clean.matches("\\d{8}"))
            return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (clean.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"))
            return LocalDate.parse(clean.substring(0, 10));
        if (clean.matches("\\d{4}-\\d{2}-\\d{2}"))
            return LocalDate.parse(clean);
        try { return LocalDate.parse(clean); } catch (DateTimeParseException e) { return null; }
    }

    public Map<String, Object> confirmImport(String batchId) {
        if (StrUtil.isBlank(batchId)) {
            throw BusinessException.badRequest("batchId 不能为空");
        }
        List<ParsedInvoiceRow> rows = batchCache.remove(batchId);
        if (rows == null) {
            throw BusinessException.notFound("批次不存在或已过期, 请重新上传文件");
        }

        if (rows.isEmpty()) {
            return Map.of("total", 0, "success", 0, "docCreated", 0, "voucherCreated", 0, "batchId", batchId);
        }

        ensureStandardSubjects();

        Set<String> existingSet = findExistingInvoiceNos(rows);

        int success = 0, docCreated = 0, voucherCreated = 0, duplicateSkipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (ParsedInvoiceRow row : rows) {
            try {
                if (StrUtil.isNotBlank(row.invoiceNo) && existingSet.contains(row.invoiceNo)) {
                    duplicateSkipped++;
                    java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                    err.put("row", row.rowNum);
                    err.put("invoiceNo", row.invoiceNo);
                    err.put("message", "发票号已存在, 已跳过");
                    errors.add(err);
                    continue;
                }
                Long customerId = matchOrCreateCustomer(row);
                if (customerId == null) {
                    errors.add(Map.of("row", row.rowNum, "invoiceNo", row.invoiceNo, "message", "客户匹配失败"));
                    continue;
                }
                String period = row.invoiceDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
                BusinessDocEntity doc = createBusinessDoc(row, customerId, period, batchId);
                createVoucher(doc, row, customerId, period);
                insertOutputInvoice(row, customerId, period, doc);
                createReceivableFromInvoice(doc, row, customerId, period);
                // 红冲发票: 找到原蓝字发票标记为 REVERSED
                if (!row.isPositive && StrUtil.isNotBlank(row.originalInvoiceNo)) {
                    handleRedFlushReversal(row.originalInvoiceNo, doc, period);
                }
                success++; docCreated++; voucherCreated++;
            } catch (Exception e) {
                log.warn("处理发票行失败 row={}: {}", row.rowNum, e.getMessage());
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("row", row.rowNum);
                err.put("invoiceNo", row.invoiceNo);
                err.put("message", e.getMessage());
                errors.add(err);
            }
        }

        // 后处理：已存在的红冲发票也尝试关联（去重跳过的行）
        for (ParsedInvoiceRow row : rows) {
            if (!row.isPositive && StrUtil.isNotBlank(row.originalInvoiceNo)
                    && StrUtil.isNotBlank(row.invoiceNo)
                    && existingSet.contains(row.invoiceNo)) {
                try {
                    handleRedFlushReversal(row.originalInvoiceNo, null, row.invoiceDate.format(DateTimeFormatter.ofPattern("yyyyMM")));
                } catch (Exception e) {
                    log.warn("红冲后处理失败 row={}: {}", row.rowNum, e.getMessage());
                }
            }
        }

        // 后处理2：没有originalInvoiceNo的红冲发票，按金额+客户匹配蓝字
        for (ParsedInvoiceRow row : rows) {
            if (!row.isPositive && StrUtil.isBlank(row.originalInvoiceNo)
                    && StrUtil.isNotBlank(row.invoiceNo)
                    && existingSet.contains(row.invoiceNo)) {
                try {
                    BigDecimal absAmount = row.amount.abs();
                    String period = row.invoiceDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
                    List<OutputInvoiceEntity> candidates = outputInvoiceMapper.selectList(
                            new LambdaQueryWrapper<OutputInvoiceEntity>()
                                    .eq(OutputInvoiceEntity::getAmount, absAmount)
                                    .eq(OutputInvoiceEntity::getCustomerName, row.buyerName)
                                    .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.REVERSED)
                                    .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.VOIDED)
                                    .last("LIMIT 1"));
                    if (!candidates.isEmpty()) {
                        OutputInvoiceEntity blue = candidates.get(0);
                        handleRedFlushReversal(blue.getInvoiceNo(), null, period);
                        log.info("红冲金额匹配: redInvoice={}, blueInvoice={}, amount={}",
                                row.invoiceNo, blue.getInvoiceNo(), absAmount);
                    } else {
                        log.info("红冲金额匹配未找到: invoiceNo={}, amount={}, buyer={}",
                                row.invoiceNo, absAmount, row.buyerName);
                    }
                } catch (Exception e) {
                    log.warn("红冲金额匹配后处理失败 row={}: {}", row.rowNum, e.getMessage());
                }
            }
        }

        return Map.of(
                "total", rows.size(), "success", success,
                "docCreated", docCreated, "voucherCreated", voucherCreated,
                "duplicateSkipped", duplicateSkipped,
                "errors", errors, "batchId", batchId
        );
    }

    public Map<String, Object> importInvoices(MultipartFile file) {
        var preview = previewInvoices(file);
        String batchId = (String) preview.get("batchId");
        var result = confirmImport(batchId);
        result.put("total", preview.get("total"));
        return result;
    }

    @Transactional
    protected BusinessDocEntity createBusinessDoc(ParsedInvoiceRow row, Long customerId, String period, String batchId) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateInvoiceDocNo(period));
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(row.invoiceDate);
        doc.setPeriod(period);
        doc.setAmount(row.totalAmount);
        doc.setCustomerId(customerId);
        doc.setSummary(row.goodsName);
        doc.setStatus("DRAFT");
        doc.setSource("INVOICE_IMPORT");
        doc.setCreatedBy(DEFAULT_USER_ID);
        docMapper.insert(doc);
        return doc;
    }

    @Transactional
    protected void createVoucher(BusinessDocEntity doc, ParsedInvoiceRow row, Long customerId, String period) {
        String voucherNo = voucherNoService.generateNextNo(period, DEFAULT_VOUCHER_TYPE_ID);
        Subject subjectBank = findSubjectByCode("1122");
        Subject subjectRevenue = findSubjectByCode("5001");
        Subject subjectOutputTax = findSubjectByCode("2221.01");
        if (subjectBank == null || subjectRevenue == null) {
            throw new BusinessException(500, "缺少基础科目配置(1122/5001)");
        }

        BigDecimal exclTaxAmount = row.amount;
        BigDecimal taxAmount = row.taxAmount;
        BigDecimal totalAmount = row.totalAmount;
        if (!row.isPositive) {
            exclTaxAmount = exclTaxAmount.negate();
            taxAmount = taxAmount.negate();
            totalAmount = totalAmount.negate();
        }

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(DEFAULT_VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("发票导入: " + row.invoiceNo + " " + row.goodsName);
        voucher.setTotalDebit(totalAmount);
        voucher.setTotalCredit(totalAmount);
        voucher.setCreatedBy(DEFAULT_USER_ID);
        voucherMapper.insert(voucher);

        int sort = 1;
        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(subjectBank.getId());
        entryDr.setDebit(totalAmount);
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(row.goodsName);
        entryDr.setSortOrder(sort++);
        voucherEntryMapper.insert(entryDr);

        VoucherEntryEntity entryCr1 = new VoucherEntryEntity();
        entryCr1.setVoucherId(voucher.getId());
        entryCr1.setSubjectId(subjectRevenue.getId());
        entryCr1.setDebit(BigDecimal.ZERO);
        entryCr1.setCredit(exclTaxAmount);
        entryCr1.setSummary(row.goodsName);
        entryCr1.setSortOrder(sort++);
        voucherEntryMapper.insert(entryCr1);

        if (subjectOutputTax != null && taxAmount.compareTo(BigDecimal.ZERO) != 0) {
            VoucherEntryEntity entryCr2 = new VoucherEntryEntity();
            entryCr2.setVoucherId(voucher.getId());
            entryCr2.setSubjectId(subjectOutputTax.getId());
            entryCr2.setDebit(BigDecimal.ZERO);
            entryCr2.setCredit(taxAmount);
            entryCr2.setSummary(row.goodsName);
            entryCr2.setSortOrder(sort++);
            voucherEntryMapper.insert(entryCr2);
        }

        doc.setVoucherId(voucher.getId());
        doc.setStatus("VOUCHERED");
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);

        log.info("发票导入生成凭证: invoiceNo={}, voucherId={}", row.invoiceNo, voucher.getId());
    }

    void ensureStandardSubjects() {
        ensureSubject("1122", "应收账款", 1, "debit", null);
        ensureSubject("5001", "主营业务收入", 1, "credit", null);
        ensureSubject("2221", "应交税费", 1, "credit", null);
        ensureSubject("2221.01", "应交增值税-销项税额", 2, "credit", "2221");
    }

    private void ensureSubject(String code, String name, int level, String direction, String parentCode) {
        if (findSubjectByCode(code) != null) return;
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setLevel(level);
        s.setDirection(direction);
        s.setIsLeaf(true);
        s.setIsActive(true);
        if (parentCode != null) {
            Subject parent = findSubjectByCode(parentCode);
            if (parent != null) {
                s.setParentId(parent.getId());
                if (Boolean.TRUE.equals(parent.getIsLeaf())) {
                    parent.setIsLeaf(false);
                    subjectMapper.updateById(parent);
                }
            }
        }
        subjectMapper.insert(s);
        log.info("自动创建科目: {} {}", code, name);
    }

    private void insertOutputInvoice(ParsedInvoiceRow row, Long customerId, String period, BusinessDocEntity doc) {
        OutputInvoiceEntity inv = new OutputInvoiceEntity();
        inv.setInvoiceNo(row.invoiceNo);
        inv.setInvoiceDate(row.invoiceDate);
        inv.setPeriod(period);
        inv.setCustomerId(customerId);
        inv.setCustomerName(row.buyerName);
        inv.setAmount(row.amount);
        if (row.taxRate != null) {
            inv.setTaxRate(row.taxRate);
        } else if (row.amount.compareTo(BigDecimal.ZERO) != 0) {
            inv.setTaxRate(row.taxAmount.divide(row.amount, 4, java.math.RoundingMode.HALF_UP));
        } else {
            inv.setTaxRate(BigDecimal.ZERO);
        }
        inv.setTaxAmount(row.taxAmount);
        inv.setTotalAmount(row.totalAmount);
        inv.setInvoiceType("SPECIAL");
        inv.setStatus(InvoiceStatus.PENDING_CONFIRM);
        inv.setDocId(doc.getId());
        inv.setVoucherId(doc.getVoucherId());
        inv.setRemark(row.remark);
        inv.setCreatedBy(DEFAULT_USER_ID);
        inv.setUpdatedAt(LocalDateTime.now());
        try {
            outputInvoiceMapper.insert(inv);
        } catch (Exception e) {
            log.error("写入销项发票失败: invoiceNo={}", row.invoiceNo, e);
            throw new RuntimeException("写入销项发票失败: " + e.getMessage(), e);
        }
        log.debug("写入销项发票: invoiceNo={}", row.invoiceNo);
    }

    Long matchOrCreateCustomer(ParsedInvoiceRow row) {
        if (StrUtil.isBlank(row.buyerTaxId) && StrUtil.isBlank(row.buyerName)) return null;
        if (StrUtil.isNotBlank(row.buyerTaxId)) {
            List<CustomerEntity> byTax = customerMapper.selectList(
                    new LambdaQueryWrapper<CustomerEntity>().eq(CustomerEntity::getTaxNo, row.buyerTaxId).last("LIMIT 1"));
            if (!byTax.isEmpty()) return byTax.get(0).getId();
        }
        if (StrUtil.isNotBlank(row.buyerName)) {
            List<CustomerEntity> byName = customerMapper.selectList(
                    new LambdaQueryWrapper<CustomerEntity>().like(CustomerEntity::getName, row.buyerName).last("LIMIT 1"));
            if (!byName.isEmpty()) return byName.get(0).getId();
            String shortName = row.buyerName.replaceAll("[（(].*[）)]", "").trim();
            if (!shortName.equals(row.buyerName) && shortName.length() >= 4) {
                List<CustomerEntity> byShort = customerMapper.selectList(
                        new LambdaQueryWrapper<CustomerEntity>().like(CustomerEntity::getName, shortName).last("LIMIT 1"));
                if (!byShort.isEmpty()) return byShort.get(0).getId();
            }
        }
        // 只有名称(无税号)也能自动创建客户
        if (StrUtil.isNotBlank(row.buyerName)) {
            CustomerEntity newCustomer = new CustomerEntity();
            newCustomer.setName(row.buyerName);
            newCustomer.setTaxNo(StrUtil.isNotBlank(row.buyerTaxId) ? row.buyerTaxId : null);
            newCustomer.setCode("AUTO-" + System.currentTimeMillis());
            newCustomer.setIsActive(true);
            newCustomer.setRemark("发票导入自动创建");
            customerMapper.insert(newCustomer);
            log.info("自动创建客户: name={}, taxNo={}", row.buyerName, row.buyerTaxId);
            return newCustomer.getId();
        }
        return null;
    }

    private String generateInvoiceDocNo(String period) {
        String key = "doc:no:INVOICE_OUT:" + period;
        Long seq = redisTemplate.opsForValue().increment(key);
        return "FPS" + period + String.format("%04d", seq);
    }

    private Subject findSubjectByCode(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 批量查询哪些发票号已存在于 t_output_invoice
     */
    Set<String> findExistingInvoiceNos(List<ParsedInvoiceRow> rows) {
        Set<String> invoiceNos = new HashSet<>();
        for (ParsedInvoiceRow row : rows) {
            if (StrUtil.isNotBlank(row.invoiceNo)) {
                invoiceNos.add(row.invoiceNo);
            }
        }
        if (invoiceNos.isEmpty()) return Collections.emptySet();
        List<OutputInvoiceEntity> existing = outputInvoiceMapper.selectList(
                new LambdaQueryWrapper<OutputInvoiceEntity>()
                        .in(OutputInvoiceEntity::getInvoiceNo, invoiceNos));
        Set<String> result = new HashSet<>();
        for (OutputInvoiceEntity inv : existing) {
            if (inv.getInvoiceNo() != null) result.add(inv.getInvoiceNo());
        }
        return result;
    }

    /**
     * P10-1: 销售发票导入成功后，自动生成应收单 t_receivable.
     * unsettled_amount 初始 = 总额（待收款），settled = 0.
     */
    @Transactional
    void createReceivableFromInvoice(BusinessDocEntity doc, ParsedInvoiceRow row,
                                     Long customerId, String period) {
        ReceivableEntity recv = new ReceivableEntity();
        recv.setCustomerId(customerId);
        recv.setDocId(doc.getId());
        recv.setVoucherId(doc.getVoucherId());
        recv.setPeriod(period);
        recv.setTxDate(row.invoiceDate);
        recv.setAmount(row.totalAmount);
        recv.setSettledAmount(BigDecimal.ZERO);
        recv.setUnsettledAmount(row.totalAmount);
        recv.setSummary(row.goodsName);
        recv.setStatus(ArapStatus.CONFIRMED);
        receivableMapper.insert(recv);
        log.info("P10-1 销售发票应收单生成: customerId={}, docId={}, amount={}",
                customerId, doc.getId(), row.totalAmount);
    }

    /**
     * 红冲关联：根据被红冲的蓝字发票号，找到对应发票并标记为 REVERSED，同时更新备注。
     */
    @Transactional
    protected void handleRedFlushReversal(String originalInvoiceNo, BusinessDocEntity redDoc, String period) {
        List<OutputInvoiceEntity> originals = outputInvoiceMapper.selectList(
                new LambdaQueryWrapper<OutputInvoiceEntity>()
                        .eq(OutputInvoiceEntity::getInvoiceNo, originalInvoiceNo)
                        .last("LIMIT 1"));
        if (originals.isEmpty()) {
            log.warn("红冲关联未找到原发票: invoiceNo={}, 跳过标记", originalInvoiceNo);
            return;
        }
        OutputInvoiceEntity original = originals.get(0);
        if (InvoiceStatus.isTerminal(original.getStatus())) {
            log.warn("原发票已是终态，跳过: id={}, status={}", original.getId(), original.getStatus());
            return;
        }
        original.setStatus(InvoiceStatus.REVERSED);
        String docRef = redDoc != null ? redDoc.getDocNo() : "外部导入";
        String note = "被" + docRef + "红冲(" + period + ")";
        if (original.getRemark() != null && !original.getRemark().isBlank()) {
            original.setRemark(original.getRemark() + " | " + note);
        } else {
            original.setRemark(note);
        }
        outputInvoiceMapper.updateById(original);
        log.info("红冲关联成功: originalInvoiceNo={}, newStatus=REVERSED", originalInvoiceNo);
    }

    /**
     * 批量扫描已存在的红字发票，按金额+客户名匹配蓝字发票并建立红冲关联。
     * 用于处理已导入数据的历史红冲关联。
     * 使用直接SQL更新绕过 AOP 审计切面（批量操作不需要逐条审计日志）。
     */
    public Map<String, Object> batchLinkRedFlushInvoices() {
        // 查找所有金额为负且状态非终态的发票（潜在红字发票）
        List<OutputInvoiceEntity> redInvoices = outputInvoiceMapper.selectList(
                new LambdaQueryWrapper<OutputInvoiceEntity>()
                        .lt(OutputInvoiceEntity::getAmount, BigDecimal.ZERO)
                        .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.VOIDED)
                        .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.REVERSED)
                        .eq(OutputInvoiceEntity::getDeleted, 0));

        int matched = 0, skipped = 0;
        for (OutputInvoiceEntity red : redInvoices) {
            BigDecimal absAmount = red.getAmount().abs();
            // 按金额+客户名找匹配的蓝字发票
            List<OutputInvoiceEntity> blues = outputInvoiceMapper.selectList(
                    new LambdaQueryWrapper<OutputInvoiceEntity>()
                            .eq(OutputInvoiceEntity::getAmount, absAmount)
                            .eq(OutputInvoiceEntity::getCustomerName, red.getCustomerName())
                            .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.REVERSED)
                            .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.VOIDED)
                            .eq(OutputInvoiceEntity::getDeleted, 0)
                            .last("LIMIT 1"));
            if (!blues.isEmpty()) {
                OutputInvoiceEntity blue = blues.get(0);
                // 直接用SQL更新，绕过 AOP 审计切面
                outputInvoiceMapper.updateStatusDirect(blue.getId(), InvoiceStatus.REVERSED,
                        (blue.getRemark() != null ? blue.getRemark() + " | " : "")
                                + "被" + red.getInvoiceNo() + "红冲(" + red.getPeriod() + ")");
                matched++;
                log.info("批量红冲关联: red={}, blue={}, amount={}", red.getInvoiceNo(), blue.getInvoiceNo(), absAmount);
            } else {
                skipped++;
            }
        }
        return Map.of("matched", matched, "skipped", skipped, "total", redInvoices.size());
    }

    static class ParsedInvoiceRow {
        int rowNum;
        String invoiceNo;
        String sellerTaxId;
        String sellerName;
        String buyerTaxId;
        String buyerName;
        LocalDate invoiceDate;
        String goodsName;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal taxRate;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean isPositive = true;
        String remark;
        String originalInvoiceNo; // 红冲关联的原蓝字发票号
    }
}