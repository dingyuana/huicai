package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.dto.ExpenseReimbursementVO;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.*;
import com.huicai.module.arap.service.EmployeeService;
import com.huicai.module.arap.service.ExpenseReimbursementService;
import com.huicai.module.arap.service.ReconciliationService;
import com.huicai.module.finance.constant.BankClassification;
import com.huicai.module.finance.constant.StatementStatus;
import com.huicai.module.finance.entity.*;
import com.huicai.module.finance.mapper.*;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.finance.service.TemplateMatcher;
import com.huicai.common.util.TemplateEngine;
import com.huicai.common.util.TemplateContext;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 银行流水自动生成单据与凭证 — 实现 4.13 章节设计的自动生单管道.
 * <p>
 * 分类路由 (新8类体系):
 * - A类 (直接制证): bank_interest_fee, tax_withholding
 * - B类 (生成业务单据后制证): business_receipt, business_payment, internal_transfer, salary_social
 * - C类 (待人工): financing_invest, other_unknown
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
    private final PrepaymentMapper prepaymentMapper;
    private final ReconciliationService reconciliationService;
    private final EmployeeService employeeService;
    private final ExpenseReimbursementService expenseReimbursementService;
    private final TemplateMatcher templateMatcher;

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

        // 状态守卫：上层调用方（generateVoucher/processManual）已做前置校验;
        // 这里只拦截明确不允许制证的非法状态
        String revStatus = stmt.getReviewStatus();
        if (StatementStatus.PENDING.equals(revStatus)) {
            log.warn("流水 {} 状态为 PENDING, 跳过自动生成", statementId);
            return false;
        }
        if (StatementStatus.APPROVED.equals(revStatus)) {
            log.warn("流水 {} 已过账, 禁止重复制证, reviewStatus={}", statementId, revStatus);
            return false;
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
            type = BankClassification.routeType(classification);
        }

        // P11-3: 银行流水 → 员工匹配. 若 counterAccount 匹配到员工, 创建报销单草稿
        if ("out".equalsIgnoreCase(stmt.getDirection()) && StrUtil.isNotBlank(stmt.getCounterAccount())) {
            EmployeeEntity emp = employeeService.findByName(stmt.getCounterAccount());
            if (emp != null) {
                ExpenseReimbursementVO reimb = expenseReimbursementService.autoCreateForBankStmt(
                        stmt.getId(), emp.getId(), stmt.getAmount().abs(), stmt.getSummary());
                log.info("P11-3 自动建报销单: statementId={}, employeeId={}, reimbId={}, amount={}",
                        stmt.getId(), emp.getId(), reimb.getId(), stmt.getAmount().abs());
                // 报销单停在 DRAFT, 等人提交 → 审批 → 生成凭证. 流水也标为已处理
                stmt.setGeneratedAt(LocalDateTime.now());
                statementMapper.updateById(stmt);
                return true;
            }
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

        // 1. 按模板生成（配置驱动）
        TemplateContext ctx = new TemplateContext()
                .setSource("BANK_STMT")
                .setClassification(stmt.getClassification())
                .setDirection(stmt.getDirection())
                .setAmount(amount)
                .setPeriod(period)
                .setSummary(stmt.getSummary())
                .setCounterpartyName(stmt.getCounterAccount());
        VoucherTemplateEntity template = templateMatcher.match(ctx);
        if (template != null) {
            List<VoucherTemplateLineEntity> lines = voucherTemplateService.getLines(template.getId());
            if (lines != null && !lines.isEmpty()) {
                generateVoucherFromTemplate(template, lines, ctx, stmt, userId);
                log.info("模板制证: statementId={}, templateId={}", stmt.getId(), template.getId());
                return;
            }
        }

        // 2. 降级: 硬编码
        log.warn("流水分类 {} 无激活模板, 回退硬编码", stmt.getClassification());
        Subject bankAcct = findSubjectByCode("1002");

        Subject debitAcct = null;
        Subject creditAcct = null;

        switch (stmt.getClassification()) {
            case BankClassification.BANK_INTEREST_FEE: {
                // 银行利息与手续费：借方根据方向判断
                if ("in".equals(direction)) {
                    debitAcct = bankAcct;
                    creditAcct = findSubjectByCode("6602.02"); // 利息收入
                } else {
                    debitAcct = findSubjectByCode("6602.01"); // 手续费
                    creditAcct = bankAcct;
                }
                break;
            }
            case BankClassification.TAX_WITHHOLDING: {
                debitAcct = findSubjectByCode("2221");
                creditAcct = bankAcct;
                break;
            }
            case BankClassification.SALARY_SOCIAL: {
                Subject salaryAcct = findSubjectByCode("2211");
                debitAcct = salaryAcct != null ? salaryAcct : findSubjectByCode("2211");
                creditAcct = bankAcct;
                break;
            }
        }

        if (debitAcct == null || creditAcct == null) {
            throw new BusinessException(500, "缺少科目配置, 无法生成凭证. classification=" + stmt.getClassification());
        }

        VoucherEntity voucher = createVoucher(stmt, period, amount, userId);
        // P19: 分录摘要同样追加对方名称
        String entrySummary = buildEntrySummary(stmt);
        int sort = 1;

        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(debitAcct.getId());
        entryDr.setDebit("in".equals(direction) ? BigDecimal.ZERO : amount);
        entryDr.setCredit("in".equals(direction) ? amount : BigDecimal.ZERO);
        entryDr.setSummary(entrySummary);
        entryDr.setSortOrder(sort++);
        voucherEntryMapper.insert(entryDr);

        VoucherEntryEntity entryCr = new VoucherEntryEntity();
        entryCr.setVoucherId(voucher.getId());
        entryCr.setSubjectId(creditAcct.getId());
        entryCr.setDebit("in".equals(direction) ? amount : BigDecimal.ZERO);
        entryCr.setCredit("in".equals(direction) ? BigDecimal.ZERO : amount);
        entryCr.setSummary(entrySummary);
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
                                              TemplateContext ctx,
                                              BankStatementEntity stmt,
                                              Long userId) {
        VoucherEntity voucher = createVoucher(stmt, ctx.getPeriod(), ctx.getAmount(), userId);
        voucher.setTemplateId(template.getId());
        voucherMapper.updateById(voucher);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int sort = 1;

        for (VoucherTemplateLineEntity line : lines) {
            BigDecimal dr = TemplateEngine.renderAmount(line.getDrAmountTemplate(), ctx);
            BigDecimal cr = TemplateEngine.renderAmount(line.getCrAmountTemplate(), ctx);
            if (dr == null) dr = BigDecimal.ZERO;
            if (cr == null) cr = BigDecimal.ZERO;

            if ("debit".equals(line.getDirection())) {
                if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) > 0) { dr = cr; cr = BigDecimal.ZERO; }
                else { cr = BigDecimal.ZERO; }
            } else if ("credit".equals(line.getDirection())) {
                if (cr.compareTo(BigDecimal.ZERO) == 0 && dr.compareTo(BigDecimal.ZERO) > 0) { cr = dr; dr = BigDecimal.ZERO; }
                else { dr = BigDecimal.ZERO; }
            }

            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

            String summary = TemplateEngine.renderSummary(line.getSummaryTemplate(), ctx);

            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setVoucherId(voucher.getId());
            entry.setSubjectId(line.getSubjectId());
            entry.setDebit(dr); entry.setCredit(cr);
            entry.setSummary(summary);
            entry.setSortOrder(sort++);
            voucherEntryMapper.insert(entry);
            totalDebit = totalDebit.add(dr);
            totalCredit = totalCredit.add(cr);
        }

        BigDecimal maxAmt = totalDebit.max(totalCredit);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);
        stmt.setGeneratedVoucherId(voucher.getId());
        log.info("模板制证(ctx): statementId={}, voucherId={}, templateId={}",
                stmt.getId(), voucher.getId(), template.getId());
    }

    /**
     * 根据模板 + 分录行生成凭证 (旧版, 兼容 generateDocThenVoucher).
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

        // P19: 从银行流水的对方名称解析客户/供应商 ID, 不存在则自动创建档案
        String counterName = stmt.getCounterAccount();
        if (StrUtil.isNotBlank(counterName)) {
            String docType = doc.getDocType();
            if ("RECEIPT".equals(docType)) {
                Long customerId = findCustomerByName(counterName);
                if (customerId != null) {
                    doc.setCustomerId(customerId);
                } else {
                    CustomerEntity newCust = new CustomerEntity();
                    newCust.setName(counterName);
                    newCust.setCode("AUTO-" + System.currentTimeMillis());
                    newCust.setIsActive(true);
                    newCust.setRemark("银行流水自动创建");
                    customerMapper.insert(newCust);
                    doc.setCustomerId(newCust.getId());
                    log.info("P19 自动创建客户: name={}, id={}", counterName, newCust.getId());
                }
            } else if ("PAYMENT".equals(docType)) {
                Long vendorId = findVendorByName(counterName);
                if (vendorId != null) {
                    doc.setSupplierId(vendorId);
                } else {
                    VendorEntity newVend = new VendorEntity();
                    newVend.setName(counterName);
                    newVend.setCode("AUTO-" + System.currentTimeMillis());
                    newVend.setIsActive(true);
                    newVend.setRemark("银行流水自动创建");
                    vendorMapper.insert(newVend);
                    doc.setSupplierId(newVend.getId());
                    log.info("P19 自动创建供应商: name={}, id={}", counterName, newVend.getId());
                }
            }
        }

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
                // P10-3: 模板路径也生成应收/应付单 (ID 已在前面解析, 不会为 null)
                createReceivableOrPayableFromBankDoc(doc, stmt, period, amount, userId);
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
        String entrySummary = buildEntrySummary(stmt);
        int sort = 1;

        switch (stmt.getClassification()) {
            case BankClassification.BUSINESS_RECEIPT: {
                Subject arAcct = findSubjectByCode("1122");
                addVoucherEntry(voucher.getId(), bankAcct.getId(), amount, BigDecimal.ZERO, entrySummary, sort++);
                addVoucherEntry(voucher.getId(), arAcct.getId(), BigDecimal.ZERO, amount, entrySummary, sort++);
                break;
            }
            case BankClassification.BUSINESS_PAYMENT: {
                Subject apAcct = findSubjectByCode("2202");
                addVoucherEntry(voucher.getId(), apAcct.getId(), amount, BigDecimal.ZERO, entrySummary, sort++);
                addVoucherEntry(voucher.getId(), bankAcct.getId(), BigDecimal.ZERO, amount, entrySummary, sort++);
                break;
            }
            case BankClassification.INTERNAL_TRANSFER: {
                Subject otherAcct = findSubjectByCode("1012");
                if (otherAcct == null) otherAcct = findSubjectByCode("1221");
                addVoucherEntry(voucher.getId(), otherAcct.getId(), amount, BigDecimal.ZERO, entrySummary, sort++);
                addVoucherEntry(voucher.getId(), bankAcct.getId(), BigDecimal.ZERO, amount, entrySummary, sort++);
                break;
            }
            case BankClassification.SALARY_SOCIAL: {
                Subject salaryAcct = findSubjectByCode("2211");
                addVoucherEntry(voucher.getId(), salaryAcct.getId(), amount, BigDecimal.ZERO, entrySummary, sort++);
                addVoucherEntry(voucher.getId(), bankAcct.getId(), BigDecimal.ZERO, amount, entrySummary, sort++);
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

        // P10-3: 硬编码路径生成应收/应付单 (ID 已在前面解析, 不会为 null)
        createReceivableOrPayableFromBankDoc(doc, stmt, period, amount, userId);
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
        // P19: 凭证摘要追加对方名称, 确保列表/详情可见
        String summary = stmt.getSummary() != null ? stmt.getSummary() : "银行流水自动生成";
        if (StrUtil.isNotBlank(stmt.getCounterAccount())) {
            summary = stmt.getCounterAccount() + "-" + summary;
        }
        voucher.setSummary(summary);
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

    /**
     * 构建分录摘要, 追加对方名称以便在凭证列表/详情中可见.
     */
    private String buildEntrySummary(BankStatementEntity stmt) {
        String base = stmt.getSummary() != null ? stmt.getSummary() : "";
        if (StrUtil.isNotBlank(stmt.getCounterAccount())) {
            return stmt.getCounterAccount() + "-" + base;
        }
        return base;
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
            case BankClassification.BUSINESS_RECEIPT -> "RECEIPT";
            case BankClassification.BUSINESS_PAYMENT, BankClassification.SALARY_SOCIAL -> "PAYMENT";
            case BankClassification.INTERNAL_TRANSFER -> "TRANSFER";
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
                                                       String period, BigDecimal amount, Long userId) {
        String classification = stmt.getClassification();
        String counterName = stmt.getCounterAccount();

        if (BankClassification.BUSINESS_RECEIPT.equals(classification)) {
            // 银行流水代表款项已收，不应创建新的应收单。
            // 检查该客户是否有未结清应收单，有则按 FIFO 核销；无则跳过。
            Long customerId = doc.getCustomerId();
            if (customerId == null) {
                customerId = findCustomerByName(counterName);
            }
            if (customerId == null) {
                log.warn("银行流水收款跳过: 客户名 '{}' 无法匹配", counterName);
                return;
            }
            doc.setCustomerId(customerId);

            if (reconciliationService.hasOpenInvoices("INVOICE_OUT", customerId)) {
                reconciliationService.autoReconcileFifo(
                        customerId, "INVOICE_OUT", amount,
                        "bank_txn", stmt.getId(),
                        period, "银行流水自动核销应收");
                log.info("银行流水收款按 FIFO 核销完成: customerId={}, amount={}, counterName={}",
                        customerId, amount, counterName);
            } else {
                // P12-3: 无未结清应收，走预收款路径
                log.info("P12-3 客户 '{}' 无未结清应收，走预收款路径", counterName);
                PrepaymentEntity prepay = new PrepaymentEntity();
                prepay.setTenantId(1L);
                prepay.setCustomerId(customerId);
                prepay.setDocId(doc.getId());
                prepay.setVoucherId(doc.getVoucherId());
                prepay.setPeriod(period);
                prepay.setTxDate(stmt.getTxDate());
                prepay.setAmount(amount);
                prepay.setSettledAmount(BigDecimal.ZERO);
                prepay.setUnsettledAmount(amount);
                prepay.setSummary(stmt.getSummary());
                prepay.setStatus(ArapStatus.DRAFT);
                prepay.setSourceDocType("bank_txn");
                prepay.setSourceDocId(stmt.getId());
                prepay.setCreatedBy(String.valueOf(userId != null ? userId : DEFAULT_USER_ID));
                prepaymentMapper.insert(prepay);
                log.info("P12-3 预收款生成: statementId={}, customerId={}, docId={}, amount={}",
                        stmt.getId(), customerId, doc.getId(), amount);
            }

        } else if (BankClassification.BUSINESS_PAYMENT.equals(classification)) {
            // 银行流水代表款项已付，不应创建新的应付单。
            // 检查该供应商是否有未结清应付单，有则按 FIFO 核销。
            Long vendorId = doc.getSupplierId();
            if (vendorId == null) {
                vendorId = findVendorByName(counterName);
            }
            if (vendorId == null) {
                log.warn("银行流水付款跳过: 供应商名 '{}' 无法匹配", counterName);
                return;
            }
            doc.setSupplierId(vendorId);

            if (reconciliationService.hasOpenInvoices("INVOICE_IN", vendorId)) {
                reconciliationService.autoReconcileFifo(
                        vendorId, "INVOICE_IN", amount,
                        "bank_txn", stmt.getId(),
                        period, "银行流水自动核销应付");
                log.info("银行流水付款按 FIFO 核销完成: vendorId={}, amount={}, counterName={}",
                        vendorId, amount, counterName);
            } else {
                // P12-3: 无未结清应付，走预付款路径
                log.info("P12-3 供应商 '{}' 无未结清应付，走预付款路径", counterName);
                PrepaymentEntity prepay = new PrepaymentEntity();
                prepay.setTenantId(1L);
                prepay.setVendorId(vendorId);
                prepay.setDocId(doc.getId());
                prepay.setVoucherId(doc.getVoucherId());
                prepay.setPeriod(period);
                prepay.setTxDate(stmt.getTxDate());
                prepay.setAmount(amount);
                prepay.setSettledAmount(BigDecimal.ZERO);
                prepay.setUnsettledAmount(amount);
                prepay.setSummary(stmt.getSummary());
                prepay.setStatus(ArapStatus.DRAFT);
                prepay.setSourceDocType("bank_txn");
                prepay.setSourceDocId(stmt.getId());
                prepay.setCreatedBy(String.valueOf(userId != null ? userId : DEFAULT_USER_ID));
                prepaymentMapper.insert(prepay);
                log.info("P12-3 预付款生成: statementId={}, vendorId={}, docId={}, amount={}",
                        stmt.getId(), vendorId, doc.getId(), amount);
            }
        } else {
            // internal_transfer / salary_social 等不走应收/应付
            log.debug("跳过: classification={}, 不需要应收/应付单", classification);
        }
    }

    public static String classifyType(String classification) {
        return BankClassification.routeType(classification);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoGenerateInNewTx(Long statementId, Long userId) {
        return autoGenerate(statementId, userId);
    }
}
