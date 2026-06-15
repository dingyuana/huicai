package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.*;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.arap.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final ReconciliationLogMapper logMapper;
    private final ArapSettlementService settlementService;

    @Override
    public RecommendResult recommendReceipt(Long receiptId, Long customerId, BigDecimal amount, String summary, String counterpartyName) {
        return recommend(customerId, null, amount, summary, counterpartyName, true, "receipt", receiptId, null, null);
    }

    @Override
    public RecommendResult recommendPayment(Long paymentId, Long vendorId, BigDecimal amount, String summary, String counterpartyName) {
        return recommend(null, vendorId, amount, summary, counterpartyName, false, "payment", paymentId, null, null);
    }

    @Override
    public RecommendResult recommendForStatement(Long statementId, Long accountId, String direction, BigDecimal amount, String counterpartyName, String summary, LocalDate txDate, String externalNo) {
        boolean isReceipt = "in".equalsIgnoreCase(direction);
        String customerName = counterpartyName;
        Long partyId = null;
        if (isReceipt) {
            List<CustomerEntity> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<CustomerEntity>().like(CustomerEntity::getName, counterpartyName).last("LIMIT 1"));
            if (!customers.isEmpty()) partyId = customers.get(0).getId();
        } else {
            List<VendorEntity> vendors = vendorMapper.selectList(
                    new LambdaQueryWrapper<VendorEntity>().like(VendorEntity::getName, counterpartyName).last("LIMIT 1"));
            if (!vendors.isEmpty()) partyId = vendors.get(0).getId();
        }
        return recommend(isReceipt ? partyId : null, isReceipt ? null : partyId, amount, summary, counterpartyName, isReceipt, "bank_txn", statementId, txDate, externalNo);
    }

    private RecommendResult recommend(Long customerId, Long vendorId, BigDecimal amount, String summary, String counterpartyName, boolean isReceipt, String sourceType, Long sourceId, LocalDate txDate, String externalNo) {
        List<RecommendItem> items = new ArrayList<>();
        List<?> invoices;
        if (isReceipt && customerId != null) {
            invoices = receivableMapper.selectList(
                    new LambdaQueryWrapper<ReceivableEntity>()
                            .eq(ReceivableEntity::getCustomerId, customerId));
        } else if (!isReceipt && vendorId != null) {
            invoices = payableMapper.selectList(
                    new LambdaQueryWrapper<PayableEntity>()
                            .eq(PayableEntity::getVendorId, vendorId));
        } else {
            return new RecommendResult(sourceType, sourceId, counterpartyName, amount, List.of());
        }

        for (Object invoice : invoices) {
            BigDecimal unsettledAmount;
            String invoiceNo;
            Long targetDocId;
            LocalDate invoiceTxDate = null;
            String targetDocType = isReceipt ? "INVOICE_OUT" : "INVOICE_IN";
            String invoiceSummary;
            String invoiceCustomerName = "";

            if (invoice instanceof ReceivableEntity r) {
                unsettledAmount = r.getUnsettledAmount();
                invoiceNo = String.valueOf(r.getId());
                targetDocId = r.getId();
                invoiceTxDate = r.getTxDate();
                invoiceSummary = r.getSummary() != null ? r.getSummary() : "";
            } else if (invoice instanceof PayableEntity p) {
                unsettledAmount = p.getUnsettledAmount();
                invoiceNo = String.valueOf(p.getId());
                targetDocId = p.getId();
                invoiceTxDate = p.getTxDate();
                invoiceSummary = p.getSummary() != null ? p.getSummary() : "";
            } else {
                continue;
            }

            if (unsettledAmount == null || unsettledAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 确定 L1-L5 匹配级别
            String matchLevel = determineMatchLevel(amount, unsettledAmount, summary, invoiceSummary,
                    counterpartyName, externalNo, invoiceNo, txDate, invoiceTxDate);
            if (matchLevel == null) continue; // 未达到最低匹配标准

            // 连续分: 同类级别内排序用
            BigDecimal matchScore = calculateScore(amount, unsettledAmount, summary, invoiceSummary, counterpartyName, invoiceCustomerName);
            BigDecimal suggestedAmount = amount.min(unsettledAmount);

            items.add(new RecommendItem(targetDocId, invoiceNo, targetDocType, amount, unsettledAmount, matchScore, matchLevel, suggestedAmount));
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
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationLogEntity execute(ExecuteRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("核销金额必须大于0");
        }

        // Update target invoice unsettled amount
        if ("INVOICE_OUT".equals(request.targetDocType())) {
            ReceivableEntity r = receivableMapper.selectById(request.targetDocId());
            if (r == null) throw new BusinessException("应收记录不存在: " + request.targetDocId());
            BigDecimal newSettled = r.getSettledAmount().add(request.amount());
            r.setSettledAmount(newSettled);
            r.setUnsettledAmount(r.getAmount().subtract(newSettled));
            receivableMapper.updateById(r);
        } else if ("INVOICE_IN".equals(request.targetDocType())) {
            PayableEntity p = payableMapper.selectById(request.targetDocId());
            if (p == null) throw new BusinessException("应付记录不存在: " + request.targetDocId());
            BigDecimal newSettled = p.getSettledAmount().add(request.amount());
            p.setSettledAmount(newSettled);
            p.setUnsettledAmount(p.getAmount().subtract(newSettled));
            payableMapper.updateById(p);
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
        reconLog.setStatus("CONFIRMED");
        reconLog.setRemark(request.remark());
        reconLog.setCreatedBy(DEFAULT_USER_ID);
        logMapper.insert(reconLog);

        // Also create settlement record via existing ArapSettlementService
        if (StrUtil.isNotBlank(request.period())) {
            try {
                ArapSettlementEntity settlement = new ArapSettlementEntity();
                settlement.setSettlementType("INVOICE_OUT".equals(request.targetDocType()) ? "RECEIVABLE" : "PAYABLE");
                settlement.setPeriod(request.period());
                settlement.setSettlementDate(java.time.LocalDate.now());
                settlement.setTotalAmount(request.amount());
                settlement.setPartyId(request.customerId() != null ? request.customerId() : request.vendorId());
                settlement.setPartyType(request.customerId() != null ? "CUSTOMER" : "VENDOR");
                settlement.setStatus("CONFIRMED");
                String prefix = "RECEIVABLE".equals(settlement.getSettlementType()) ? "JS" : "FS";
                settlement.setSettlementNo(prefix + "-" + request.period() + "-" + cn.hutool.core.util.IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());

                ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
                if ("INVOICE_OUT".equals(request.targetDocType())) {
                    entry.setReceivableId(request.targetDocId());
                } else {
                    entry.setPayableId(request.targetDocId());
                }
                entry.setSettledAmount(request.amount());

                settlementService.create(settlement, List.of(entry));
            } catch (Exception e) {
                log.warn("创建核销单记录失败(不影响核销): {}", e.getMessage());
            }
        }

        log.info("核销执行完成: sourceType={}, sourceId={}, targetId={}, amount={}", request.sourceDocType(), request.sourceDocId(), request.targetDocId(), request.amount());
        return reconLog;
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
        if ("INVOICE_OUT".equals(request.targetDocType())) {
            ReceivableEntity r = receivableMapper.selectById(request.targetDocId());
            invoiceExists = r != null && r.getUnsettledAmount().compareTo(BigDecimal.ZERO) > 0;
            invoiceMsg = invoiceExists ? "应收单据有效" : "应收单据不存在或已结清";
        } else if ("INVOICE_IN".equals(request.targetDocType())) {
            PayableEntity p = payableMapper.selectById(request.targetDocId());
            invoiceExists = p != null && p.getUnsettledAmount().compareTo(BigDecimal.ZERO) > 0;
            invoiceMsg = invoiceExists ? "应付单据有效" : "应付单据不存在或已结清";
        } else {
            invoiceMsg = "未知单据类型: " + request.targetDocType();
        }
        checks.add(new PreCheckItem("invoiceValid", invoiceExists, invoiceMsg));

        // 3. 客商一致: 来源与目标客商匹配
        boolean partyMatch = false;
        String partyMsg;
        if ("INVOICE_OUT".equals(request.targetDocType()) && request.customerId() != null) {
            ReceivableEntity r = receivableMapper.selectById(request.targetDocId());
            partyMatch = r != null && r.getCustomerId().equals(request.customerId());
            partyMsg = partyMatch ? "客商一致(客户)" : "客户不匹配";
        } else if ("INVOICE_IN".equals(request.targetDocType()) && request.vendorId() != null) {
            PayableEntity p = payableMapper.selectById(request.targetDocId());
            partyMatch = p != null && p.getVendorId().equals(request.vendorId());
            partyMsg = partyMatch ? "客商一致(供应商)" : "供应商不匹配";
        } else {
            partyMatch = true;
            partyMsg = "客商未指定, 跳过检查";
        }
        checks.add(new PreCheckItem("partyMatch", partyMatch, partyMsg));

        // 4. 金额充足: 核销金额 ≤ 未结算金额
        boolean amountValid = false;
        String amountMsg;
        BigDecimal unsettled = BigDecimal.ZERO;
        if ("INVOICE_OUT".equals(request.targetDocType())) {
            ReceivableEntity r = receivableMapper.selectById(request.targetDocId());
            unsettled = r != null ? r.getUnsettledAmount() : BigDecimal.ZERO;
        } else if ("INVOICE_IN".equals(request.targetDocType())) {
            PayableEntity p = payableMapper.selectById(request.targetDocId());
            unsettled = p != null ? p.getUnsettledAmount() : BigDecimal.ZERO;
        }
        amountValid = request.amount().compareTo(unsettled) <= 0;
        amountMsg = amountValid ? "核销金额 " + request.amount() + " ≤ 未结算金额 " + unsettled : "核销金额超过未结算余额";
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
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long logId) {
        ReconciliationLogEntity reconLog = logMapper.selectById(logId);
        if (reconLog == null) throw new BusinessException("核销记录不存在");
        if (!"CONFIRMED".equals(reconLog.getStatus())) throw new BusinessException("仅已确认的核销可反核销");

        // Restore target invoice unsettled amount
        BigDecimal amount = reconLog.getAllocatedAmount();
        if ("INVOICE_OUT".equals(reconLog.getTargetDocType())) {
            ReceivableEntity r = receivableMapper.selectById(reconLog.getTargetDocId());
            if (r != null) {
                BigDecimal newSettled = r.getSettledAmount().subtract(amount);
                r.setSettledAmount(newSettled);
                r.setUnsettledAmount(r.getAmount().subtract(newSettled));
                receivableMapper.updateById(r);
            }
        } else if ("INVOICE_IN".equals(reconLog.getTargetDocType())) {
            PayableEntity p = payableMapper.selectById(reconLog.getTargetDocId());
            if (p != null) {
                BigDecimal newSettled = p.getSettledAmount().subtract(amount);
                p.setSettledAmount(newSettled);
                p.setUnsettledAmount(p.getAmount().subtract(newSettled));
                payableMapper.updateById(p);
            }
        }

        reconLog.setStatus("CANCELLED");
        logMapper.updateById(reconLog);
        log.info("反核销完成: logId={}, amount={}", logId, amount);
    }
}
