package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.system.service.PeriodService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesInvoiceImportService {

    private static final String INVOICE_DOC_NO_PREFIX = "doc:no:INVOICE_OUT:";
    private static final long DEFAULT_USER_ID = 1L;
    private static final long DEFAULT_VOUCHER_TYPE_ID = 1L;

    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final CustomerMapper customerMapper;
    private final SubjectMapper subjectMapper;
    private final PeriodService periodService;
    private final StringRedisTemplate redisTemplate;
    private final ColumnMappingResolver columnMappingResolver;

    /**
     * 导入销售发票 Excel
     */
    @Transactional
    public Map<String, Object> importInvoices(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }

        String batchId = "INV_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ParsedInvoiceRow> rows = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            InvoiceReadListener listener = new InvoiceReadListener(rows, errors, columnMappingResolver);
            EasyExcel.read(is, new HashMap<Integer, String>().getClass(), listener)
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw new BusinessException(500, "读取Excel失败: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            return Map.of("total", 0, "success", 0, "errors", errors, "batchId", batchId,
                    "message", "未解析到有效发票行");
        }

        int success = 0;
        int docCreated = 0;
        int voucherCreated = 0;

        for (ParsedInvoiceRow row : rows) {
            try {
                // 1. 匹配客户
                Long customerId = matchOrCreateCustomer(row);
                if (customerId == null) {
                    errors.add(Map.of("row", row.rowNum, "invoiceNo", row.invoiceNo, "message", "客户匹配失败"));
                    continue;
                }

                // 2. 生成会计期间
                String period = row.invoiceDate.format(DateTimeFormatter.ofPattern("yyyyMM"));

                // 3. 创建业务单据
                BusinessDocEntity doc = createBusinessDoc(row, customerId, period, batchId);

                // 4. 创建凭证
                createVoucher(doc, row, customerId, period);

                success++;
                docCreated++;
                voucherCreated++;
            } catch (Exception e) {
                log.warn("处理发票行失败 row={}: {}", row.rowNum, e.getMessage());
                errors.add(Map.of("row", row.rowNum, "invoiceNo", row.invoiceNo, "message", e.getMessage()));
            }
        }

        log.info("销售发票导入完成: batchId={}, total={}, success={}", batchId, rows.size(), success);
        return Map.of(
                "total", rows.size(),
                "success", success,
                "docCreated", docCreated,
                "voucherCreated", voucherCreated,
                "errors", errors,
                "batchId", batchId
        );
    }

    @Transactional
    protected BusinessDocEntity createBusinessDoc(ParsedInvoiceRow row, Long customerId, String period, String batchId) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateInvoiceDocNo(period));
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(row.invoiceDate);
        doc.setPeriod(period);
        doc.setAmount(row.totalAmount); // 价税合计
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

        // 查找科目
        Subject subjectBank = findSubjectByCode("1122");  // 应收账款
        Subject subjectRevenue = findSubjectByCode("5001"); // 主营业务收入
        Subject subjectOutputTax = findSubjectByCode("2221.01"); // 应交税费-销项税

        if (subjectBank == null || subjectRevenue == null) {
            throw new BusinessException(500, "缺少基础科目配置(1122/5001)");
        }

        BigDecimal exclTaxAmount = row.amount;       // 不含税金额
        BigDecimal taxAmount = row.taxAmount;        // 税额
        BigDecimal totalAmount = row.totalAmount;    // 价税合计

        // 红字发票: 金额取负数
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

        // 借: 应收账款
        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(subjectBank.getId());
        entryDr.setDebit(totalAmount);
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(row.goodsName);
        entryDr.setSortOrder(sort++);
        voucherEntryMapper.insert(entryDr);

        // 贷: 主营业务收入
        VoucherEntryEntity entryCr1 = new VoucherEntryEntity();
        entryCr1.setVoucherId(voucher.getId());
        entryCr1.setSubjectId(subjectRevenue.getId());
        entryCr1.setDebit(BigDecimal.ZERO);
        entryCr1.setCredit(exclTaxAmount);
        entryCr1.setSummary(row.goodsName);
        entryCr1.setSortOrder(sort++);
        voucherEntryMapper.insert(entryCr1);

        // 贷: 应交税费-销项税
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

        // 回写单据
        doc.setVoucherId(voucher.getId());
        doc.setStatus("VOUCHERED");
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);

        log.info("发票导入生成凭证: invoiceNo={}, voucherId={}, voucherNo={}",
                row.invoiceNo, voucher.getId(), voucherNo);
    }

    private Long matchOrCreateCustomer(ParsedInvoiceRow row) {
        if (StrUtil.isBlank(row.buyerTaxId) && StrUtil.isBlank(row.buyerName)) return null;

        // 阶段一: 按税号精确匹配
        if (StrUtil.isNotBlank(row.buyerTaxId)) {
            List<CustomerEntity> byTax = customerMapper.selectList(
                    new LambdaQueryWrapper<CustomerEntity>()
                            .eq(CustomerEntity::getTaxNo, row.buyerTaxId)
                            .last("LIMIT 1"));
            if (!byTax.isEmpty()) return byTax.get(0).getId();
        }

        // 阶段二: 按名称模糊匹配
        if (StrUtil.isNotBlank(row.buyerName)) {
            List<CustomerEntity> byName = customerMapper.selectList(
                    new LambdaQueryWrapper<CustomerEntity>()
                            .like(CustomerEntity::getName, row.buyerName)
                            .last("LIMIT 1"));
            if (!byName.isEmpty()) return byName.get(0).getId();

            // 再试一下更宽松的匹配
            String shortName = row.buyerName.replaceAll("[（(].*[）)]", "").trim();
            if (!shortName.equals(row.buyerName) && shortName.length() >= 4) {
                List<CustomerEntity> byShort = customerMapper.selectList(
                        new LambdaQueryWrapper<CustomerEntity>()
                                .like(CustomerEntity::getName, shortName)
                                .last("LIMIT 1"));
                if (!byShort.isEmpty()) return byShort.get(0).getId();
            }
        }

        // 阶段三: 自动创建客户
        if (StrUtil.isNotBlank(row.buyerName) && StrUtil.isNotBlank(row.buyerTaxId)) {
            CustomerEntity newCustomer = new CustomerEntity();
            newCustomer.setName(row.buyerName);
            newCustomer.setTaxNo(row.buyerTaxId);
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
        String key = INVOICE_DOC_NO_PREFIX + period;
        Long seq = redisTemplate.opsForValue().increment(key);
        return "FPS" + period + String.format("%04d", seq);
    }

    private Subject findSubjectByCode(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    // ─── 内部数据结构 ───

    static class ParsedInvoiceRow {
        int rowNum;
        String invoiceNo;
        String sellerTaxId;
        String sellerName;
        String buyerTaxId;
        String buyerName;
        LocalDate invoiceDate;
        String goodsName;
        BigDecimal amount;       // 不含税金额
        BigDecimal taxRate;
        BigDecimal taxAmount;
        BigDecimal totalAmount;  // 价税合计
        boolean isPositive;      // 正数发票
    }

    // ─── EasyExcel 监听器 ───

    private static class InvoiceReadListener implements ReadListener<Map<Integer, String>> {

        private final List<ParsedInvoiceRow> rows;
        private final List<Map<String, Object>> errors;
        private final ColumnMappingResolver resolver;

        private boolean isHeaderRow = true;
        private String[] headers;
        private ColumnMappingResolver.MappingResult mapping;
        private int dataRowIndex = 0;

        InvoiceReadListener(List<ParsedInvoiceRow> rows, List<Map<String, Object>> errors,
                            ColumnMappingResolver resolver) {
            this.rows = rows;
            this.errors = errors;
            this.resolver = resolver;
        }

        @Override
        public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
            if (isHeaderRow) {
                isHeaderRow = false;
                headers = new String[rowMap.size()];
                for (int i = 0; i < rowMap.size(); i++) {
                    headers[i] = rowMap.getOrDefault(i, "").trim();
                }
                mapping = resolver.resolve(headers);
                if (!mapping.isValid()) {
                    throw new RuntimeException("必含列名缺失(发票日期/金额). 表头: " + String.join(",", headers));
                }
                return;
            }

            dataRowIndex++;
            try {
                ParsedInvoiceRow row = parseRow(rowMap);
                if (row != null) {
                    rows.add(row);
                }
            } catch (Exception e) {
                errors.add(Map.of("row", dataRowIndex + 1, "message", "解析失败: " + e.getMessage()));
            }
        }

        private ParsedInvoiceRow parseRow(Map<Integer, String> rowMap) {
            if (mapping == null) return null;
            ParsedInvoiceRow row = new ParsedInvoiceRow();
            row.rowNum = dataRowIndex + 1;

            // 发票号码
            Integer invIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.INVOICE_NO);
            if (invIdx != null) row.invoiceNo = rowMap.getOrDefault(invIdx, "").trim();

            // 销方识别号
            Integer sellerTaxIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SELLER_TAX_ID);
            if (sellerTaxIdx != null) row.sellerTaxId = rowMap.getOrDefault(sellerTaxIdx, "").trim();

            // 销方名称
            Integer sellerNameIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SELLER_NAME);
            if (sellerNameIdx != null) row.sellerName = rowMap.getOrDefault(sellerNameIdx, "").trim();

            // 购方识别号
            Integer buyerTaxIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.BUYER_TAX_ID);
            if (buyerTaxIdx != null) row.buyerTaxId = rowMap.getOrDefault(buyerTaxIdx, "").trim();

            // 购买方名称
            Integer buyerNameIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.BUYER_NAME);
            if (buyerNameIdx != null) row.buyerName = rowMap.getOrDefault(buyerNameIdx, "").trim();

            // 开票日期
            Integer dateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE);
            if (dateIdx != null) {
                String dateStr = rowMap.getOrDefault(dateIdx, "").trim();
                row.invoiceDate = parseDate(dateStr);
            }
            if (row.invoiceDate == null) throw new RuntimeException("缺少有效开票日期");

            // 货物或应税劳务名称
            Integer goodsIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.GOODS_NAME);
            if (goodsIdx != null) row.goodsName = rowMap.getOrDefault(goodsIdx, "").trim();

            // 金额(不含税)
            Integer amtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT);
            if (amtIdx != null) {
                String amtStr = rowMap.getOrDefault(amtIdx, "").trim();
                if (StrUtil.isNotBlank(amtStr))
                    row.amount = new BigDecimal(amtStr.replaceAll("[^0-9.-]", ""));
            }
            if (row.amount == null) throw new RuntimeException("缺少金额");

            // 税额
            Integer taxAmtIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TAX_AMOUNT);
            if (taxAmtIdx != null) {
                String taxStr = rowMap.getOrDefault(taxAmtIdx, "").trim();
                if (StrUtil.isNotBlank(taxStr))
                    row.taxAmount = new BigDecimal(taxStr.replaceAll("[^0-9.-]", ""));
            }
            if (row.taxAmount == null) row.taxAmount = BigDecimal.ZERO;

            // 税率
            Integer rateIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TAX_RATE);
            if (rateIdx != null) {
                String rateStr = rowMap.getOrDefault(rateIdx, "").trim();
                if (StrUtil.isNotBlank(rateStr)) {
                    try {
                        row.taxRate = new BigDecimal(rateStr.replace("%", "").trim());
                    } catch (Exception ignored) {}
                }
            }

            // 价税合计
            Integer totalIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TOTAL_AMOUNT);
            if (totalIdx != null) {
                String totalStr = rowMap.getOrDefault(totalIdx, "").trim();
                if (StrUtil.isNotBlank(totalStr))
                    row.totalAmount = new BigDecimal(totalStr.replaceAll("[^0-9.-]", ""));
            }
            if (row.totalAmount == null) {
                row.totalAmount = row.amount.add(row.taxAmount);
            }

            // 是否正数发票
            Integer positiveIdx = mapping.getFieldToColumnIndex().get(ColumnMappingResolver.Field.IS_POSITIVE);
            if (positiveIdx != null) {
                String posStr = rowMap.getOrDefault(positiveIdx, "").trim();
                row.isPositive = posStr.contains("是") || posStr.contains("正数") || posStr.toLowerCase().contains("y");
            } else {
                row.isPositive = true;
            }

            // 红字检测: 发票状态含"红字"/"红冲" 或 金额为负数
            if (!row.isPositive) {
                row.amount = row.amount.abs();
                row.taxAmount = row.taxAmount.abs();
                row.totalAmount = row.totalAmount.abs();
            }

            return row;
        }

        private LocalDate parseDate(String dateStr) {
            if (StrUtil.isBlank(dateStr)) return null;
            String clean = dateStr.trim();
            if (clean.matches("\\d{8}"))
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (clean.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"))
                return LocalDate.parse(clean.substring(0, 10));
            if (clean.matches("\\d{4}-\\d{2}-\\d{2}"))
                return LocalDate.parse(clean);
            try {
                return LocalDate.parse(clean);
            } catch (DateTimeParseException e) {
                return null;
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            log.info("发票Excel解析完成, 共 {} 行", dataRowIndex);
        }
    }
}