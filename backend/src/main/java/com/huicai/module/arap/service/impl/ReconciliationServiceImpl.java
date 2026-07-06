package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.*;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.arap.service.ReconciliationService;
import com.huicai.module.tax.service.OutputInvoiceStateMachineService;
import com.huicai.module.finance.constant.VoucherType;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final BigDecimal TOLERANCE = new BigDecimal("5.00");
    private static final BigDecimal SCORE_THRESHOLD = new BigDecimal("0.70");
    private static final BigDecimal TOLERANCE_RATE = new BigDecimal("0.10"); // L5: 容差 10%
    private static final long DEFAULT_TENANT_ID = 1L;
    private static final long DEFAULT_USER_ID = 1L;

    private final BankStatementMapper bankStatementMapper;
    private final BusinessDocMapper businessDocMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final ReconciliationLogMapper logMapper;
    private final ReconciliationExceptionMapper exceptionMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final VoucherTemplateService voucherTemplateService;
    private final ArapSettlementService settlementService;
    private final OutputInvoiceStateMachineService outputInvoiceStateMachineService;

    @Override
    public RecommendResult recommendReceipt(Long receiptId, Long customerId, BigDecimal amount, String summary, String counterpartyName) {
        return recommend(customerId, null, amount, summary, counterpartyName, true, "receipt", receiptId, null, null);
    }

    @Override
    public RecommendResult recommendPayment(Long paymentId, Long vendorId, BigDecimal amount, String summary, String counterpartyName) {
        return recommend(null, vendorId, amount, summary, counterpartyName, false, "payment", paymentId, null, null);
    }

    private RecommendResult recommend(Long customerId, Long vendorId, BigDecimal amount, String summary, String counterpartyName, boolean isReceipt, String sourceType, Long sourceId, LocalDate txDate, String externalNo) {
        List<RecommendItem> items = new ArrayList<>();
        String targetDocType = isReceipt ? "INVOICE_OUT" : "INVOICE_IN";

        // P34: 直接查询 BusinessDocEntity（替代 ReceivableEntity/PayableEntity）
        List<BusinessDocEntity> invoices;
        if (isReceipt && customerId != null) {
            invoices = businessDocMapper.selectList(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getCustomerId, customerId)
                            .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                            .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO));
        } else if (!isReceipt && vendorId != null) {
            invoices = businessDocMapper.selectList(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getSupplierId, vendorId)
                            .eq(BusinessDocEntity::getDocType, "INVOICE_IN")
                            .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO));
        } else {
            return new RecommendResult(sourceType, sourceId, counterpartyName, amount, List.of());
        }

        for (BusinessDocEntity doc : invoices) {
            // 跳过由银行流水自动生成的单据(防止自引用循环推荐)
            if ("bank_txn".equals(sourceType) && "FROM_BANK_TXN".equals(doc.getSource())) {
                continue;
            }

            BigDecimal unsettledAmount = doc.getUnsettledAmount();
            if (unsettledAmount == null || unsettledAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            String invoiceNo = doc.getDocNo();
            Long targetDocId = doc.getId();
            LocalDate invoiceTxDate = doc.getDocDate();
            String invoiceSummary = doc.getSummary() != null ? doc.getSummary() : "";
            String invoiceCustomerName = "";

            if (unsettledAmount == null || unsettledAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 确定 L1-L5 匹配级别
            String matchLevel = determineMatchLevel(amount, unsettledAmount, summary, invoiceSummary,
                    counterpartyName, externalNo, invoiceNo, txDate, invoiceTxDate);

            if (matchLevel != null) {
                BigDecimal matchScore = calculateScore(amount, unsettledAmount, summary, invoiceSummary, counterpartyName, invoiceCustomerName);
                BigDecimal suggestedAmount = amount.min(unsettledAmount);
                items.add(new RecommendItem(targetDocId, invoiceNo, targetDocType, amount, unsettledAmount, matchScore, matchLevel, suggestedAmount));
            } else {
                BigDecimal suggestedAmount = amount.min(unsettledAmount);
                items.add(new RecommendItem(targetDocId, invoiceNo, targetDocType, amount, unsettledAmount, BigDecimal.ZERO, "L6", suggestedAmount));
            }
        }

        items.sort((a, b) -> {
            int levelCmp = levelOrder(a.matchLevel()) - levelOrder(b.matchLevel());
            if (levelCmp != 0) return levelCmp;
            return b.matchScore().compareTo(a.matchScore());
        });
        return new RecommendResult(sourceType, sourceId, counterpartyName, amount, items);
    }

    private static int levelOrder(String level) {
        if (level == null) return 99;
        return switch (level) {
            case "L1" -> 1;
            case "L2" -> 2;
            case "L3" -> 3;
            case "L4" -> 4;
            case "L5" -> 5;
            default -> 99;
        };
    }

    /**
     * 确定 L1-L5 匹配级别 (按优先级依次检查, 返回最高级别).
     *
     * @return 级别字符串 L1-L5, 或 null (不匹配)
     */
    private String determineMatchLevel(BigDecimal sourceAmount, BigDecimal unsettledAmount,
                                        String sourceSummary, String targetSummary,
                                        String sourceName, String externalNo, String invoiceNo,
                                        LocalDate txDate, LocalDate invoiceTxDate) {
        // L1: 引用号匹配 — externalNo 与 invoice ID 一致
        if (StrUtil.isNotBlank(externalNo) && externalNo.equals(invoiceNo)) {
            return "L1";
        }

        // L2: 发票号匹配 — 摘要中包含发票号码 (数字6-10位)
        if (StrUtil.isNotBlank(sourceSummary) && StrUtil.isNotBlank(invoiceNo)) {
            Pattern invoicePattern = Pattern.compile("\\b" + Pattern.quote(invoiceNo) + "\\b");
            if (invoicePattern.matcher(sourceSummary).find()) {
                return "L2";
            }
        }

        BigDecimal diff = sourceAmount.subtract(unsettledAmount).abs();

        // L3: 金额+日期 — 金额精确 + 日期 ±3天 (客商已由上游查询保证)
        if (diff.compareTo(BigDecimal.ZERO) == 0
                && txDate != null && invoiceTxDate != null
                && Math.abs(ChronoUnit.DAYS.between(txDate, invoiceTxDate)) <= 3) {
            return "L3";
        }

        // L4: 金额精确
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return "L4";
        }

        // L5: 容差匹配 — 金额差异 ≤ invoice 金额的 10%
        if (unsettledAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = diff.divide(unsettledAmount, 4, RoundingMode.HALF_UP);
            if (rate.compareTo(TOLERANCE_RATE) <= 0) {
                return "L5";
            }
        }

        return null;
    }

    private BigDecimal calculateScore(BigDecimal sourceAmount, BigDecimal unsettledAmount, String sourceSummary, String targetSummary, String sourceName, String targetName) {
        BigDecimal score = BigDecimal.ZERO;

        // Amount match (0.4)
        BigDecimal diff = sourceAmount.subtract(unsettledAmount).abs();
        if (diff.compareTo(TOLERANCE) <= 0) {
            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                score = score.add(new BigDecimal("0.40"));
            } else {
                score = score.add(new BigDecimal("0.30"));
            }
        }

        // Summary similarity (0.4) — Jaccard 2-gram
        if (StrUtil.isNotBlank(sourceSummary) && StrUtil.isNotBlank(targetSummary)) {
            double similarity = jaccardSimilarity(sourceSummary, targetSummary);
            score = score.add(BigDecimal.valueOf(similarity * 0.4));
        }

        // Name match (0.2)
        if (StrUtil.isNotBlank(sourceName) && StrUtil.isNotBlank(targetName)) {
            if (sourceName.equals(targetName)) {
                score = score.add(new BigDecimal("0.20"));
            } else {
                double nameSim = levenshteinSimilarity(sourceName.toLowerCase(), targetName.toLowerCase());
                if (nameSim >= 0.8) {
                    score = score.add(new BigDecimal("0.10"));
                }
            }
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> gramsA = ngram2(a);
        Set<String> gramsB = ngram2(b);
        if (gramsA.isEmpty() && gramsB.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(gramsA);
        intersection.retainAll(gramsB);
        Set<String> union = new HashSet<>(gramsA);
        union.addAll(gramsB);
        return (double) intersection.size() / union.size();
    }

    private Set<String> ngram2(String text) {
        Set<String> grams = new HashSet<>();
        String clean = text.replaceAll("\\s+", "");
        for (int i = 0; i < clean.length() - 1; i++) {
            grams.add(clean.substring(i, i + 2));
        }
        return grams;
    }

    private double levenshteinSimilarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshteinDistance(a, b) / maxLen;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
        }
        return dp[a.length()][b.length()];
    }

    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 100))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReconciliationLogEntity execute(ExecuteRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("核销金额必须大于0");
        }

        Long resolvedCustomerId = request.customerId();
        Long resolvedVendorId = request.vendorId();

        // P34: Update target business doc unsettled amount
        BusinessDocEntity doc = businessDocMapper.selectById(request.targetDocId());
        if (doc == null) throw new BusinessException("业务单据不存在: " + request.targetDocId());
        if ("INVOICE_OUT".equals(request.targetDocType()) && resolvedCustomerId == null) {
            resolvedCustomerId = doc.getCustomerId();
        }
        if ("INVOICE_IN".equals(request.targetDocType()) && resolvedVendorId == null) {
            resolvedVendorId = doc.getSupplierId();
        }
        BigDecimal newSettled = (doc.getSettledAmount() != null ? doc.getSettledAmount() : BigDecimal.ZERO)
                .add(request.amount());
        doc.setSettledAmount(newSettled);
        doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
        doc.setStatus(doc.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
                ? "FULLY_RECONCILED" : "PARTIALLY_RECONCILED");
        if (businessDocMapper.updateById(doc) == 0) {
            throw new OptimisticLockingFailureException("BusinessDoc版本冲突, id=" + doc.getId());
        }

        // P38-F4: 核销后同步发票状态
        if (doc.getInvoiceId() != null && "INVOICE_OUT".equals(request.targetDocType())) {
            try {
                outputInvoiceStateMachineService.onReconciliationUpdate(
                        doc.getInvoiceId(), doc.getUnsettledAmount(), DEFAULT_USER_ID);
                log.info("P38 核销同步发票状态: invoiceId={}, unsettled={}",
                        doc.getInvoiceId(), doc.getUnsettledAmount());
            } catch (Exception e) {
                log.warn("P38 核销同步发票状态失败(不影响核销): {}", e.getMessage());
            }
        }

        // Create reconciliation log
        ReconciliationLogEntity reconLog = new ReconciliationLogEntity();
        reconLog.setTenantId(DEFAULT_TENANT_ID);
        reconLog.setSourceDocType(request.sourceDocType());
        reconLog.setSourceDocId(request.sourceDocId());
        reconLog.setTargetDocType(request.targetDocType());
        reconLog.setTargetDocId(request.targetDocId());
        reconLog.setAllocatedAmount(request.amount());
        reconLog.setDiscountAmount(BigDecimal.ZERO);
        reconLog.setMatchScore(request.matchScore());
        reconLog.setMatchMethod(request.matchMethod() != null ? request.matchMethod() : "MANUAL");
        reconLog.setStatus(ArapStatus.CONFIRMED);
        reconLog.setRemark(request.remark());
        reconLog.setCreatedBy(DEFAULT_USER_ID);
        logMapper.insert(reconLog);

        // Also create settlement record via existing ArapSettlementService
        if (StrUtil.isNotBlank(request.period())) {
            try {
                boolean isReceivableSettle = "INVOICE_OUT".equals(request.targetDocType());
                ArapSettlementEntity settlement = new ArapSettlementEntity();
                settlement.setSettlementType(isReceivableSettle ? "RECEIVE" : "PAY");
                settlement.setPeriod(request.period());
                settlement.setSettlementDate(java.time.LocalDate.now());
                settlement.setTotalAmount(request.amount());
                Long partyId = isReceivableSettle ? resolvedCustomerId : resolvedVendorId;
                if (partyId == null) {
                    log.warn("未找到客商ID, 跳过创建核销单记录: sourceType={}, sourceId={}", request.sourceDocType(), request.sourceDocId());
                } else {
                    settlement.setPartyId(partyId);
                    settlement.setPartyType(isReceivableSettle ? "CUSTOMER" : "VENDOR");
                    settlement.setStatus(ArapStatus.CONFIRMED);
                    String prefix = isReceivableSettle ? "JS" : "FS";
                    settlement.setSettlementNo(prefix + "-" + request.period() + "-" + cn.hutool.core.util.IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());

                    ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
                    entry.setBusinessDocId(request.targetDocId());
                    entry.setSettledAmount(request.amount());

                    settlementService.create(settlement, List.of(entry));
                }
            } catch (Exception e) {
                log.warn("创建核销单记录失败(不影响核销): {}", e.getMessage());
            }
        }

        // G12: 核销完成后自动生成草稿凭证 (仅当来源单据尚无凭证时)
        if ("bank_txn".equals(request.sourceDocType())) {
            try {
                // 将银行流水标记为已核销, 不再出现在核销工作台
                com.huicai.module.finance.entity.BankStatementEntity stmt =
                        bankStatementMapper.selectById(request.sourceDocId());
                if (stmt != null && "UNMATCHED".equals(stmt.getMatchStatus())) {
                    stmt.setMatchStatus("MATCHED");
                    bankStatementMapper.updateById(stmt);
                }
                createReconciliationVoucher(request, reconLog);
            } catch (Exception e) {
                log.warn("核销自动制证失败(不影响核销): {}", e.getMessage());
            }
        }

        log.info("核销执行完成: sourceType={}, sourceId={}, targetId={}, amount={}", request.sourceDocType(), request.sourceDocId(), request.targetDocId(), request.amount());
        return reconLog;
    }

    private void createReconciliationVoucher(ExecuteRequest request, ReconciliationLogEntity reconLog) {
        if ("bank_txn".equals(request.sourceDocType()) && request.sourceDocId() != null) {
            com.huicai.module.finance.entity.BankStatementEntity stmt =
                    bankStatementMapper.selectById(request.sourceDocId());
            if (stmt == null || stmt.getGeneratedVoucherId() != null) return;
        }

        String classification;
        if ("INVOICE_OUT".equals(request.targetDocType())) {
            classification = "reconciliation_receipt";
        } else if ("INVOICE_IN".equals(request.targetDocType())) {
            classification = "reconciliation_payment";
        } else {
            return;
        }

        VoucherTemplateEntity template = voucherTemplateService.matchByClassification(classification);
        if (template == null) {
            log.warn("核销自动制证: 无匹配模板 classification={}", classification);
            return;
        }

        List<VoucherTemplateLineEntity> lines = voucherTemplateService.getLines(template.getId());
        if (lines == null || lines.isEmpty()) {
            log.warn("核销自动制证: 模板 {} 无分录行", template.getName());
            return;
        }

        String period = request.period() != null ? request.period()
                : java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        long voucherTypeId = "reconciliation_receipt".equals(classification) ? VoucherType.SK : VoucherType.FK;
        String voucherNo = voucherNoService.generateNextNo(period, voucherTypeId);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(voucherTypeId);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(request.remark() != null ? request.remark() : "核销自动生成");
        voucher.setTemplateId(template.getId());
        voucher.setCreatedBy(DEFAULT_USER_ID);
        // P38-F7: 来源追溯字段（凭证→核销来源）
        voucher.setSourceDocType("RECONCILIATION");
        voucher.setSourceDocId(request.sourceDocId());
        voucher.setSourceDocNo(request.remark() != null ? request.remark() : "");
        voucherMapper.insert(voucher);

        BigDecimal totalAmount = BigDecimal.ZERO;
        int sort = 1;
        for (VoucherTemplateLineEntity line : lines) {
            BigDecimal amount = request.amount();
            BigDecimal dr = BigDecimal.ZERO;
            BigDecimal cr = BigDecimal.ZERO;

            if ("debit".equals(line.getDirection())) {
                dr = amount;
            } else if ("credit".equals(line.getDirection())) {
                cr = amount;
            }

            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

            String summary = line.getSummaryTemplate() != null
                    ? line.getSummaryTemplate().replace("{{summary}}", request.remark() != null ? request.remark() : "")
                    : "核销生成";

            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setVoucherId(voucher.getId());
            entry.setSubjectId(line.getSubjectId());
            entry.setDebit(dr);
            entry.setCredit(cr);
            entry.setSummary(summary);
            entry.setSortOrder(sort++);
            voucherEntryMapper.insert(entry);

            totalAmount = totalAmount.add(dr).add(cr);
        }

        BigDecimal maxAmt = totalAmount.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);

        if ("bank_txn".equals(request.sourceDocType()) && request.sourceDocId() != null) {
            try {
                com.huicai.module.finance.entity.BankStatementEntity stmt =
                        bankStatementMapper.selectById(request.sourceDocId());
                if (stmt != null && stmt.getGeneratedVoucherId() == null) {
                    stmt.setGeneratedVoucherId(voucher.getId());
                    stmt.setGeneratedAt(java.time.LocalDateTime.now());
                    bankStatementMapper.updateById(stmt);
                }
            } catch (Exception e) {
                log.warn("更新银行流水凭证ID失败: {}", e.getMessage());
            }
        }

        log.info("核销自动制证完成: reconLogId={}, voucherId={}, classification={}, amount={}",
                reconLog.getId(), voucher.getId(), classification, request.amount());
    }

    @Override
    public PreCheckResult preCheck(ExecuteRequest request) {
        List<PreCheckItem> checks = new ArrayList<>();

        // 1. 单据有效: sourceDoc 是否存在
        boolean docValid = request.sourceDocType() != null && request.sourceDocId() != null;
        checks.add(new PreCheckItem("sourceDocValid", docValid, docValid ? "来源单据有效" : "来源单据无效"));

        // 2. 发票有效: 目标单据存在且未结清
        boolean invoiceExists = false;
        String invoiceMsg;
        BusinessDocEntity checkDoc = businessDocMapper.selectById(request.targetDocId());
        if (checkDoc != null) {
            invoiceExists = checkDoc.getUnsettledAmount() != null
                    && checkDoc.getUnsettledAmount().compareTo(BigDecimal.ZERO) > 0;
            invoiceMsg = invoiceExists ? "业务单据有效" : "业务单据已结清";
        } else {
            invoiceMsg = "业务单据不存在: " + request.targetDocId();
        }
        checks.add(new PreCheckItem("invoiceValid", invoiceExists, invoiceMsg));

        // 3. 客商一致: 来源与目标客商匹配
        boolean partyMatch = false;
        String partyMsg;
        if (checkDoc != null && "INVOICE_OUT".equals(request.targetDocType()) && request.customerId() != null) {
            partyMatch = checkDoc.getCustomerId() != null && checkDoc.getCustomerId().equals(request.customerId());
            partyMsg = partyMatch ? "客商一致(客户)" : "客户不匹配";
        } else if (checkDoc != null && "INVOICE_IN".equals(request.targetDocType()) && request.vendorId() != null) {
            partyMatch = checkDoc.getSupplierId() != null && checkDoc.getSupplierId().equals(request.vendorId());
            partyMsg = partyMatch ? "客商一致(供应商)" : "供应商不匹配";
        } else {
            partyMatch = true;
            partyMsg = "客商未指定, 跳过检查";
        }
        checks.add(new PreCheckItem("partyMatch", partyMatch, partyMsg));

        // 4. 金额充足: 核销金额 ≤ 未结算金额
        BigDecimal unsettled = checkDoc != null ? checkDoc.getUnsettledAmount() : BigDecimal.ZERO;
        boolean amountValid = request.amount().compareTo(unsettled) <= 0;
        String amountMsg = amountValid ? "核销金额 " + request.amount() + " ≤ 未结算金额 " + unsettled : "核销金额超过未结算余额";
        checks.add(new PreCheckItem("amountValid", amountValid, amountMsg));

        // 5. 期间正常: period 格式校验 (YYYYMM 格式)
        boolean periodValid = request.period() != null && request.period().matches("\\d{6}");
        checks.add(new PreCheckItem("periodValid", periodValid, periodValid ? "期间格式正确: " + request.period() : "期间格式无效: " + request.period()));

        boolean allPassed = checks.stream().allMatch(PreCheckItem::passed);
        return new PreCheckResult(allPassed, checks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReconciliationLogEntity> batchExecute(List<ExecuteRequest> requests) {
        return requests.stream().map(this::execute).collect(Collectors.toList());
    }

    @Override
    public List<ReconciliationLogEntity> getRecords(String sourceDocType, Long sourceDocId) {
        return logMapper.findBySource(sourceDocType, sourceDocId);
    }

    @Override
    public IPage<ReconciliationLogEntity> pageLogs(String sourceDocType, Integer current, Integer size) {
        Page<ReconciliationLogEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<ReconciliationLogEntity> wrapper = new LambdaQueryWrapper<ReconciliationLogEntity>()
                .eq(sourceDocType != null, ReconciliationLogEntity::getSourceDocType, sourceDocType)
                .orderByDesc(ReconciliationLogEntity::getCreatedAt);
        return logMapper.selectPage(page, wrapper);
    }

    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long logId, String reason) {
        ReconciliationLogEntity reconLog = logMapper.selectById(logId);
        if (reconLog == null) throw new BusinessException("核销记录不存在");
        if (!ArapStatus.isConfirmed(reconLog.getStatus()) && !ArapStatus.EXECUTED.equals(reconLog.getStatus())) {
            throw new BusinessException("仅已确认或已执行的核销可反核销, 当前: " + reconLog.getStatus());
        }
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("反核销必须填写原因");
        }

        // P34: Restore target business doc unsettled amount
        BigDecimal amount = reconLog.getAllocatedAmount();
        BusinessDocEntity doc = businessDocMapper.selectById(reconLog.getTargetDocId());
        if (doc != null) {
            BigDecimal newSettled = doc.getSettledAmount().subtract(amount);
            doc.setSettledAmount(newSettled);
            doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
            if ("FULLY_RECONCILED".equals(doc.getStatus())) {
                doc.setStatus("APPROVED");
            }
            if (businessDocMapper.updateById(doc) == 0) {
                throw new OptimisticLockingFailureException("BusinessDoc反核销版本冲突, id=" + doc.getId());
            }
        }

        reconLog.setStatus(ArapStatus.CANCELLED);
        reconLog.setRemark("反核销原因: " + reason);
        logMapper.updateById(reconLog);

        log.info("反核销完成: logId={}, amount={}, reason={}", logId, amount, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationLogEntity approve(Long logId) {
        ReconciliationLogEntity reconLog = logMapper.selectById(logId);
        if (reconLog == null) throw new BusinessException("核销记录不存在: " + logId);
        if (!ArapStatus.isConfirmed(reconLog.getStatus())) {
            throw new BusinessException("仅已确认(CONFIRMED)的核销可审批执行, 当前状态: " + reconLog.getStatus());
        }
        reconLog.setStatus(ArapStatus.EXECUTED);
        logMapper.updateById(reconLog);
        log.info("核销审批执行完成: logId={}, amount={}", logId, reconLog.getAllocatedAmount());
        return reconLog;
    }

    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long logId, String reason) {
        ReconciliationLogEntity reconLog = logMapper.selectById(logId);
        if (reconLog == null) throw new BusinessException("核销记录不存在: " + logId);
        if (!ArapStatus.isConfirmed(reconLog.getStatus())) {
            throw new BusinessException("仅已确认(CONFIRMED)的核销可驳回, 当前状态: " + reconLog.getStatus());
        }
        // P34: 恢复业务单据未结金额（同 reverse 逻辑）
        BigDecimal amount = reconLog.getAllocatedAmount();
        BusinessDocEntity doc = businessDocMapper.selectById(reconLog.getTargetDocId());
        if (doc != null) {
            BigDecimal newSettled = doc.getSettledAmount().subtract(amount);
            doc.setSettledAmount(newSettled);
            doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
            if ("FULLY_RECONCILED".equals(doc.getStatus())) {
                doc.setStatus("APPROVED");
            }
            if (businessDocMapper.updateById(doc) == 0) {
                throw new OptimisticLockingFailureException("BusinessDoc驳回版本冲突, id=" + doc.getId());
            }
        }
        reconLog.setStatus(ArapStatus.REJECTED);
        reconLog.setRemark(reason);
        logMapper.updateById(reconLog);
        log.info("核销驳回完成: logId={}, amount={}, reason={}", logId, amount, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationLogEntity executeWithAdjustment(ExecuteRequest request, BigDecimal adjustAmount, String adjustType, Long adjustSubjectId) {
        if (adjustAmount == null || adjustAmount.compareTo(BigDecimal.ZERO) == 0) {
            return execute(request); // 无差额, 走普通核销
        }

        // 主核销金额 = 请求金额 - 差额
        BigDecimal mainAmount = request.amount().subtract(adjustAmount);
        if (mainAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("扣除差额后主核销金额必须大于0, mainAmount=" + mainAmount);
        }

        // 1. 执行主核销
        ExecuteRequest mainRequest = new ExecuteRequest(
                request.sourceDocType(), request.sourceDocId(),
                request.targetDocType(), request.targetDocId(),
                mainAmount, request.matchScore(), request.matchMethod(),
                request.customerId(), request.vendorId(),
                request.period(), request.remark()
        );
        ReconciliationLogEntity mainLog = execute(mainRequest);

        // 2. 标记主核销日志差额类型
        mainLog.setRemark(adjustType + "差额核销, adjustAmount=" + adjustAmount);
        logMapper.updateById(mainLog);

        // 3. 创建差额调整凭证分录 (仅记录, 不修改应收/应付结算金额)
        //    实际企业会额外生成一笔调整凭证: 借 财务费用/折扣 / 贷 应收/应付
        ReconciliationLogEntity adjustLog = new ReconciliationLogEntity();
        adjustLog.setTenantId(DEFAULT_TENANT_ID);
        adjustLog.setSourceDocType(request.sourceDocType());
        adjustLog.setSourceDocId(request.sourceDocId());
        adjustLog.setTargetDocType(request.targetDocType());
        adjustLog.setTargetDocId(request.targetDocId());
        adjustLog.setAllocatedAmount(adjustAmount);
        adjustLog.setDiscountAmount(adjustAmount);
        adjustLog.setMatchScore(BigDecimal.ZERO);
        adjustLog.setMatchMethod("ADJUSTMENT");
        adjustLog.setStatus(ArapStatus.EXECUTED); // 差额调整自动执行
        adjustLog.setRemark("差额调整(" + adjustType + "), 科目=" + adjustSubjectId + ", 金额=" + adjustAmount);
        adjustLog.setCreatedBy(DEFAULT_USER_ID);
        logMapper.insert(adjustLog);

        log.info("带差额核销完成: sourceId={}, targetId={}, mainAmount={}, adjustAmount={}, adjustType={}",
                request.sourceDocId(), request.targetDocId(), mainAmount, adjustAmount, adjustType);

        return mainLog;
    }

    @Override
    public boolean hasOpenInvoices(String targetDocType, Long partyId) {
        if (targetDocType == null || partyId == null) return false;
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<BusinessDocEntity>()
                .eq("INVOICE_OUT".equals(targetDocType), BusinessDocEntity::getCustomerId, partyId)
                .eq("INVOICE_IN".equals(targetDocType), BusinessDocEntity::getSupplierId, partyId)
                .eq(BusinessDocEntity::getDocType, targetDocType)
                .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                .in(BusinessDocEntity::getStatus, List.of("APPROVED", "VOUCHERED", "PARTIALLY_RECONCILED"))
                .last("LIMIT 1");
        return businessDocMapper.selectCount(wrapper) > 0;
    }

    // ==================== FIFO 自动核销策略 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReconciliationLogEntity> autoReconcileFifo(Long partyId, String targetDocType, BigDecimal totalAmount,
                                                            String sourceDocType, Long sourceDocId,
                                                            String period, String summary) {
        List<ReconciliationLogEntity> logs = new ArrayList<>();
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) return logs;

        BigDecimal remaining = totalAmount;

        // P34: 统一查询 BusinessDocEntity（替代 ReceivableEntity/PayableEntity FIFO）
        List<BusinessDocEntity> invoices = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq("INVOICE_OUT".equals(targetDocType), BusinessDocEntity::getCustomerId, partyId)
                        .eq("INVOICE_IN".equals(targetDocType), BusinessDocEntity::getSupplierId, partyId)
                        .eq(BusinessDocEntity::getDocType, targetDocType)
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                        .in(BusinessDocEntity::getStatus, List.of("APPROVED", "VOUCHERED", "PARTIALLY_RECONCILED"))
                        .orderByAsc(BusinessDocEntity::getDueDate)
        );
        for (BusinessDocEntity inv : invoices) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal alloc = remaining.min(inv.getUnsettledAmount());
            ExecuteRequest req = new ExecuteRequest(
                    sourceDocType, sourceDocId,
                    targetDocType, inv.getId(),
                    alloc, new BigDecimal("100"),
                    "AUTO",
                    "INVOICE_OUT".equals(targetDocType) ? partyId : null,
                    "INVOICE_IN".equals(targetDocType) ? partyId : null,
                    period, summary != null ? summary : "FIFO自动核销"
            );
            ReconciliationLogEntity log = execute(req);
            logs.add(log);
            remaining = remaining.subtract(alloc);
        }

        // 若还有剩余金额未核销完, 自动转为预收/预付
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.info("FIFO核销后有剩余金额 {} 未分配 (sourceType={}, sourceId={}, partyId={}), 需转预收/预付",
                    remaining, sourceDocType, sourceDocId, partyId);
        }

        log.info("FIFO自动核销完成: partyId={}, targetDocType={}, totalAmount={}, actualAllocated={}, remaining={}",
                partyId, targetDocType, totalAmount,
                totalAmount.subtract(remaining), remaining);
        return logs;
    }

    @Override
    public List<BusinessDocEntity> getUnsettledInvoicesFifo(String targetDocType, Long partyId, LocalDate dueDateBefore) {
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<BusinessDocEntity>()
                .eq("INVOICE_OUT".equals(targetDocType), BusinessDocEntity::getCustomerId, partyId)
                .eq("INVOICE_IN".equals(targetDocType), BusinessDocEntity::getSupplierId, partyId)
                .eq(BusinessDocEntity::getDocType, targetDocType)
                .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                .in(BusinessDocEntity::getStatus, List.of("APPROVED", "VOUCHERED", "PARTIALLY_RECONCILED"));
        if (dueDateBefore != null) {
            wrapper.le(BusinessDocEntity::getDueDate, dueDateBefore);
        }
        wrapper.orderByAsc(BusinessDocEntity::getDueDate);
        return businessDocMapper.selectList(wrapper);
    }

    // ==================== 异常池管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationExceptionEntity createException(
            String sourceDocType, Long sourceDocId,
            String targetDocType, Long targetDocId,
            Long partyId, String partyType,
            BigDecimal amount, BigDecimal unsettledAmount,
            String exceptionType, String exceptionReason,
            String matchSuggestion) {
        ReconciliationExceptionEntity ex = new ReconciliationExceptionEntity();
        ex.setTenantId(DEFAULT_TENANT_ID);
        ex.setSourceDocType(sourceDocType);
        ex.setSourceDocId(sourceDocId);
        ex.setTargetDocType(targetDocType);
        ex.setTargetDocId(targetDocId);
        ex.setPartyId(partyId);
        ex.setPartyType(partyType);
        ex.setAmount(amount);
        ex.setUnsettledAmount(unsettledAmount);
        ex.setExceptionType(exceptionType);
        ex.setExceptionReason(exceptionReason);
        ex.setMatchSuggestion(matchSuggestion);
        ex.setStatus("OPEN");
        ex.setRetryCount(0);
        ex.setCreatedBy(DEFAULT_USER_ID);
        exceptionMapper.insert(ex);
        log.info("核销异常记录创建: id={}, type={}, sourceDocType={}, sourceDocId={}, reason={}",
                ex.getId(), exceptionType, sourceDocType, sourceDocId, exceptionReason);
        return ex;
    }

    @Override
    public IPage<ReconciliationExceptionEntity> pageExceptions(String status, String exceptionType,
                                                                Integer current, Integer size) {
        Page<ReconciliationExceptionEntity> page = new Page<>(
                current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<ReconciliationExceptionEntity> wrapper = new LambdaQueryWrapper<ReconciliationExceptionEntity>()
                .eq(StrUtil.isNotBlank(status), ReconciliationExceptionEntity::getStatus, status)
                .eq(StrUtil.isNotBlank(exceptionType), ReconciliationExceptionEntity::getExceptionType, exceptionType)
                .orderByDesc(ReconciliationExceptionEntity::getCreatedAt);
        return exceptionMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveException(Long id, Long userId, String remark) {
        ReconciliationExceptionEntity ex = exceptionMapper.selectById(id);
        if (ex == null) throw new BusinessException("异常记录不存在: " + id);
        if (!"OPEN".equals(ex.getStatus())) {
            throw new BusinessException("仅 OPEN 状态的异常可解决, 当前: " + ex.getStatus());
        }
        ex.setStatus("RESOLVED");
        ex.setResolvedBy(userId != null ? userId : DEFAULT_USER_ID);
        ex.setResolvedAt(LocalDateTime.now());
        ex.setRemark(remark);
        exceptionMapper.updateById(ex);
        log.info("核销异常已解决: id={}, userId={}, remark={}", id, userId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ignoreException(Long id, Long userId, String reason) {
        ReconciliationExceptionEntity ex = exceptionMapper.selectById(id);
        if (ex == null) throw new BusinessException("异常记录不存在: " + id);
        if (!"OPEN".equals(ex.getStatus())) {
            throw new BusinessException("仅 OPEN 状态的异常可忽略, 当前: " + ex.getStatus());
        }
        ex.setStatus("IGNORED");
        ex.setResolvedBy(userId != null ? userId : DEFAULT_USER_ID);
        ex.setResolvedAt(LocalDateTime.now());
        ex.setRemark(reason);
        exceptionMapper.updateById(ex);
        log.info("核销异常已忽略: id={}, userId={}, reason={}", id, userId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationLogEntity retryException(Long id, Long userId) {
        ReconciliationExceptionEntity ex = exceptionMapper.selectById(id);
        if (ex == null) throw new BusinessException("异常记录不存在: " + id);
        if (!"OPEN".equals(ex.getStatus())) {
            throw new BusinessException("仅 OPEN 状态的异常可重试, 当前: " + ex.getStatus());
        }

        String targetDocType = ex.getTargetDocType();
        Long targetDocId = ex.getTargetDocId();
        String sourceDocType = ex.getSourceDocType();
        if (targetDocType == null || targetDocId == null) {
            throw new BusinessException("异常记录缺少目标单据信息, 无法重试");
        }

        // 重新执行核销
        BigDecimal amount = ex.getAmount();
        String period = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        ExecuteRequest req = new ExecuteRequest(
                sourceDocType, ex.getSourceDocId(),
                targetDocType, targetDocId,
                amount, BigDecimal.ZERO,
                "MANUAL",
                "CUSTOMER".equals(ex.getPartyType()) ? ex.getPartyId() : null,
                "VENDOR".equals(ex.getPartyType()) ? ex.getPartyId() : null,
                period, "异常重试: " + (ex.getExceptionReason() != null ? ex.getExceptionReason() : "")
        );
        ReconciliationLogEntity reconLog = execute(req);

        // 更新异常记录
        ex.setRetryCount(ex.getRetryCount() + 1);
        ex.setResolvedBy(userId != null ? userId : DEFAULT_USER_ID);
        ex.setResolvedAt(LocalDateTime.now());
        exceptionMapper.updateById(ex);

        log.info("核销异常重试完成: exceptionId={}, logId={}, amount={}", id, reconLog.getId(), amount);
        return reconLog;
    }

    // ==================== 多对多核销拓扑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReconciliationLogEntity> splitAllocate(
            String sourceDocType, Long sourceDocId,
            Long customerId, Long vendorId,
            BigDecimal totalAmount, List<AllocationItem> allocations,
            String period, String summary) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("核销总金额必须大于0");
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new BusinessException("分配列表不能为空");
        }

        // 校验分配金额总和
        BigDecimal allocSum = allocations.stream()
                .map(AllocationItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocSum.compareTo(totalAmount) > 0) {
            throw new BusinessException("分配金额总和超过来源金额: allocSum=" + allocSum + ", total=" + totalAmount);
        }

        String effectivePeriod = period != null ? period
                : java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        List<ReconciliationLogEntity> logs = new ArrayList<>();

        for (AllocationItem alloc : allocations) {
            BusinessDocEntity targetDoc = businessDocMapper.selectById(alloc.targetDocId());
            BigDecimal targetUnsettled = targetDoc != null ? targetDoc.getUnsettledAmount() : BigDecimal.ZERO;

            if (targetUnsettled.compareTo(alloc.amount()) < 0) {
                throw new BusinessException("目标单据 " + alloc.targetDocId() + " 未结金额不足: required="
                        + alloc.amount() + ", available=" + targetUnsettled);
            }

            ExecuteRequest req = new ExecuteRequest(
                    sourceDocType, sourceDocId,
                    alloc.targetDocType(), alloc.targetDocId(),
                    alloc.amount(), BigDecimal.ZERO,
                    "MANUAL", customerId, vendorId,
                    effectivePeriod, summary != null ? summary : "多对多核销"
            );
            logs.add(execute(req));
        }

        log.info("多对多核销完成: sourceType={}, sourceId={}, total={}, splits={}",
                sourceDocType, sourceDocId, totalAmount, allocations.size());
        return logs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReconciliationLogEntity> smartAllocate(
            String sourceDocType, Long sourceDocId,
            Long partyId, String partyType,
            String targetDocType,
            BigDecimal totalAmount,
            String period, String summary) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        String effectivePeriod = period != null ? period
                : java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        BigDecimal remaining = totalAmount;
        List<ReconciliationLogEntity> logs = new ArrayList<>();

        // P34: 统一查询 BusinessDocEntity
        List<BusinessDocEntity> invoices = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq("INVOICE_OUT".equals(targetDocType), BusinessDocEntity::getCustomerId, partyId)
                        .eq("INVOICE_IN".equals(targetDocType), BusinessDocEntity::getSupplierId, partyId)
                        .eq(BusinessDocEntity::getDocType, targetDocType)
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                        .in(BusinessDocEntity::getStatus, List.of("APPROVED", "VOUCHERED", "PARTIALLY_RECONCILED"))
                        .orderByAsc(BusinessDocEntity::getDueDate)
        );
        for (BusinessDocEntity inv : invoices) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal alloc = remaining.min(inv.getUnsettledAmount());
            ExecuteRequest req = new ExecuteRequest(
                    sourceDocType, sourceDocId,
                    targetDocType, inv.getId(),
                    alloc, BigDecimal.ZERO, "AUTO",
                    "INVOICE_OUT".equals(targetDocType) ? partyId : null,
                    "INVOICE_IN".equals(targetDocType) ? partyId : null,
                    effectivePeriod,
                    summary != null ? summary : "智能最优匹配核销"
            );
            logs.add(execute(req));
            remaining = remaining.subtract(alloc);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.info("智能匹配后有剩余金额 {} 未分配, 需转预收/预付 (partyId={}, partyType={})",
                    remaining, partyId, partyType);
        }

        log.info("智能最优匹配完成: sourceType={}, sourceId={}, partyId={}, total={}, logs={}",
                sourceDocType, sourceDocId, partyId, totalAmount, logs.size());
        return logs;
    }
}
