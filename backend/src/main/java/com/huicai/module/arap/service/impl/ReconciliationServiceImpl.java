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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final BigDecimal TOLERANCE = new BigDecimal("5.00");
    private static final BigDecimal TAIL_DIFF = new BigDecimal("0.50");
    private static final BigDecimal SCORE_THRESHOLD = new BigDecimal("0.70");
    private static final BigDecimal GREEN_THRESHOLD = new BigDecimal("0.95");
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
        return recommend(customerId, null, amount, summary, counterpartyName, true, "receipt", receiptId);
    }

    @Override
    public RecommendResult recommendPayment(Long paymentId, Long vendorId, BigDecimal amount, String summary, String counterpartyName) {
        return recommend(null, vendorId, amount, summary, counterpartyName, false, "payment", paymentId);
    }

    @Override
    public RecommendResult recommendForStatement(Long statementId, Long accountId, String direction, BigDecimal amount, String counterpartyName, String summary) {
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
        return recommend(isReceipt ? partyId : null, isReceipt ? null : partyId, amount, summary, counterpartyName, isReceipt, "bank_txn", statementId);
    }

    private RecommendResult recommend(Long customerId, Long vendorId, BigDecimal amount, String summary, String counterpartyName, boolean isReceipt, String sourceType, Long sourceId) {
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
            String targetDocType = isReceipt ? "INVOICE_OUT" : "INVOICE_IN";
            String invoiceSummary;
            String invoiceCustomerName = "";

            if (invoice instanceof ReceivableEntity r) {
                unsettledAmount = r.getUnsettledAmount();
                invoiceNo = String.valueOf(r.getId());
                targetDocId = r.getId();
                invoiceSummary = r.getSummary() != null ? r.getSummary() : "";
            } else if (invoice instanceof PayableEntity p) {
                unsettledAmount = p.getUnsettledAmount();
                invoiceNo = String.valueOf(p.getId());
                targetDocId = p.getId();
                invoiceSummary = p.getSummary() != null ? p.getSummary() : "";
            } else {
                continue;
            }

            if (unsettledAmount == null || unsettledAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal matchScore = calculateScore(amount, unsettledAmount, summary, invoiceSummary, counterpartyName, invoiceCustomerName);
            if (matchScore.compareTo(SCORE_THRESHOLD) < 0) continue;

            String matchLevel = matchScore.compareTo(GREEN_THRESHOLD) >= 0 ? "GREEN" : "YELLOW";
            BigDecimal suggestedAmount = amount.min(unsettledAmount);

            items.add(new RecommendItem(targetDocId, invoiceNo, targetDocType, amount, unsettledAmount, matchScore, matchLevel, suggestedAmount));
        }

        items.sort((a, b) -> b.matchScore().compareTo(a.matchScore()));
        return new RecommendResult(sourceType, sourceId, counterpartyName, amount, items);
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
    @Transactional(rollbackFor = Exception.class)
    public List<ReconciliationLogEntity> batchExecute(List<ExecuteRequest> requests) {
        return requests.stream().map(this::execute).collect(Collectors.toList());
    }

    @Override
    public List<ReconciliationLogEntity> getRecords(String sourceDocType, Long sourceDocId) {
        return logMapper.findBySource(sourceDocType, sourceDocId);
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
