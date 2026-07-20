package com.huicai.sme.tax.service.impl;
import com.huicai.base.business.util.ColumnMappingResolver;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.voucher.constant.VoucherType;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.business.mapper.BusinessDocEntryMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.mapper.InputInvoiceMapper;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P10-2: 采购发票 Excel 导入 - 仿 SalesInvoiceImportService.
 * P40 变更: 导入时只创建进项发票(status=PENDING_CONFIRM)，不再自动创建业务单据和凭证。
 *          审核通过后由 InputInvoiceStateMachineService.confirm() 自动创建。
 *
 * 与销售版差异:
 * - 凭证方向: 借 1601/2221.01 (进项税) / 贷 2202 (应付账款) [审核后生成]
 * - 客商: Vendor 而非 Customer
 * - 发票表: t_input_invoice 而非 t_output_invoice
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InputInvoiceImportService {

    private static final long DEFAULT_USER_ID = 1L;
    private static final long DEFAULT_VOUCHER_TYPE_ID = VoucherType.FK;

    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final VendorMapper vendorMapper;
    private final SubjectMapper subjectMapper;
    private final InputInvoiceMapper inputInvoiceMapper;
    private final ColumnMappingResolver columnMappingResolver;
    private final InvoiceDedupUtil invoiceDedupUtil;

    private final Map<String, List<ParsedInputInvoiceRow>> batchCache = new ConcurrentHashMap<>();

    public Map<String, Object> previewInvoices(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }

        String batchId = "PRE_IN_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ParsedInputInvoiceRow> allRows = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            org.apache.poi.ss.usermodel.Workbook workbook =
                    org.apache.poi.ss.usermodel.WorkbookFactory.create(is);
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
                    } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                            && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        java.util.Date dateVal = cell.getDateCellValue();
                        if (dateVal != null) {
                            rowMap.put(ci, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(dateVal));
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
                    ParsedInputInvoiceRow parsed = parseInvoiceRow(rowMap, mapping, rowIdx + 1);
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
            ParsedInputInvoiceRow r = allRows.get(i);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("rowIndex", i + 1);
            p.put("invoiceNo", r.invoiceNo);
            p.put("sellerTaxId", r.sellerTaxId);
            p.put("sellerName", r.sellerName);
            p.put("invoiceDate", r.invoiceDate != null ? r.invoiceDate.toString() : null);
            p.put("goodsName", r.goodsName);
            p.put("amount", r.amount);
            p.put("taxAmount", r.taxAmount);
            p.put("totalAmount", r.totalAmount);
            p.put("isPositive", r.isPositive);
            previews.add(p);
        }

        Set<String> existingSet = invoiceDedupUtil.findExisting(
                allRows.stream().map(r -> r.invoiceNo).filter(StrUtil::isNotBlank).toList());

        log.info("采购发票预览完成: batchId={}, total={}, errors={}, existing={}",
                batchId, allRows.size(), errors.size(), existingSet.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("total", allRows.size());
        result.put("preview", previews);
        result.put("errors", errors);
        result.put("existingInvoiceNos", new ArrayList<>(existingSet));
        return result;
    }

    private ParsedInputInvoiceRow parseInvoiceRow(Map<Integer, String> rowMap,
                                                   ColumnMappingResolver.MappingResult mapping, int rowNum) {
        ParsedInputInvoiceRow r = new ParsedInputInvoiceRow();
        r.rowNum = rowNum;

        Integer invIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.INVOICE_NO);
        if (invIdx != null) r.invoiceNo = rowMap.getOrDefault(invIdx, "").trim();

        // 销售版用 buyer, 采购版用 seller 作为外部对手方
        Integer sellerTaxIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SELLER_TAX_ID);
        if (sellerTaxIdx != null) r.sellerTaxId = rowMap.getOrDefault(sellerTaxIdx, "").trim();

        Integer sellerNameIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SELLER_NAME);
        if (sellerNameIdx != null) r.sellerName = rowMap.getOrDefault(sellerNameIdx, "").trim();

        Integer dateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE);
        if (dateIdx != null) r.invoiceDate = parseInvoiceDate(rowMap.getOrDefault(dateIdx, ""));

        Integer goodsIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.GOODS_NAME);
        if (goodsIdx != null) r.goodsName = rowMap.getOrDefault(goodsIdx, "").trim();

        Integer amountIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT);
        if (amountIdx != null) {
            try {
                r.amount = new BigDecimal(rowMap.getOrDefault(amountIdx, "0").trim());
            } catch (Exception e) {
                throw new BusinessException(400, "金额格式错误: " + rowMap.getOrDefault(amountIdx, ""));
            }
        }

        Integer taxIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TAX_AMOUNT);
        if (taxIdx != null) {
            try {
                r.taxAmount = new BigDecimal(rowMap.getOrDefault(taxIdx, "0").trim());
            } catch (Exception e) {
                r.taxAmount = BigDecimal.ZERO;
            }
        }

        // 价税合计列（可选，用于价税分离）
        Integer totalIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TOTAL_AMOUNT);
        BigDecimal totalFromColumn = null;
        if (totalIdx != null) {
            try {
                totalFromColumn = new BigDecimal(rowMap.getOrDefault(totalIdx, "0").trim());
            } catch (Exception ignored) {}
        }

        // 税率列（可选，用于价税分离）
        Integer rateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TAX_RATE);
        if (rateIdx != null) {
            String rateStr = rowMap.getOrDefault(rateIdx, "").trim();
            if (StrUtil.isNotBlank(rateStr)) {
                try { r.taxRate = new BigDecimal(rateStr.replace("%", "").trim()); } catch (Exception ignored) {}
            }
        }

        // 价税分离：当 amount(不含税)缺失或为零时，从 totalAmount 反向计算
        boolean amountMissing = r.amount.signum() == 0;
        if (amountMissing && totalFromColumn != null && r.taxRate != null && r.taxRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal divisor = BigDecimal.ONE.add(r.taxRate.divide(BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP));
            r.amount = totalFromColumn.divide(divisor, 2, java.math.RoundingMode.HALF_UP);
            r.taxAmount = totalFromColumn.subtract(r.amount);
            r.totalAmount = totalFromColumn;
        } else {
            // totalAmount = amount + taxAmount (若未单独列)
            if (totalFromColumn != null && totalFromColumn.compareTo(BigDecimal.ZERO) > 0) {
                r.totalAmount = totalFromColumn;
            } else {
                r.totalAmount = r.amount.add(r.taxAmount);
            }
        }

        r.isPositive = r.totalAmount.compareTo(BigDecimal.ZERO) > 0;
        if (!r.isPositive) {
            r.totalAmount = r.totalAmount.abs();
            r.amount = r.amount.abs();
            r.taxAmount = r.taxAmount.abs();
        }
        if (r.invoiceDate == null) {
            throw new BusinessException(400, "日期格式无法解析");
        }
        if (StrUtil.isBlank(r.goodsName)) {
            throw new BusinessException(400, "商品名称为空");
        }
        if (r.amount.signum() == 0) {
            throw new BusinessException(400, "金额为零");
        }
        return r;
    }

    LocalDate parseInvoiceDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return null;
        String s = dateStr.trim();
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy.MM.dd"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(p));
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    public Map<String, Object> confirmImport(String batchId) {
        if (StrUtil.isBlank(batchId)) {
            throw BusinessException.badRequest("batchId 不能为空");
        }
        List<ParsedInputInvoiceRow> rows = batchCache.remove(batchId);
        if (rows == null) {
            throw BusinessException.notFound("批次不存在或已过期, 请重新上传文件");
        }
        if (rows.isEmpty()) {
            return Map.of("total", 0, "success", 0, "docCreated", 0, "voucherCreated", 0, "batchId", batchId);
        }

        Set<String> existingSet = invoiceDedupUtil.findExisting(
                rows.stream().map(r -> r.invoiceNo).filter(StrUtil::isNotBlank).toList());

        int success = 0, docCreated = 0, voucherCreated = 0, duplicateSkipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (ParsedInputInvoiceRow row : rows) {
            try {
                if (StrUtil.isNotBlank(row.invoiceNo) && existingSet.contains(row.invoiceNo)) {
                    duplicateSkipped++;
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("row", row.rowNum);
                    err.put("invoiceNo", row.invoiceNo);
                    err.put("message", "发票号已存在, 已跳过");
                    errors.add(err);
                    continue;
                }
                Long vendorId = matchOrCreateVendor(row);
                if (vendorId == null) {
                    errors.add(Map.of("row", row.rowNum, "invoiceNo", row.invoiceNo, "message", "供应商匹配失败"));
                    continue;
                }
                String period = row.invoiceDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
                // P40: 导入时只创建发票，审核通过后才创建业务单据和凭证
                insertInputInvoice(row, vendorId, period);
                success++;
            } catch (Exception e) {
                log.warn("处理采购发票行失败 row={}: {}", row.rowNum, e.getMessage());
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", row.rowNum);
                err.put("invoiceNo", row.invoiceNo);
                err.put("message", e.getMessage());
                errors.add(err);
            }
        }

        return Map.of(
                "total", rows.size(), "success", success,
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
    BusinessDocEntity createBusinessDoc(ParsedInputInvoiceRow row, Long vendorId, String period) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateInputInvoiceDocNo(period));
        doc.setDocType("INVOICE_IN");
        doc.setDocDate(row.invoiceDate);
        doc.setPeriod(period);
        doc.setAmount(row.totalAmount);
        doc.setSupplierId(vendorId);
        doc.setSummary(row.goodsName);
        doc.setStatus("DRAFT");
        doc.setSource("INVOICE_IMPORT");
        doc.setInvoiceNo(row.invoiceNo);
        doc.setCreatedBy(DEFAULT_USER_ID);
        docMapper.insert(doc);

        // 创建业务单分录（供 generateVoucher 使用），带入发票号
        com.huicai.base.business.entity.BusinessDocEntryEntity entry =
                new com.huicai.base.business.entity.BusinessDocEntryEntity();
        entry.setDocId(doc.getId());
        entry.setAmount(row.totalAmount);
        entry.setSummary(row.goodsName);
        entry.setInvoiceNo(row.invoiceNo);
        entry.setSortOrder(1);
        docEntryMapper.insert(entry);

        return doc;
    }

    @Transactional
    String createVoucher(BusinessDocEntity doc, ParsedInputInvoiceRow row, Long vendorId, String period) {
        String voucherNo = voucherNoService.generateNextNo(period, DEFAULT_VOUCHER_TYPE_ID);
        Subject subjectBank = findSubjectByCode("2202");
        Subject subjectRevenue = findSubjectByCode("5001");
        Subject subjectInputTax = findSubjectByCode("2221.01");

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(DEFAULT_VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(row.totalAmount);
        voucher.setTotalCredit(row.totalAmount);
        voucher.setSummary(row.goodsName);
        voucher.setSource("INVOICE_IMPORT");
        voucher.setCreatedBy(DEFAULT_USER_ID);
        // 新增：溯源字段（采购发票 → 凭证）
        voucher.setSourceDocType("INPUT_INVOICE");
        voucher.setSourceDocNo(row.invoiceNo);
        voucherMapper.insert(voucher);

        doc.setVoucherId(voucher.getId());
        docMapper.updateById(doc);

        int sort = 1;
        // 借: 5001 销售收入 (采购: 借 5001/进项税)
        addVoucherEntry(voucher.getId(), subjectRevenue.getId(), row.amount, BigDecimal.ZERO, row.goodsName, sort++);
        addVoucherEntry(voucher.getId(), subjectInputTax.getId(), row.taxAmount, BigDecimal.ZERO, row.goodsName, sort++);
        // 贷: 2202 应付账款
        addVoucherEntry(voucher.getId(), subjectBank.getId(), BigDecimal.ZERO, row.totalAmount, row.goodsName, sort++);
        
        return voucherNo;
    }

    void addVoucherEntry(Long voucherId, Long subjectId, BigDecimal dr, BigDecimal cr, String summary, int sort) {
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(voucherId);
        entry.setSubjectId(subjectId);
        entry.setDebit(dr);
        entry.setCredit(cr);
        entry.setSummary(summary);
        entry.setSortOrder(sort);
        voucherEntryMapper.insert(entry);
    }

    @Transactional
    void insertInputInvoice(ParsedInputInvoiceRow row, Long vendorId, String period) {
        InputInvoiceEntity inv = new InputInvoiceEntity();
        inv.setInvoiceNo(row.invoiceNo);
        inv.setInvoiceDate(row.invoiceDate);
        inv.setPeriod(period);
        inv.setVendorId(vendorId);
        inv.setVendorName(row.sellerName);
        inv.setAmount(row.amount);
        inv.setAmountExTax(row.amount);
        inv.setTaxRate(row.taxRate != null ? row.taxRate : BigDecimal.ZERO);
        inv.setTaxAmount(row.taxAmount);
        inv.setTotalAmount(row.totalAmount);
        inv.setProcessStatus("PENDING");
        inv.setStatus(InvoiceStatus.PENDING_CONFIRM);  // P40: 导入后待确认
        inv.setInvoiceType("SPECIAL");
        // P21-b 重构 2026-06-22 修 P0 bug: 原 PENDING 违反 V8 CHECK 约束
        inv.setCertificationStatus("UNCERTIFIED");
        inv.setCreatedBy(DEFAULT_USER_ID);
        inputInvoiceMapper.insert(inv);
    }

    /**
     * 仿 SalesInvoiceImportService.matchOrCreateCustomer — 但匹配的是供应商
     */
    Long matchOrCreateVendor(ParsedInputInvoiceRow row) {
        if (StrUtil.isBlank(row.sellerTaxId) && StrUtil.isBlank(row.sellerName)) {
            return null;
        }
        if (StrUtil.isNotBlank(row.sellerTaxId)) {
            List<VendorEntity> byTax = vendorMapper.selectList(
                    new LambdaQueryWrapper<VendorEntity>().eq(VendorEntity::getTaxNo, row.sellerTaxId));
            if (!byTax.isEmpty()) return byTax.get(0).getId();
        }
        if (StrUtil.isNotBlank(row.sellerName)) {
            List<VendorEntity> byName = vendorMapper.selectList(
                    new LambdaQueryWrapper<VendorEntity>().eq(VendorEntity::getName, row.sellerName));
            if (!byName.isEmpty()) return byName.get(0).getId();
            // 短名匹配
            String shortName = row.sellerName.replaceAll("[（()）]", "").replaceAll("\\s+", "");
            if (shortName.length() >= 4) {
                List<VendorEntity> all = vendorMapper.selectList(
                        new LambdaQueryWrapper<VendorEntity>().like(VendorEntity::getName, "%" + shortName + "%"));
                if (!all.isEmpty()) return all.get(0).getId();
            }
            // 创建新供应商
            VendorEntity v = new VendorEntity();
            v.setName(row.sellerName);
            v.setTaxNo(row.sellerTaxId);
            vendorMapper.insert(v);
            if (v.getId() != null) return v.getId();
        }
        return null;
    }

    private Subject findSubjectByCode(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code));
        if (list.isEmpty()) {
            // 自动创建
            Subject s = new Subject();
            s.setCode(code);
            s.setName("P10-2-AUTO-" + code);
            s.setIsLeaf(true);
            subjectMapper.insert(s);
            return s;
        }
        return list.get(0);
    }

    private String generateInputInvoiceDocNo(String period) {
        return "IN" + period + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    static class ParsedInputInvoiceRow {
        int rowNum;
        String invoiceNo;
        String sellerTaxId;
        String sellerName;
        LocalDate invoiceDate;
        String goodsName;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal taxRate;
        boolean isPositive;
    }
}
