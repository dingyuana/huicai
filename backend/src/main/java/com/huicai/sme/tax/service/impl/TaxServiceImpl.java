package com.huicai.sme.tax.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.sme.tax.dto.BatchOperationResult;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.entity.TaxTypeEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.sme.tax.mapper.TaxDeclarationMapper;
import com.huicai.sme.tax.mapper.TaxTypeMapper;
import com.huicai.sme.tax.service.TaxService;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.base.business.util.TemplateMatcher;
import com.huicai.common.util.TemplateEngine;
import com.huicai.common.util.TemplateContext;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;
import com.huicai.base.voucher.service.VoucherTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxServiceImpl implements TaxService {

    private final TaxTypeMapper taxTypeMapper;
    private final InputInvoiceMapper inputMapper;
    private final OutputInvoiceMapper outputMapper;
    private final TaxDeclarationMapper declarationMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectMapper subjectMapper;
    private final TemplateMatcher templateMatcher;
    private final VoucherTemplateService voucherTemplateService;
    private final BusinessDocMapper businessDocMapper;
    private final OutputInvoiceStateMachineService outputInvoiceStateMachineService;

    // ========== 税种 ==========
    @Override
    public IPage<TaxTypeEntity> pageQueryTaxType(String keyword, Integer current, Integer size) {
        Page<TaxTypeEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<TaxTypeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(TaxTypeEntity::getCode, keyword)
                    .or().like(TaxTypeEntity::getName, keyword));
        }
        wrapper.orderByAsc(TaxTypeEntity::getCode);
        return taxTypeMapper.selectPage(page, wrapper);
    }

    @Override
    public List<TaxTypeEntity> listAllTaxTypes() {
        return taxTypeMapper.selectList(new LambdaQueryWrapper<TaxTypeEntity>()
                .eq(TaxTypeEntity::getIsActive, true)
                .orderByAsc(TaxTypeEntity::getCode));
    }

    @Override
    public TaxTypeEntity createTaxType(TaxTypeEntity entity) {
        if (entity.getIsActive() == null) entity.setIsActive(true);
        taxTypeMapper.insert(entity);
        return entity;
    }

    @Override
    public TaxTypeEntity updateTaxType(TaxTypeEntity entity) {
        TaxTypeEntity existing = taxTypeMapper.selectById(entity.getId());
        if (existing == null) {
            throw new BusinessException("税种不存在");
        }
        existing.setName(entity.getName());
        existing.setRate(entity.getRate());
        existing.setTaxCategory(entity.getTaxCategory());
        existing.setIsActive(entity.getIsActive());
        existing.setRemark(entity.getRemark());
        taxTypeMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deleteTaxType(Long id) {
        taxTypeMapper.deleteById(id);
    }

    // ========== 进项发票 ==========
    @Override
    public IPage<InputInvoiceEntity> pageQueryInput(String vendorName, String period, String certStatus,
                                                     Integer current, Integer size) {
        Page<InputInvoiceEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<InputInvoiceEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(vendorName)) {
            wrapper.like(InputInvoiceEntity::getVendorName, vendorName);
        }
        if (StrUtil.isNotBlank(period)) {
            wrapper.eq(InputInvoiceEntity::getPeriod, period);
        }
        if (StrUtil.isNotBlank(certStatus)) {
            wrapper.eq(InputInvoiceEntity::getCertificationStatus, certStatus);
        }
        wrapper.orderByDesc(InputInvoiceEntity::getInvoiceDate);
        IPage<InputInvoiceEntity> result = inputMapper.selectPage(page, wrapper);
        // 回填关联编号（docNo / voucherNo）
        for (InputInvoiceEntity inv : result.getRecords()) {
            fillInputInvoiceNumbers(inv);
        }
        return result;
    }

    /** 回填进项发票的关联编号 */
    private void fillInputInvoiceNumbers(InputInvoiceEntity inv) {
        if (inv.getDocId() != null) {
            var doc = businessDocMapper.selectById(inv.getDocId());
            if (doc != null) inv.setDocNo(doc.getDocNo());
        }
        if (inv.getVoucherId() != null) {
            var voucher = voucherMapper.selectById(inv.getVoucherId());
            if (voucher != null) inv.setVoucherNo(voucher.getVoucherNo());
        }
    }

    @Override
    public InputInvoiceEntity createInput(InputInvoiceEntity entity) {
        // 校验金额
        if (entity.getAmount() == null || entity.getTaxRate() == null) {
            throw new BusinessException("金额和税率不能为空");
        }
        if (entity.getTaxAmount() == null) {
            // taxRate 存储为百分比整数(如13表示13%)，需除100
            entity.setTaxAmount(entity.getAmount().multiply(entity.getTaxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (entity.getTotalAmount() == null) {
            entity.setTotalAmount(entity.getAmount().add(entity.getTaxAmount()));
        }
        if (entity.getCertificationStatus() == null) {
            entity.setCertificationStatus("UNCERTIFIED");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(InvoiceStatus.PENDING_CONFIRM);
        }
        if (entity.getPeriod() == null) {
            entity.setPeriod(String.format("%04d%02d",
                    entity.getInvoiceDate().getYear(),
                    entity.getInvoiceDate().getMonthValue()));
        }
        inputMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InputInvoiceEntity certify(Long id, String deductionPeriod) {
        InputInvoiceEntity entity = inputMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("发票不存在");
        }
        if (!"UNCERTIFIED".equals(entity.getCertificationStatus())) {
            throw new BusinessException("当前状态不可认证");
        }
        entity.setCertificationStatus("CERTIFIED");
        entity.setCertifiedDate(LocalDate.now());
        entity.setDeductionPeriod(deductionPeriod != null ? deductionPeriod :
                String.format("%04d%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue()));
        entity.setDeductionAmount(entity.getTaxAmount());
        inputMapper.updateById(entity);
        return entity;
    }

    @Override
    public Map<String, Object> inputSummary(String period) {
        return inputMapper.summaryByPeriod(period);
    }

    @Override
    public List<Map<String, Object>> inputByTaxRate(String period) {
        return inputMapper.byTaxRate(period);
    }

    // ========== 销项发票 ==========
    @Override
    public IPage<OutputInvoiceEntity> pageQueryOutput(String customerName, String period, String status,
                                                       String invoiceType, Integer current, Integer size) {
        Page<OutputInvoiceEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<OutputInvoiceEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(customerName)) {
            wrapper.like(OutputInvoiceEntity::getCustomerName, customerName);
        }
        if (StrUtil.isNotBlank(period)) {
            wrapper.eq(OutputInvoiceEntity::getPeriod, period);
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(OutputInvoiceEntity::getStatus, status);
        }
        if (StrUtil.isNotBlank(invoiceType)) {
            if ("RED".equals(invoiceType)) {
                // 红字发票：金额<0 或 有原蓝字发票号(红冲关联字段)
                // originalInvoiceNo 是 @TableField(exist=false)，不能用 Lambda 映射，改用 apply
                wrapper.and(w -> w
                        .lt(OutputInvoiceEntity::getAmount, BigDecimal.ZERO)
                        .or()
                        .apply("reversed_by_invoice_id IS NOT NULL"));
            } else {
                // 专用/普通发票：排除红字(金额<0)、红冲关联(reversed_by_invoice_id非空)、已冲销(status=REVERSED)
                wrapper.eq(OutputInvoiceEntity::getInvoiceType, invoiceType)
                        .ge(OutputInvoiceEntity::getAmount, BigDecimal.ZERO)
                        .apply("reversed_by_invoice_id IS NULL")
                        .ne(OutputInvoiceEntity::getStatus, InvoiceStatus.REVERSED);
            }
        }
        wrapper.orderByDesc(OutputInvoiceEntity::getInvoiceDate);
        IPage<OutputInvoiceEntity> result = outputMapper.selectPage(page, wrapper);
        // 回填关联编号（docNo / voucherNo）+ 红冲关联信息
        for (OutputInvoiceEntity inv : result.getRecords()) {
            fillOutputInvoiceDetails(inv);
        }
        return result;
    }

    @Override
    public OutputInvoiceEntity getOutputById(Long id) {
        OutputInvoiceEntity invoice = outputMapper.selectById(id);
        if (invoice == null) {
            return null;
        }
        fillOutputInvoiceDetails(invoice);
        return invoice;
    }

    /** 回填销项发票的关联编号和红冲信息 */
    private void fillOutputInvoiceDetails(OutputInvoiceEntity inv) {
        if (StrUtil.isNotBlank(inv.getOriginalInvoiceNo())) {
            OutputInvoiceEntity original = outputMapper.selectOne(
                    new LambdaQueryWrapper<OutputInvoiceEntity>()
                            .eq(OutputInvoiceEntity::getInvoiceNo, inv.getOriginalInvoiceNo())
                            .last("LIMIT 1")
            );
            if (original != null) {
                inv.setOriginalInvoiceId(original.getId());
            }
        }
        
        if (inv.getReversedByInvoiceId() != null) {
            OutputInvoiceEntity redInvoice = outputMapper.selectById(inv.getReversedByInvoiceId());
            if (redInvoice != null) {
                inv.setReversedByInvoiceNo(redInvoice.getInvoiceNo());
            }
        }

        // 回填关联单据号和凭证号
        if (inv.getDocId() != null) {
            var doc = businessDocMapper.selectById(inv.getDocId());
            if (doc != null) {
                inv.setDocNo(doc.getDocNo());
                inv.setDocStatus(doc.getStatus());  // P2: 关联业务单据状态
            }
        }
        if (inv.getVoucherId() != null) {
            var voucher = voucherMapper.selectById(inv.getVoucherId());
            if (voucher != null) {
                inv.setVoucherNo(voucher.getVoucherNo());
                inv.setVoucherStatus(voucher.getStatus());  // P2: 关联凭证状态
            }
        }
        // P34: 应收单已合并到业务单据，不再回填 receivableNo
    }

    @Override
    public OutputInvoiceEntity createOutput(OutputInvoiceEntity entity) {
        if (entity.getAmount() == null || entity.getTaxRate() == null) {
            throw new BusinessException("金额和税率不能为空");
        }
        if (entity.getTaxAmount() == null) {
            // taxRate 存储为百分比整数(如13表示13%)，需除100
            entity.setTaxAmount(entity.getAmount().multiply(entity.getTaxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (entity.getTotalAmount() == null) {
            entity.setTotalAmount(entity.getAmount().add(entity.getTaxAmount()));
        }
        if (entity.getStatus() == null) {
            entity.setStatus("DRAFT");
        }
        if (entity.getPeriod() == null) {
            entity.setPeriod(String.format("%04d%02d",
                    entity.getInvoiceDate().getYear(),
                    entity.getInvoiceDate().getMonthValue()));
        }
        outputMapper.insert(entity);
        return entity;
    }

    @Override
    public void deleteOutput(Long id) {
        outputMapper.deleteById(id);
    }

    private static final long VOUCHER_TYPE_ID = 1L;

    @Override
    @Transactional
    public void generateVoucherFromInvoice(Long invoiceId, Long userId) {
        OutputInvoiceEntity inv = outputMapper.selectById(invoiceId);
        if (inv == null) throw BusinessException.notFound("发票不存在");
        if (!InvoiceStatus.isVoucherable(inv.getStatus())) {
            throw BusinessException.badRequest("仅已确认状态可生成凭证，当前: " + inv.getStatus());
        }

        // 1. 按模板生成
        TemplateContext ctx = new TemplateContext()
                .setSource("INVOICE")
                .setBusinessType("INVOICE_OUT")
                .setAmount(inv.getAmount())
                .setTaxAmount(inv.getTaxAmount())
                .setTotalAmount(inv.getTotalAmount())
                .setPeriod(inv.getPeriod())
                .setCustomerName(inv.getCustomerName());
        ctx.getVariables().put("发票号", inv.getInvoiceNo() != null ? inv.getInvoiceNo() : "");
        VoucherTemplateEntity template = templateMatcher.match(ctx);
        if (template != null) {
            List<VoucherTemplateLineEntity> tplLines = voucherTemplateService.getLines(template.getId());
            if (tplLines != null && !tplLines.isEmpty()) {
                generateFromTemplate(inv, template, tplLines, ctx, userId);
                return;
            }
        }

        // 2. 降级: 硬编码科目 — 1 次批量查询替代 3 次串行查询
        String voucherNo = voucherNoService.generateNextNo(inv.getPeriod(), VOUCHER_TYPE_ID);
        java.util.Map<String, Subject> subjects = findSubjectsByCodes(java.util.List.of("1122", "5001", "2221.01"));
        Subject subjectAr = subjects.get("1122");
        Subject subjectRevenue = subjects.get("5001");
        Subject subjectOutputTax = subjects.get("2221.01");
        if (subjectAr == null || subjectRevenue == null) {
            throw new BusinessException(500, "缺少基础科目配置(1122/5001)");
        }

        BigDecimal exclTax = inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
        BigDecimal taxAmt = inv.getTaxAmount() != null ? inv.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal totalAmt = inv.getTotalAmount() != null ? inv.getTotalAmount() : exclTax.add(taxAmt);
        boolean isRed = exclTax.compareTo(BigDecimal.ZERO) < 0;

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(inv.getPeriod());
        voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        String summaryBase = "发票转凭证: " + (inv.getInvoiceNo() != null ? inv.getInvoiceNo() : "") + " - " + (inv.getCustomerName() != null ? inv.getCustomerName() : "");
        voucher.setSummary(summaryBase);
        voucher.setTotalDebit(totalAmt.abs());
        voucher.setTotalCredit(totalAmt.abs());
        voucher.setCreatedBy(userId);
        // 新增：溯源字段（销售发票 → 凭证）
        voucher.setSourceDocId(inv.getId());           // P33 补充
        voucher.setSourceDocType("OUTPUT_INVOICE");
        voucher.setSourceDocNo(inv.getInvoiceNo());
        voucherMapper.insert(voucher);

        String entrySummary = (inv.getInvoiceNo() != null ? inv.getInvoiceNo() : "") + " " + (inv.getCustomerName() != null ? inv.getCustomerName() : "");
        int sort = 1;
        if (isRed) {
            // 红字发票：借收入/借销项税/贷应收，全部取绝对值以满足 chk_entry_amount (debit>=0, credit>=0)
            // 贷：应收账款 1122
            VoucherEntryEntity crAr = new VoucherEntryEntity();
            crAr.setVoucherId(voucher.getId());
            crAr.setSubjectId(subjectAr.getId());
            crAr.setDebit(BigDecimal.ZERO);
            crAr.setCredit(totalAmt.abs());
            crAr.setSummary(entrySummary);
            crAr.setSortOrder(sort++);
            voucherEntryMapper.insert(crAr);

            // 借：主营业务收入 5001
            VoucherEntryEntity drRev = new VoucherEntryEntity();
            drRev.setVoucherId(voucher.getId());
            drRev.setSubjectId(subjectRevenue.getId());
            drRev.setDebit(exclTax.abs());
            drRev.setCredit(BigDecimal.ZERO);
            drRev.setSummary(entrySummary);
            drRev.setSortOrder(sort++);
            voucherEntryMapper.insert(drRev);

            // 借：销项税 2221.01
            if (subjectOutputTax != null && taxAmt.compareTo(BigDecimal.ZERO) != 0) {
                VoucherEntryEntity drTax = new VoucherEntryEntity();
                drTax.setVoucherId(voucher.getId());
                drTax.setSubjectId(subjectOutputTax.getId());
                drTax.setDebit(taxAmt.abs());
                drTax.setCredit(BigDecimal.ZERO);
                drTax.setSummary(entrySummary);
                drTax.setSortOrder(sort++);
                voucherEntryMapper.insert(drTax);
            }
        } else {
            // 借：应收账款 1122
            VoucherEntryEntity dr = new VoucherEntryEntity();
            dr.setVoucherId(voucher.getId());
            dr.setSubjectId(subjectAr.getId());
            dr.setDebit(totalAmt);
            dr.setCredit(BigDecimal.ZERO);
            dr.setSummary(entrySummary);
            dr.setSortOrder(sort++);
            voucherEntryMapper.insert(dr);

            // 贷：主营业务收入 5001
            VoucherEntryEntity cr1 = new VoucherEntryEntity();
            cr1.setVoucherId(voucher.getId());
            cr1.setSubjectId(subjectRevenue.getId());
            cr1.setDebit(BigDecimal.ZERO);
            cr1.setCredit(exclTax);
            cr1.setSummary(entrySummary);
            cr1.setSortOrder(sort++);
            voucherEntryMapper.insert(cr1);

            // 贷：销项税 2221.01
            if (subjectOutputTax != null && taxAmt.compareTo(BigDecimal.ZERO) != 0) {
                VoucherEntryEntity cr2 = new VoucherEntryEntity();
                cr2.setVoucherId(voucher.getId());
                cr2.setSubjectId(subjectOutputTax.getId());
                cr2.setDebit(BigDecimal.ZERO);
                cr2.setCredit(taxAmt);
                cr2.setSummary(entrySummary);
                cr2.setSortOrder(sort++);
                voucherEntryMapper.insert(cr2);
            }
        }

        // Inline markVouchered to break circular dependency
        inv.setStatus(InvoiceStatus.VOUCHERED);
        inv.setVoucherId(voucher.getId());
        inv.setVoucherNo(voucherNo);
        inv.setUpdatedBy(userId);
        outputMapper.updateById(inv);
        log.info("发票生成凭证: invoiceId={}, voucherId={}, voucherNo={}", invoiceId, voucher.getId(), voucherNo);
        log.info("发票生成凭证: invoiceId={}, voucherId={}, voucherNo={}", invoiceId, voucher.getId(), voucherNo);
    }

    /**
     * 按模板生成发票凭证.
     */
    private void generateFromTemplate(OutputInvoiceEntity inv,
                                       VoucherTemplateEntity template,
                                       List<VoucherTemplateLineEntity> tplLines,
                                       TemplateContext ctx,
                                       Long userId) {
        String voucherNo = voucherNoService.generateNextNo(inv.getPeriod(), VOUCHER_TYPE_ID);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(inv.getPeriod());
        voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(TemplateEngine.renderSummary("发票转凭证: {发票号} - {客户名称}", ctx));
        voucher.setTemplateId(template.getId());
        voucher.setCreatedBy(userId);
        // 新增：溯源字段（销售发票 → 凭证）
        voucher.setSourceDocId(inv.getId());           // P33 补充
        voucher.setSourceDocType("OUTPUT_INVOICE");
        voucher.setSourceDocNo(inv.getInvoiceNo());
        voucherMapper.insert(voucher);

        BigDecimal totalD = BigDecimal.ZERO, totalC = BigDecimal.ZERO;
        int sort = 1;
        for (VoucherTemplateLineEntity tplLine : tplLines) {
            BigDecimal dr = TemplateEngine.renderAmount(tplLine.getDrAmountTemplate(), ctx);
            BigDecimal cr = TemplateEngine.renderAmount(tplLine.getCrAmountTemplate(), ctx);
            if (dr == null) dr = BigDecimal.ZERO;
            if (cr == null) cr = BigDecimal.ZERO;

            if ("debit".equals(tplLine.getDirection())) {
                if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) > 0) { dr = cr; cr = BigDecimal.ZERO; }
                else { cr = BigDecimal.ZERO; }
            } else if ("credit".equals(tplLine.getDirection())) {
                if (cr.compareTo(BigDecimal.ZERO) == 0 && dr.compareTo(BigDecimal.ZERO) > 0) { cr = dr; dr = BigDecimal.ZERO; }
                else { dr = BigDecimal.ZERO; }
            }
            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

            String summary = TemplateEngine.renderSummary(tplLine.getSummaryTemplate(), ctx);
            VoucherEntryEntity ve = new VoucherEntryEntity();
            ve.setVoucherId(voucher.getId());
            ve.setSubjectId(tplLine.getSubjectId());
            ve.setDebit(dr); ve.setCredit(cr);
            ve.setSummary(summary);
            ve.setSortOrder(sort++);
            voucherEntryMapper.insert(ve);
            totalD = totalD.add(dr); totalC = totalC.add(cr);
        }

        BigDecimal maxAmt = totalD.max(totalC);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);

        // Inline markVouchered to break circular dependency
        inv.setStatus(InvoiceStatus.VOUCHERED);
        inv.setVoucherId(voucher.getId());
        inv.setVoucherNo(voucherNo);
        inv.setUpdatedBy(userId);
        outputMapper.updateById(inv);
        log.info("发票模板制证: invoiceId={}, voucherId={}, templateId={}", inv.getId(), voucher.getId(), template.getId());
    }

    private Subject findSubject(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<String, Subject> findSubjectsByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyMap();
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().in(Subject::getCode, codes));
        Map<String, Subject> result = new HashMap<>(list.size() * 2);
        for (Subject s : list) result.put(s.getCode(), s);
        return result;
    }

    @Override
    public Map<String, Object> outputSummary(String period) {
        return outputMapper.summaryByPeriod(period);
    }

    @Override
    public Map<String, Object> outputSummaryAll() {
        return outputMapper.summaryAll();
    }

    @Override
    public List<Map<String, Object>> outputByTaxRate(String period) {
        return outputMapper.byTaxRate(period);
    }

    // ========== 增值税计算 ==========
    @Override
    public Map<String, Object> calculateVat(String period) {
        Map<String, Object> output = outputMapper.summaryByPeriod(period);
        Map<String, Object> input = inputMapper.summaryByPeriod(period);
        BigDecimal outputTax = toBigDecimal(output == null ? null : output.get("tax"));
        BigDecimal inputTax = toBigDecimal(input == null ? null : input.get("deductible"));
        BigDecimal payable = outputTax.subtract(inputTax);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("period", period);
        result.put("outputTax", outputTax);
        result.put("inputTax", inputTax);
        result.put("payableTax", payable);
        // 附加税: 城建税7% + 教育费附加3% + 地方教育费附加2% = 12%
        if (payable.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal surcharge = payable.multiply(new BigDecimal("0.12"))
                    .setScale(2, RoundingMode.HALF_UP);
            result.put("surcharge", surcharge);
            result.put("totalPayable", payable.add(surcharge));
        } else {
            result.put("surcharge", BigDecimal.ZERO);
            result.put("totalPayable", BigDecimal.ZERO);
            result.put("note", "留抵税额(下期继续抵扣)");
        }
        return result;
    }

    // ========== 申报 ==========
    @Override
    public IPage<TaxDeclarationEntity> pageQueryDeclaration(String status, Integer current, Integer size) {
        Page<TaxDeclarationEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<TaxDeclarationEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(TaxDeclarationEntity::getStatus, status);
        }
        wrapper.orderByDesc(TaxDeclarationEntity::getCreatedAt);
        return declarationMapper.selectPage(page, wrapper);
    }

    @Override
    public TaxDeclarationEntity createDeclaration(TaxDeclarationEntity entity) {
        if (entity.getStatus() == null) entity.setStatus("DRAFT");
        declarationMapper.insert(entity);
        return entity;
    }

    @Override
    public TaxDeclarationEntity submitDeclaration(Long id) {
        TaxDeclarationEntity entity = declarationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("申报记录不存在");
        }
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        entity.setStatus("SUBMITTED");
        declarationMapper.updateById(entity);
        return entity;
    }

    // ─── P18-1: 申报审批 / 驳回 ───

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaxDeclarationEntity approveDeclaration(Long id, String approver) {
        TaxDeclarationEntity entity = declarationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("申报记录不存在");
        }
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw new BusinessException("仅已提交状态可审批: 当前=" + entity.getStatus());
        }
        entity.setStatus("APPROVED");
        Long userId = approver == null ? null : Long.valueOf(approver.hashCode() & 0x7FFFFFFF);
        entity.setUpdatedBy(userId);
        declarationMapper.updateById(entity);
        log.info("P18-1 申报审批通过: id={}, approver={}", id, approver);

        // P1: 审批通过后自动生成缴税凭证
        try {
            generateVoucherFromDeclaration(id, userId != null ? userId : 0L);
            log.info("P18-1 申报自动生成凭证成功: declarationId={}", id);
        } catch (Exception e) {
            log.error("P18-1 申报自动生成凭证失败, 可手工调用 generateVoucherFromDeclaration: declarationId={}, error={}", id, e.getMessage());
        }
        return declarationMapper.selectById(id);
    }

    @Override
    public TaxDeclarationEntity rejectDeclaration(Long id, String approver, String reason) {
        TaxDeclarationEntity entity = declarationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("申报记录不存在");
        }
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw new BusinessException("仅已提交状态可驳回: 当前=" + entity.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("驳回必须填理由");
        }
        entity.setStatus("REJECTED");
        entity.setRemark((entity.getRemark() == null ? "" : entity.getRemark() + " | ") + "REJECTED by " + approver + ": " + reason);
        declarationMapper.updateById(entity);
        log.info("P18-1 申报驳回: id={}, approver={}, reason={}", id, approver, reason);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateVoucherFromDeclaration(Long declarationId, Long userId) {
        TaxDeclarationEntity entity = declarationMapper.selectById(declarationId);
        if (entity == null) {
            throw new BusinessException("申报记录不存在");
        }
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new BusinessException("仅已审批(APPROVED)的申报可生成凭证, 当前=" + entity.getStatus());
        }
        if (entity.getVoucherId() != null) {
            throw new BusinessException("该申报已生成凭证, voucherId=" + entity.getVoucherId());
        }

        String period = entity.getPeriod() != null ? entity.getPeriod()
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String voucherNo = voucherNoService.generateNextNo(period, VOUCHER_TYPE_ID);

        // 尝试按模板匹配
        TemplateContext ctx = new TemplateContext()
                .setSource("TAX_DECLARATION")
                .setBusinessType("TAX_PAYMENT")
                .setAmount(entity.getPayableAmount())
                .setTaxAmount(entity.getPayableAmount())
                .setTotalAmount(entity.getPayableAmount())
                .setPeriod(period)
                .setCustomerName(entity.getTaxType());
        VoucherTemplateEntity template = templateMatcher.match(ctx);
        if (template != null) {
            List<VoucherTemplateLineEntity> tplLines = voucherTemplateService.getLines(template.getId());
            if (tplLines != null && !tplLines.isEmpty()) {
                generateDeclarationVoucherFromTemplate(entity, template, tplLines, ctx, userId, period, voucherNo);
                return;
            }
        }

        // 降级: 硬编码科目 — 借:应交税费(2221) 贷:银行存款(1002)
        Subject subjectTaxPayable = findSubject("2221");
        Subject subjectBank = findSubject("1002");
        if (subjectTaxPayable == null || subjectBank == null) {
            throw new BusinessException(500, "缺少基础科目配置(2221/1002)");
        }

        BigDecimal amount = entity.getPayableAmount() != null ? entity.getPayableAmount() : BigDecimal.ZERO;

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("缴税: " + (entity.getTaxType() != null ? entity.getTaxType() : "") + " " + entity.getPeriod());
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        voucher.setCreatedBy(userId);
        // 新增：溯源字段（税务申报 → 凭证）
        voucher.setSourceDocType("TAX_DECLARATION");
        voucher.setSourceDocNo(entity.getDeclarationNo());
        voucherMapper.insert(voucher);

        int sort = 1;
        VoucherEntryEntity dr = new VoucherEntryEntity();
        dr.setVoucherId(voucher.getId());
        dr.setSubjectId(subjectTaxPayable.getId());
        dr.setDebit(amount);
        dr.setCredit(BigDecimal.ZERO);
        dr.setSummary("缴纳税款-" + entity.getPeriod());
        dr.setSortOrder(sort++);
        voucherEntryMapper.insert(dr);

        VoucherEntryEntity cr = new VoucherEntryEntity();
        cr.setVoucherId(voucher.getId());
        cr.setSubjectId(subjectBank.getId());
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(amount);
        cr.setSummary("缴纳税款-" + entity.getPeriod());
        cr.setSortOrder(sort);
        voucherEntryMapper.insert(cr);

        entity.setVoucherId(voucher.getId());
        declarationMapper.updateById(entity);

        log.info("申报生成凭证: declarationId={}, voucherId={}, voucherNo={}, amount={}",
                declarationId, voucher.getId(), voucherNo, amount);
    }

    private void generateDeclarationVoucherFromTemplate(TaxDeclarationEntity entity,
                                                         VoucherTemplateEntity template,
                                                         List<VoucherTemplateLineEntity> tplLines,
                                                         TemplateContext ctx,
                                                         Long userId,
                                                         String period,
                                                         String voucherNo) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("缴税: " + (entity.getTaxType() != null ? entity.getTaxType() : ""));
        voucher.setTemplateId(template.getId());
        voucher.setCreatedBy(userId);
        // 新增：溯源字段（税务申报 → 凭证）
        voucher.setSourceDocType("TAX_DECLARATION");
        voucher.setSourceDocNo(entity.getDeclarationNo());
        voucherMapper.insert(voucher);

        BigDecimal totalD = BigDecimal.ZERO, totalC = BigDecimal.ZERO;
        int sort = 1;
        for (VoucherTemplateLineEntity tplLine : tplLines) {
            BigDecimal dr = TemplateEngine.renderAmount(tplLine.getDrAmountTemplate(), ctx);
            BigDecimal cr = TemplateEngine.renderAmount(tplLine.getCrAmountTemplate(), ctx);
            if (dr == null) dr = BigDecimal.ZERO;
            if (cr == null) cr = BigDecimal.ZERO;

            if ("debit".equals(tplLine.getDirection())) {
                if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) > 0) { dr = cr; cr = BigDecimal.ZERO; }
                else { cr = BigDecimal.ZERO; }
            } else if ("credit".equals(tplLine.getDirection())) {
                if (cr.compareTo(BigDecimal.ZERO) == 0 && dr.compareTo(BigDecimal.ZERO) > 0) { cr = dr; dr = BigDecimal.ZERO; }
                else { dr = BigDecimal.ZERO; }
            }
            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

            String summary = TemplateEngine.renderSummary(tplLine.getSummaryTemplate(), ctx);
            VoucherEntryEntity ve = new VoucherEntryEntity();
            ve.setVoucherId(voucher.getId());
            ve.setSubjectId(tplLine.getSubjectId());
            ve.setDebit(dr); ve.setCredit(cr);
            ve.setSummary(summary);
            ve.setSortOrder(sort++);
            voucherEntryMapper.insert(ve);
            totalD = totalD.add(dr); totalC = totalC.add(cr);
        }

        BigDecimal maxAmt = totalD.max(totalC);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);

        entity.setVoucherId(voucher.getId());
        declarationMapper.updateById(entity);

        log.info("申报模板制证: declarationId={}, voucherId={}, templateId={}",
                entity.getId(), voucher.getId(), template.getId());
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString());
    }

    @Override
    @Transactional
    public String batchGenerateVoucherFromInvoices(List<Long> invoiceIds, Long userId, Boolean sameCustomer) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw BusinessException.badRequest("发票ID列表不能为空");
        }
        List<OutputInvoiceEntity> invoices = outputMapper.selectBatchIds(invoiceIds);
        if (invoices.size() != invoiceIds.size()) {
            throw BusinessException.badRequest("部分发票不存在");
        }
        for (OutputInvoiceEntity inv : invoices) {
            if (!InvoiceStatus.isVoucherable(inv.getStatus())) {
                throw BusinessException.badRequest("发票 " + inv.getInvoiceNo() + " 状态不允许生成凭证，当前: " + inv.getStatus());
            }
        }
        if (Boolean.TRUE.equals(sameCustomer)) {
            String firstCust = invoices.get(0).getCustomerName();
            for (int i = 1; i < invoices.size(); i++) {
                String cust = invoices.get(i).getCustomerName();
                if (!java.util.Objects.equals(firstCust, cust)) {
                    throw BusinessException.badRequest("批量生成要求同一客户，第" + (i + 1) + "张不一致: " + cust);
                }
            }
        }
        BigDecimal totalExclTax = invoices.stream()
                .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = invoices.stream()
                .map(i -> i.getTaxAmount() != null ? i.getTaxAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = totalExclTax.add(totalTax);
        OutputInvoiceEntity first = invoices.get(0);
        String period = first.getPeriod();
        String customerName = first.getCustomerName();
        String invoiceNoList = invoices.stream()
                .map(OutputInvoiceEntity::getInvoiceNo)
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> a + ", " + b).orElse("");
        String voucherNo = voucherNoService.generateNextNo(period, VOUCHER_TYPE_ID);
        java.util.Map<String, Subject> subjects = findSubjectsByCodes(java.util.List.of("1122", "5001", "2221.01"));
        Subject subjectAr = subjects.get("1122");
        Subject subjectRevenue = subjects.get("5001");
        Subject subjectOutputTax = subjects.get("2221.01");
        if (subjectAr == null || subjectRevenue == null) {
            throw new BusinessException(500, "缺少基础科目配置(1122/5001)");
        }
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("合并凭证: " + invoiceNoList + " - " + (customerName != null ? customerName : ""));
        voucher.setTotalDebit(totalAmount);
        voucher.setTotalCredit(totalAmount);
        voucher.setCreatedBy(userId);
        voucher.setSourceDocType("OUTPUT_INVOICE");
        voucher.setSourceDocNo(invoiceNoList);
        voucherMapper.insert(voucher);
        int sort = 1;
        VoucherEntryEntity dr = new VoucherEntryEntity();
        dr.setVoucherId(voucher.getId());
        dr.setSubjectId(subjectAr.getId());
        dr.setDebit(totalAmount);
        dr.setCredit(BigDecimal.ZERO);
        dr.setSummary("合并发票: " + invoiceNoList);
        dr.setSortOrder(sort++);
        voucherEntryMapper.insert(dr);
        VoucherEntryEntity cr = new VoucherEntryEntity();
        cr.setVoucherId(voucher.getId());
        cr.setSubjectId(subjectRevenue.getId());
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(totalExclTax);
        cr.setSummary("合并发票: " + invoiceNoList);
        cr.setSortOrder(sort++);
        voucherEntryMapper.insert(cr);
        if (subjectOutputTax != null && totalTax.compareTo(BigDecimal.ZERO) > 0) {
            VoucherEntryEntity taxCr = new VoucherEntryEntity();
            taxCr.setVoucherId(voucher.getId());
            taxCr.setSubjectId(subjectOutputTax.getId());
            taxCr.setDebit(BigDecimal.ZERO);
            taxCr.setCredit(totalTax);
            taxCr.setSummary("合并发票: " + invoiceNoList);
            taxCr.setSortOrder(sort);
            voucherEntryMapper.insert(taxCr);
        }
        for (OutputInvoiceEntity inv : invoices) {
            inv.setProcessStatus("PROCESSED");
            inv.setVoucherId(voucher.getId());
            inv.setVoucherNo(voucherNo);
            outputMapper.updateById(inv);
        }
        log.info("批量生成凭证成功: {} 张发票 -> 凭证 {}", invoiceIds.size(), voucherNo);
        return voucherNo;
    }

    // ==================== 销项发票批量操作（P56 best-effort）====================

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchSubmitForReview(List<Long> ids, Long userId) {
        return runBatch(ids, userId, (id, uid) -> {
            outputInvoiceStateMachineService.submitForReview(id, uid);
            return null;
        }, "批量提交审核");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchConfirm(List<Long> ids, Long userId) {
        return runBatch(ids, userId, (id, uid) -> {
            outputInvoiceStateMachineService.confirm(id, uid);
            return null;
        }, "批量审核通过");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchReject(List<Long> ids, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("驳回必须填写原因");
        }
        return runBatch(ids, userId, (id, uid) -> {
            outputInvoiceStateMachineService.reject(id, uid, reason);
            return null;
        }, "批量驳回");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchRevert(List<Long> ids, Long userId) {
        return runBatch(ids, userId, (id, uid) -> {
            outputInvoiceStateMachineService.revertToReview(id, uid);
            return null;
        }, "批量回退");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchMarkVouchered(List<Long> ids, Long userId) {
        return runBatch(ids, userId, (id, uid) -> {
            generateVoucherFromInvoice(id, uid);
            return null;
        }, "批量生成凭证");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchVoid(List<Long> ids, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("作废必须填写原因");
        }
        return runBatch(ids, userId, (id, uid) -> {
            outputInvoiceStateMachineService.voidInvoice(id, uid, reason);
            return null;
        }, "批量作废");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BatchOperationResult batchReverse(List<Long> ids, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("红冲必须填写原因");
        }
        return runBatch(ids, userId, (id, uid) -> {
            outputInvoiceStateMachineService.reverseInvoice(id, uid, reason);
            return null;
        }, "批量红冲");
    }

    @FunctionalInterface
    private interface BatchInvoker {
        Long invoke(Long id, Long userId);
    }

    /**
     * best-effort 批量执行器：单条失败计入 failure，继续后续；最外层不开事务
     * 让每条状态机调用自身事务独立提交。
     */
    private BatchOperationResult runBatch(List<Long> ids, Long userId, BatchInvoker invoker, String actionLabel) {
        List<Long> success = new ArrayList<>();
        List<BatchOperationResult.FailureDetail> failure = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return new BatchOperationResult(success, failure);
        }
        for (Long id : ids) {
            try {
                invoker.invoke(id, userId);
                success.add(id);
            } catch (BusinessException e) {
                failure.add(new BatchOperationResult.FailureDetail(id, e.getMessage()));
                log.warn("{} 失败: id={}, reason={}", actionLabel, id, e.getMessage());
            } catch (Exception e) {
                failure.add(new BatchOperationResult.FailureDetail(id, "系统异常: " + e.getMessage()));
                log.error("{} 系统异常: id={}", actionLabel, id, e);
            }
        }
        log.info("{} 完成: 成功={}, 失败={}", actionLabel, success.size(), failure.size());
        return new BatchOperationResult(success, failure);
    }
}
