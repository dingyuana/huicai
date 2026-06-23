package com.huicai.module.tax.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.entity.TaxDeclarationEntity;
import com.huicai.module.tax.entity.TaxTypeEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.mapper.TaxDeclarationMapper;
import com.huicai.module.tax.mapper.TaxTypeMapper;
import com.huicai.module.tax.service.OutputInvoiceStateMachineService;
import com.huicai.module.tax.service.TaxService;
import com.huicai.module.finance.service.TemplateMatcher;
import com.huicai.common.util.TemplateEngine;
import com.huicai.common.util.TemplateContext;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import com.huicai.module.finance.service.VoucherTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private final OutputInvoiceStateMachineService stateMachineService;
    private final TemplateMatcher templateMatcher;
    private final VoucherTemplateService voucherTemplateService;

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
        return inputMapper.selectPage(page, wrapper);
    }

    @Override
    public InputInvoiceEntity createInput(InputInvoiceEntity entity) {
        // 校验金额
        if (entity.getAmount() == null || entity.getTaxRate() == null) {
            throw new BusinessException("金额和税率不能为空");
        }
        if (entity.getTaxAmount() == null) {
            entity.setTaxAmount(entity.getAmount().multiply(entity.getTaxRate())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (entity.getTotalAmount() == null) {
            entity.setTotalAmount(entity.getAmount().add(entity.getTaxAmount()));
        }
        if (entity.getCertificationStatus() == null) {
            entity.setCertificationStatus("UNCERTIFIED");
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
                                                       Integer current, Integer size) {
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
        wrapper.orderByDesc(OutputInvoiceEntity::getInvoiceDate);
        return outputMapper.selectPage(page, wrapper);
    }

    @Override
    public OutputInvoiceEntity getOutputById(Long id) {
        OutputInvoiceEntity invoice = outputMapper.selectById(id);
        if (invoice == null) {
            return null;
        }
        
        if (StrUtil.isNotBlank(invoice.getOriginalInvoiceNo())) {
            OutputInvoiceEntity original = outputMapper.selectOne(
                    new LambdaQueryWrapper<OutputInvoiceEntity>()
                            .eq(OutputInvoiceEntity::getInvoiceNo, invoice.getOriginalInvoiceNo())
                            .last("LIMIT 1")
            );
            if (original != null) {
                invoice.setOriginalInvoiceId(original.getId());
            }
        }
        
        if (invoice.getReversedByInvoiceId() != null) {
            OutputInvoiceEntity redInvoice = outputMapper.selectById(invoice.getReversedByInvoiceId());
            if (redInvoice != null) {
                invoice.setReversedByInvoiceNo(redInvoice.getInvoiceNo());
            }
        }
        
        return invoice;
    }

    @Override
    public OutputInvoiceEntity createOutput(OutputInvoiceEntity entity) {
        if (entity.getAmount() == null || entity.getTaxRate() == null) {
            throw new BusinessException("金额和税率不能为空");
        }
        if (entity.getTaxAmount() == null) {
            entity.setTaxAmount(entity.getAmount().multiply(entity.getTaxRate())
                    .setScale(2, RoundingMode.HALF_UP));
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
        VoucherTemplateEntity template = templateMatcher.match(ctx);
        if (template != null) {
            List<VoucherTemplateLineEntity> tplLines = voucherTemplateService.getLines(template.getId());
            if (tplLines != null && !tplLines.isEmpty()) {
                generateFromTemplate(inv, template, tplLines, ctx, userId);
                return;
            }
        }

        // 2. 降级: 硬编码科目
        String voucherNo = voucherNoService.generateNextNo(inv.getPeriod(), VOUCHER_TYPE_ID);
        Subject subjectAr = findSubject("1122");
        Subject subjectRevenue = findSubject("5001");
        Subject subjectOutputTax = findSubject("2221.01");
        if (subjectAr == null || subjectRevenue == null) {
            throw new BusinessException(500, "缺少基础科目配置(1122/5001)");
        }

        BigDecimal exclTax = inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
        BigDecimal taxAmt = inv.getTaxAmount() != null ? inv.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal totalAmt = inv.getTotalAmount() != null ? inv.getTotalAmount() : exclTax.add(taxAmt);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(inv.getPeriod());
        voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("发票转凭证: " + inv.getInvoiceNo());
        voucher.setTotalDebit(totalAmt);
        voucher.setTotalCredit(totalAmt);
        voucher.setCreatedBy(userId);
        voucherMapper.insert(voucher);

        int sort = 1;
        // 借：应收账款 1122
        VoucherEntryEntity dr = new VoucherEntryEntity();
        dr.setVoucherId(voucher.getId());
        dr.setSubjectId(subjectAr.getId());
        dr.setDebit(totalAmt);
        dr.setCredit(BigDecimal.ZERO);
        dr.setSummary(inv.getCustomerName());
        dr.setSortOrder(sort++);
        voucherEntryMapper.insert(dr);

        // 贷：主营业务收入 5001
        VoucherEntryEntity cr1 = new VoucherEntryEntity();
        cr1.setVoucherId(voucher.getId());
        cr1.setSubjectId(subjectRevenue.getId());
        cr1.setDebit(BigDecimal.ZERO);
        cr1.setCredit(exclTax);
        cr1.setSummary(inv.getCustomerName());
        cr1.setSortOrder(sort++);
        voucherEntryMapper.insert(cr1);

        // 贷：销项税 2221.01
        if (subjectOutputTax != null && taxAmt.compareTo(BigDecimal.ZERO) != 0) {
            VoucherEntryEntity cr2 = new VoucherEntryEntity();
            cr2.setVoucherId(voucher.getId());
            cr2.setSubjectId(subjectOutputTax.getId());
            cr2.setDebit(BigDecimal.ZERO);
            cr2.setCredit(taxAmt);
            cr2.setSummary(inv.getCustomerName());
            cr2.setSortOrder(sort++);
            voucherEntryMapper.insert(cr2);
        }

        stateMachineService.markVouchered(invoiceId, voucher.getId(), userId);
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
        voucher.setSummary(TemplateEngine.renderSummary("发票转凭证: {客户名称}", ctx));
        voucher.setTemplateId(template.getId());
        voucher.setCreatedBy(userId);
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

        stateMachineService.markVouchered(invoiceId, voucher.getId(), userId);
        log.info("发票模板制证: invoiceId={}, voucherId={}, templateId={}", invoiceId, voucher.getId(), template.getId());
    }

    private Subject findSubject(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
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
    public TaxDeclarationEntity approveDeclaration(Long id, String approver) {
        TaxDeclarationEntity entity = declarationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("申报记录不存在");
        }
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw new BusinessException("仅已提交状态可审批: 当前=" + entity.getStatus());
        }
        entity.setStatus("APPROVED");
        entity.setUpdatedBy(approver == null ? null : Long.valueOf(approver.hashCode() & 0x7FFFFFFF));
        declarationMapper.updateById(entity);
        log.info("P18-1 申报审批通过: id={}, approver={}", id, approver);
        return entity;
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

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString());
    }
}
