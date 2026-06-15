package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.ReconciliationService;
import com.huicai.module.finance.entity.*;
import com.huicai.module.finance.mapper.*;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 银行流水自动生成单据与凭证 — 实现 4.13 章节设计的自动生单管道.
 * <p>
 * 分类路由:
 * - A类 (直接制证): bank_fee, interest_income, tax_payment, social_security, insurance_fee
 * - B类 (生成业务单据后制证): business_receipt, business_payment, internal_transfer, salary_payment
 * - C类 (待人工): pending
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoGenerationService {

    private static final long DEFAULT_USER_ID = 1L;
    private static final long DEFAULT_VOUCHER_TYPE_ID = 1L;

    private final BankStatementMapper statementMapper;
    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectMapper subjectMapper;
    private final VoucherTemplateService voucherTemplateService;
    private final ClassificationRuleMapper classificationRuleMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final ReconciliationService reconciliationService;

    /**
     * 对已确认分类的银行流水执行自动生单/制证.
     * 调用时机: 出纳在 review() 确认后触发.
     *
     * @param statementId 银行流水 ID
     * @param userId      操作人 ID
     * @return true 表示已处理, false 表示无需处理(如 pending 分类)
     */
    @Transactional
    public boolean autoGenerate(Long statementId, Long userId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        if (stmt == null) {
            throw BusinessException.notFound("银行流水不存在: " + statementId);
        }
        if (stmt.getGeneratedVoucherId() != null) {
            log.warn("流水 {} 已生成凭证, 跳过", statementId);
            return false;
        }

        String classification = stmt.getClassification();
        if (StrUtil.isBlank(classification)) {
            log.warn("流水 {} 未分类, 跳过自动生成", statementId);
            return false;
        }

        // 优先使用命中规则的 route_type, 其次使用分类硬编码映射
        String type = null;
        if (stmt.getRuleId() != null) {
            ClassificationRuleEntity rule = classificationRuleMapper.selectById(stmt.getRuleId());
            if (rule != null && StrUtil.isNotBlank(rule.getRouteType())) {
                type = rule.getRouteType();
            }
        }
        if (type == null) {
            type = classifyType(classification);
        }

        switch (type) {
            case "A":
                // A类: 直接生成凭证
                generateVoucherDirect(stmt, userId);
                break;
            case "B":
                // B类: 先生成业务单据, 再生成凭证
                generateDocThenVoucher(stmt, userId);
                break;
            default:
                // C类: 不处理, 留在待认领池
                log.info("流水 {} 分类={}, 需人工处理", statementId, classification);
                return false;
        }

        // 更新流水状态
        stmt.setGeneratedAt(LocalDateTime.now());
        statementMapper.updateById(stmt);

        log.info("自动生成完成: statementId={}, classification={}, docId={}, voucherId={}",
                statementId, classification, stmt.getGeneratedDocId(), stmt.getGeneratedVoucherId());
        return true;
    }

    // ─── A类: 直接生成凭证 ───

    private void generateVoucherDirect(BankStatementEntity stmt, Long userId) {
        BigDecimal amount = stmt.getAmount().abs();
        String direction = stmt.getDirection();
        String period = stmt.getTxDate().format(DateTimeFormatter.ofPattern("yyyyMM"));

        // 1. 尝试按模板生成 (配置驱动)
        VoucherTemplateEntity template = voucherTemplateService.matchByClassification(stmt.getClassification());
        if (template != null) {
            List<VoucherTemplateLineEntity> lines = voucherTemplateService.getLines(template.getId());
            if (lines != null && !lines.isEmpty()) {
                generateVoucherFromTemplate(template, lines, stmt, period, amount, userId);
                log.info("模板制证: statementId={}, templateId={}, classification={}",
                        stmt.getId(), template.getId(), stmt.getClassification());
                return;
            }
        }

        // 2. 降级: 硬编码逻辑 (向后兼容)
        log.warn("流水分类 {} 无激活模板, 回退硬编码", stmt.getClassification());

        // 查找科目
        Subject bankAcct = findSubjectByCode("1002");

        Subject debitAcct = null;
        Subject creditAcct = null;

        switch (stmt.getClassification()) {
            case "bank_fee": {
                debitAcct = findSubjectByCode("6602.01");
                creditAcct = bankAcct;
                break;
            }
            case "interest_income": {
                debitAcct = bankAcct;
                creditAcct = findSubjectByCode("6602.02");
                break;
            }
            case "tax_payment": {
                debitAcct = findSubjectByCode("2221");
                creditAcct = bankAcct;
                break;
            }
            case "social_security": {
                Subject socialAcct = findSubjectByCode("2211.04");
                debitAcct = socialAcct != null ? socialAcct : findSubjectByCode("2211");
                creditAcct = bankAcct;
                break;
            }
            case "insurance_fee": {
                debitAcct = findSubjectByCode("6602.06");
                creditAcct = bankAcct;
                break;
            }
        }

        if (debitAcct == null || creditAcct == null) {
            throw new BusinessException(500, "缺少科目配置, 无法生成凭证. classification=" + stmt.getClassification());
        }

        VoucherEntity voucher = createVoucher(stmt, period, amount, userId);
        int sort = 1;

        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(debitAcct.getId());
        entryDr.setDebit("in".equals(direction) ? BigDecimal.ZERO : amount);
        entryDr.setCredit("in".equals(direction) ? amount : BigDecimal.ZERO);
        entryDr.setSummary(stmt.getSummary());
        entryDr.setSortOrder(sort++);
        voucherEntryMapper.insert(entryDr);

        VoucherEntryEntity entryCr = new VoucherEntryEntity();
        entryCr.setVoucherId(voucher.getId());
        entryCr.setSubjectId(creditAcct.getId());
        entryCr.setDebit("in".equals(direction) ? amount : BigDecimal.ZERO);
        entryCr.setCredit("in".equals(direction) ? BigDecimal.ZERO : amount);
        entryCr.setSummary(stmt.getSummary());
        entryCr.setSortOrder(sort++);
        voucherEntryMapper.insert(entryCr);

        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        voucherMapper.updateById(voucher);

        stmt.setGeneratedVoucherId(voucher.getId());

        log.info("A类硬编码制证: statementId={}, voucherId={}, classification={}",
                stmt.getId(), voucher.getId(), stmt.getClassification());
    }

    /**
     * 根据模板 + 分录行生成凭证.
     */
    private void generateVoucherFromTemplate(VoucherTemplateEntity template,
                                              List<VoucherTemplateLineEntity> lines,
                                              BankStatementEntity stmt,
                                              String period,
                                              BigDecimal amount,
                                              Long userId) {
        VoucherEntity voucher = createVoucher(stmt, period, amount, userId);
        voucher.setTemplateId(template.getId());
        voucherMapper.updateById(voucher);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int sort = 1;

        for (VoucherTemplateLineEntity line : lines) {
            // 解析金额
            BigDecimal dr = resolveAmount(line.getDrAmountTemplate(), amount, stmt);
            BigDecimal cr = resolveAmount(line.getCrAmountTemplate(), amount, stmt);
            if (dr == null) dr = BigDecimal.ZERO;
            if (cr == null) cr = BigDecimal.ZERO;

            // 方向约束: 如果行标记为 debit, 确保 cr=0; credit 行则 dr=0
            if ("debit".equals(line.getDirection())) {
                if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) > 0) {
                    dr = cr;
                    cr = BigDecimal.ZERO;
                } else {
                    cr = BigDecimal.ZERO;
                }
            } else if ("credit".equals(line.getDirection())) {
                if (cr.compareTo(BigDecimal.ZERO) == 0 && dr.compareTo(BigDecimal.ZERO) > 0) {
                    cr = dr;
                    dr = BigDecimal.ZERO;
                } else {
                    dr = BigDecimal.ZERO;
                }
            }

            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) {
                continue; // 跳过 0 金额行
            }

            // 解析摘要
            String summary = resolveSummary(line.getSummaryTemplate(), stmt);

            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setVoucherId(voucher.getId());
            entry.setSubjectId(line.getSubjectId());
            entry.setDebit(dr);
            entry.setCredit(cr);
            entry.setSummary(summary);
            entry.setSortOrder(sort++);
            voucherEntryMapper.insert(entry);

            totalDebit = totalDebit.add(dr);
            totalCredit = totalCredit.add(cr);
        }

        // 更新借贷合计
        BigDecimal maxAmt = totalDebit.max(totalCredit);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);

        stmt.setGeneratedVoucherId(voucher.getId());

        log.info("A类模板制证: statementId={}, voucherId={}, templateId={}, classification={}",
                stmt.getId(), voucher.getId(), template.getId(), stmt.getClassification());
    }

    // ─── B类: 先生成业务单据, 再生成凭证 ───

    private void generateDocThenVoucher(BankStatementEntity stmt, Long userId) {
        BigDecimal amount = stmt.getAmount().abs();
        String period = stmt.getTxDate().format(DateTimeFormatter.ofPattern("yyyyMM"));

        // 1. 生成业务单据
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateDocNo(stmt.getClassification(), period));
        doc.setDocType(mapToDocType(stmt.getClassification()));
        doc.setDocDate(stmt.getTxDate());
        doc.setPeriod(period);
        doc.setAmount(amount);
        doc.setSummary(stmt.getSummary());
        doc.setStatus("DRAFT");
        doc.setSource("FROM_BANK_TXN");
        doc.setCreatedBy(userId);
        docMapper.insert(doc);

        // 2. 尝试模板制证
        VoucherTemplateEntity template = voucherTemplateService.matchByClassification(stmt.getClassification());
        if (template != null) {
            List<VoucherTemplateLineEntity> lines = voucherTemplateService.getLines(template.getId());
            if (lines != null && !lines.isEmpty()) {
                generateVoucherFromTemplate(template, lines, stmt, period, amount, userId);
                doc.setVoucherId(stmt.getGeneratedVoucherId());
                doc.setStatus("VOUCHERED");
                doc.setUpdatedAt(LocalDateTime.now());
                docMapper.updateById(doc);
                stmt.setGeneratedDocId(doc.getId());
                // P10-3: 模板路径也生成应收/应付单
                doc.setSupplierId(null);
                doc.setCustomerId(null);
                createReceivableOrPayableFromBankDoc(doc, stmt, period, amount);
                docMapper.updateById(doc);
                log.info("B类模板制证: statementId={}, docId={}, voucherId={}, templateId={}",
                        stmt.getId(), doc.getId(), stmt.getGeneratedVoucherId(), template.getId());
                return;
            }
        }

        // 3. 降级: 硬编码
        log.warn("流水分类 {} 无激活模板, 回退硬编码", stmt.getClassification());
        Subject bankAcct = findSubjectByCode("1002");

        VoucherEntity voucher = createVoucher(stmt, period, amount, userId);
        int sort = 1;

        switch (stmt.getClassification()) {
            case "business_receipt": {
                Subject arAcct = findSubjectByCode("1122");
                addVoucherEntry(voucher.getId(), bankAcct.getId(), amount, BigDecimal.ZERO, stmt.getSummary(), sort++);
                addVoucherEntry(voucher.getId(), arAcct.getId(), BigDecimal.ZERO, amount, stmt.getSummary(), sort++);
                break;
            }
            case "business_payment": {
                Subject apAcct = findSubjectByCode("2202");
                addVoucherEntry(voucher.getId(), apAcct.getId(), amount, BigDecimal.ZERO, stmt.getSummary(), sort++);
                addVoucherEntry(voucher.getId(), bankAcct.getId(), BigDecimal.ZERO, amount, stmt.getSummary(), sort++);
                break;
            }
            case "internal_transfer": {
                Subject otherAcct = findSubjectByCode("1012");
                if (otherAcct == null) otherAcct = findSubjectByCode("1221");
                addVoucherEntry(voucher.getId(), otherAcct.getId(), amount, BigDecimal.ZERO, stmt.getSummary(), sort++);
                addVoucherEntry(voucher.getId(), bankAcct.getId(), BigDecimal.ZERO, amount, stmt.getSummary(), sort++);
                break;
            }
            case "salary_payment": {
                Subject salaryAcct = findSubjectByCode("2211");
                addVoucherEntry(voucher.getId(), salaryAcct.getId(), amount, BigDecimal.ZERO, stmt.getSummary(), sort++);
                addVoucherEntry(voucher.getId(), bankAcct.getId(), BigDecimal.ZERO, amount, stmt.getSummary(), sort++);
                break;
            }
        }

        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        voucherMapper.updateById(voucher);

        doc.setVoucherId(voucher.getId());
        doc.setStatus("VOUCHERED");
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);

        stmt.setGeneratedDocId(doc.getId());
        stmt.setGeneratedVoucherId(voucher.getId());

        // P10-3: 硬编码路径生成应收/应付单
        doc.setSupplierId(null);
        doc.setCustomerId(null);
        createReceivableOrPayableFromBankDoc(doc, stmt, period, amount);
        docMapper.updateById(doc);

        log.info("B类硬编码生单+制证: statementId={}, docId={}, voucherId={}, classification={}",
                stmt.getId(), doc.getId(), voucher.getId(), stmt.getClassification());
    }

    // ─── 辅助方法 ───

    private VoucherEntity createVoucher(BankStatementEntity stmt, String period, BigDecimal amount, Long userId) {
        String voucherNo = voucherNoService.generateNextNo(period, DEFAULT_VOUCHER_TYPE_ID);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(DEFAULT_VOUCHER_TYPE_ID);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(stmt.getSummary() != null ? stmt.getSummary() : "银行流水自动生成");
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        voucher.setCreatedBy(userId);
        voucherMapper.insert(voucher);
        return voucher;
    }

    private void addVoucherEntry(Long voucherId, Long subjectId, BigDecimal debit, BigDecimal credit,
                                  String summary, int sortOrder) {
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(voucherId);
        entry.setSubjectId(subjectId);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setSummary(summary);
        entry.setSortOrder(sortOrder);
        voucherEntryMapper.insert(entry);
    }

    /**
     * 解析模板金额表达式.
     * 支持: {{amount}} — 流水的交易金额
     *       {{taxAmount}} — 保留, 暂为 0
     *       纯数字 — 直接使用
     *       空或 null — 返回 0
     */
    private BigDecimal resolveAmount(String template, BigDecimal amount, BankStatementEntity stmt) {
        if (StrUtil.isBlank(template)) return BigDecimal.ZERO;
        String expr = template.trim();

        if ("{{amount}}".equals(expr)) return amount;
        if ("{{taxAmount}}".equals(expr)) return BigDecimal.ZERO;

        // 纯数字
        try {
            return new BigDecimal(expr);
        } catch (NumberFormatException e) {
            log.warn("无法解析金额模板 '{}', 返回 0", expr);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析摘要模板.
     * 支持: {{summary}} — 替换为流水的摘要
     *       纯文本 — 直接返回
     *       空 — 返回流水摘要
     */
    private String resolveSummary(String template, BankStatementEntity stmt) {
        if (StrUtil.isBlank(template)) {
            return stmt.getSummary() != null ? stmt.getSummary() : "";
        }
        String result = template.replace("{{summary}}",
                stmt.getSummary() != null ? stmt.getSummary() : "");
        result = result.replace("{{counterAccount}}",
                stmt.getCounterAccount() != null ? stmt.getCounterAccount() : "");
        return result;
    }

    private Subject findSubjectByCode(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private String generateDocNo(String classification, String period) {
        return mapToDocType(classification) + period + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    private String mapToDocType(String classification) {
        return switch (classification) {
            case "business_receipt" -> "RECEIPT";
            case "business_payment", "salary_payment", "social_security" -> "PAYMENT";
            case "internal_transfer" -> "TRANSFER";
            default -> "EXPENSE";
        };
    }

    /**
     * P10-3: 按对方名称从客商档案查找客户/供应商 ID.
     */
    private Long findCustomerByName(String name) {
        if (StrUtil.isBlank(name)) return null;
        List<com.huicai.module.arap.entity.CustomerEntity> list = customerMapper.selectList(
                new LambdaQueryWrapper<com.huicai.module.arap.entity.CustomerEntity>()
                        .eq(com.huicai.module.arap.entity.CustomerEntity::getName, name)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private Long findVendorByName(String name) {
        if (StrUtil.isBlank(name)) return null;
        List<com.huicai.module.arap.entity.VendorEntity> list = vendorMapper.selectList(
                new LambdaQueryWrapper<com.huicai.module.arap.entity.VendorEntity>()
                        .eq(com.huicai.module.arap.entity.VendorEntity::getName, name)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /**
     * P10-3: 银行流水 B 类确认后自动生成应收/应付单.
     * 在 generateDocThenVoucher 的模板/硬编码路径最后调用.
     */
    private void createReceivableOrPayableFromBankDoc(BusinessDocEntity doc, BankStatementEntity stmt,
                                                       String period, BigDecimal amount) {
        String classification = stmt.getClassification();
        String counterName = stmt.getCounterAccount();

        if ("business_receipt".equals(classification)) {
            Long customerId = findCustomerByName(counterName);
            if (customerId == null) {
                log.warn("P10-3 应收单跳过: 客户名 '{}' 无法匹配", counterName);
                return;
            }
            ReceivableEntity recv = new ReceivableEntity();
            recv.setCustomerId(customerId);
            recv.setDocId(doc.getId());
            recv.setVoucherId(doc.getVoucherId());
            recv.setPeriod(period);
            recv.setTxDate(stmt.getTxDate());
            recv.setAmount(amount);
            recv.setSettledAmount(BigDecimal.ZERO);
            recv.setUnsettledAmount(amount);
            recv.setSummary(stmt.getSummary());
            receivableMapper.insert(recv);
            doc.setCustomerId(customerId);
            log.info("P10-3 应收单生成: statementId={}, customerId={}, docId={}, amount={}",
                    stmt.getId(), customerId, doc.getId(), amount);

            // P10-4: 自动核销应收（停在 CONFIRMED 状态）
            try {
                reconciliationService.execute(new ReconciliationService.ExecuteRequest(
                        "bank_txn", stmt.getId(),
                        "INVOICE_OUT", recv.getId(),
                        amount, new BigDecimal("100"),
                        "AUTO", customerId, null,
                        period, "P10-4 银行流水自动核销"
                ));
                log.info("P10-4 应收自动核销完成: statementId={}, receivableId={}, amount={}",
                        stmt.getId(), recv.getId(), amount);
            } catch (Exception e) {
                log.warn("P10-4 应收自动核销失败(不影响主流程): {}", e.getMessage());
            }
        } else if ("business_payment".equals(classification)) {
            Long vendorId = findVendorByName(counterName);
            if (vendorId == null) {
                log.warn("P10-3 应付单跳过: 供应商名 '{}' 无法匹配", counterName);
                return;
            }
            PayableEntity pay = new PayableEntity();
            pay.setVendorId(vendorId);
            pay.setDocId(doc.getId());
            pay.setVoucherId(doc.getVoucherId());
            pay.setPeriod(period);
            pay.setTxDate(stmt.getTxDate());
            pay.setAmount(amount);
            pay.setSettledAmount(BigDecimal.ZERO);
            pay.setUnsettledAmount(amount);
            pay.setSummary(stmt.getSummary());
            payableMapper.insert(pay);
            doc.setSupplierId(vendorId);
            log.info("P10-3 应付单生成: statementId={}, vendorId={}, docId={}, amount={}",
                    stmt.getId(), vendorId, doc.getId(), amount);

            // P10-4: 自动核销应付（停在 CONFIRMED 状态）
            try {
                reconciliationService.execute(new ReconciliationService.ExecuteRequest(
                        "bank_txn", stmt.getId(),
                        "INVOICE_IN", pay.getId(),
                        amount, new BigDecimal("100"),
                        "AUTO", null, vendorId,
                        period, "P10-4 银行流水自动核销"
                ));
                log.info("P10-4 应付自动核销完成: statementId={}, payableId={}, amount={}",
                        stmt.getId(), pay.getId(), amount);
            } catch (Exception e) {
                log.warn("P10-4 应付自动核销失败(不影响主流程): {}", e.getMessage());
            }
        } else {
            // internal_transfer / salary_payment 等不走应收/应付
            log.debug("P10-3 跳过: classification={}, 不需要应收/应付单", classification);
        }
    }

    public static String classifyType(String classification) {
        return switch (classification) {
            case "bank_fee", "interest_income", "tax_payment",
                 "social_security", "insurance_fee" -> "A";
            case "business_receipt", "business_payment",
                 "internal_transfer", "salary_payment" -> "B";
            default -> "C";
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoGenerateInNewTx(Long statementId, Long userId) {
        return autoGenerate(statementId, userId);
    }
}
