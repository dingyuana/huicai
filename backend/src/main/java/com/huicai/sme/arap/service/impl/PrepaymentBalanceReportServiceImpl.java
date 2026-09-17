package com.huicai.sme.arap.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.PrepaymentBalanceReportService;
import com.huicai.sme.arap.service.PrepaymentBalanceReportService.PrepaymentBalancePartyVO;
import com.huicai.sme.arap.service.PrepaymentBalanceReportService.PrepaymentBalanceSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预收预付余额汇总实现（P78）。
 * <p>
 * 口径（对齐 SPEC P78 V1.0，代码实证 2026-09-17）：
 * <ul>
 *   <li>数据源 t_prepayment，status IN (CONFIRMED, APPLIED, REVERSED)（排除 DRAFT）；
 *       预收按 customerId 分组（PRE_RECEIPT），预付按 vendorId 分组（PRE_PAYMENT），两侧互不混入</li>
 *   <li>有效期间：PrepaymentEntity.period 在 create() 未赋值，非空则用，否则回退 txDate 的 yyyyMM</li>
 *   <li>currentCreated：有效期间 = 查询期间，聚合 amount（含后续被冲销的单据）</li>
 *   <li>currentApplied：t_arap_settlement 前缀隔离——YS-（预收冲应收，CUSTOMER）/ YF-（预付冲应付，VENDOR），
 *       status IN (CONFIRMED, VOUCHERED)、period = 查询期间、totalAmount &gt; 0；前缀隔离避免与 P75 的 JS/FS 双计</li>
 *   <li>currentReversed：status = REVERSED 且 有效期间 = 查询期间，聚合 amount</li>
 *   <li>closingUnsettled：status IN (CONFIRMED, APPLIED) 且 有效期间 ≤ 查询期间，求和 unsettledAmount</li>
 *   <li>openingUnsettled = closingUnsettled − currentCreated + currentApplied + currentReversed（会计恒等式）</li>
 * </ul>
 * 数据权限由 EnterpriseDataPermissionInterceptor 自动注入 enterprise_id（t_prepayment /
 * t_arap_settlement 均非共享表），服务不做手工过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepaymentBalanceReportServiceImpl implements PrepaymentBalanceReportService {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 预付冲应付结算单前缀（PrepaymentServiceImpl.applyToPayable） */
    private static final String SETTLE_PREFIX_PRE_PAYMENT = "YF-";
    /** 预收冲应收结算单前缀（PrepaymentServiceImpl.applyToReceivable） */
    private static final String SETTLE_PREFIX_PRE_RECEIPT = "YS-";

    /** 期末未结清计入状态（敞口） */
    private static final List<String> OPEN_PREPAYMENT_STATUSES = List.of(
            ArapStatus.CONFIRMED, ArapStatus.APPLIED);
    /** 有效单据全部状态（排除 DRAFT，含 REVERSED） */
    private static final List<String> VALID_PREPAYMENT_STATUSES = List.of(
            ArapStatus.CONFIRMED, ArapStatus.APPLIED, ArapStatus.REVERSED);
    /** 抵扣结算单有效状态（对齐 P75） */
    private static final List<String> SETTLED_STATUSES = List.of(
            ArapStatus.CONFIRMED, ArapStatus.VOUCHERED);

    public static final String PARTY_TYPE_PRE_RECEIPT = "PRE_RECEIPT";
    public static final String PARTY_TYPE_PRE_PAYMENT = "PRE_PAYMENT";

    private final PrepaymentMapper prepaymentMapper;
    private final ArapSettlementMapper settlementMapper;
    private final PeriodMapper periodMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;

    @Override
    public PrepaymentBalanceSummaryVO getBalanceSummary(String period, String partyType, Long partyId) {
        requirePeriod(period);
        normalizePartyType(partyType);
        requirePeriodExists(period);

        // 按显式 partyType 决定输出侧；null = 两侧都出
        List<PrepaymentBalancePartyVO> preReceipts;
        List<PrepaymentBalancePartyVO> prePayments;
        boolean includePreReceipt = partyType == null || PARTY_TYPE_PRE_RECEIPT.equals(partyType);
        boolean includePrePayment = partyType == null || PARTY_TYPE_PRE_PAYMENT.equals(partyType);

        preReceipts = includePreReceipt
                ? buildSide(period, PARTY_TYPE_PRE_RECEIPT, true, partyId)
                : List.of();
        prePayments = includePrePayment
                ? buildSide(period, PARTY_TYPE_PRE_PAYMENT, false, partyId)
                : List.of();

        BigDecimal preReceiptTotal = sumClosing(preReceipts);
        BigDecimal prePaymentTotal = sumClosing(prePayments);
        boolean consistent = isConsistent(preReceipts) && isConsistent(prePayments);
        log.info("预收预付余额汇总: period={}, partyType={}, 预收行={}, 预付行={}, consistent={}",
                period, partyType, preReceipts.size(), prePayments.size(), consistent);
        return new PrepaymentBalanceSummaryVO(period, partyType, consistent,
                preReceiptTotal, prePaymentTotal, preReceipts, prePayments);
    }

    /**
     * 构建单侧（预收/预付）余额行。
     *
     * @param period      查询期间
     * @param partyType   PRE_RECEIPT / PRE_PAYMENT
     * @param customerSide true=预收（按 customerId 分组，YS- 前缀），false=预付（按 vendorId 分组，YF- 前缀）
     * @param partyIdFilter 可选单位过滤（对应侧的 customerId / vendorId）
     */
    private List<PrepaymentBalancePartyVO> buildSide(String period, String partyType,
                                                     boolean customerSide, Long partyIdFilter) {
        // 预收单 customerId 非空，预付单 vendorId 非空——两侧互不混入（对称负向断言）
        List<PrepaymentEntity> prepayments = prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .in(PrepaymentEntity::getStatus, VALID_PREPAYMENT_STATUSES)
                        .eq(customerSide && partyIdFilter != null, PrepaymentEntity::getCustomerId, partyIdFilter)
                        .eq(!customerSide && partyIdFilter != null, PrepaymentEntity::getVendorId, partyIdFilter)
                        .isNotNull(customerSide, PrepaymentEntity::getCustomerId)
                        .isNotNull(!customerSide, PrepaymentEntity::getVendorId));

        // 抵扣结算单：前缀隔离 + partyType 隔离
        String settlePrefix = customerSide ? SETTLE_PREFIX_PRE_RECEIPT : SETTLE_PREFIX_PRE_PAYMENT;
        String settlePartyType = customerSide ? "CUSTOMER" : "VENDOR";
        List<ArapSettlementEntity> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntity>()
                        .likeRight(ArapSettlementEntity::getSettlementNo, settlePrefix)
                        .eq(ArapSettlementEntity::getPartyType, settlePartyType)
                        .eq(ArapSettlementEntity::getPeriod, period)
                        .in(ArapSettlementEntity::getStatus, SETTLED_STATUSES)
                        .gt(ArapSettlementEntity::getTotalAmount, BigDecimal.ZERO)
                        .eq(partyIdFilter != null, ArapSettlementEntity::getPartyId, partyIdFilter));

        // 按单位分组的有效单据
        Map<Long, List<PrepaymentEntity>> docsByParty = prepayments.stream()
                .filter(p -> customerSide ? p.getCustomerId() != null : p.getVendorId() != null)
                .collect(Collectors.groupingBy(p -> customerSide ? p.getCustomerId() : p.getVendorId()));

        // 抵扣金额按单位聚合（前缀在 Java 侧二次过滤：wrapper 的 likeRight 仅在 DB 层生效，
        // 防御性二次校验 + mock 可测，对齐 P75 的 "wrapper 条件 + Java 侧再 filter" 风格）
        Map<Long, BigDecimal> appliedByParty = settlements.stream()
                .filter(s -> s.getPartyId() != null && s.getTotalAmount() != null
                        && s.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(s -> SETTLED_STATUSES.contains(s.getStatus()))
                .filter(s -> period.equals(s.getPeriod()))
                .filter(s -> s.getSettlementNo() != null && s.getSettlementNo().startsWith(settlePrefix))
                .collect(Collectors.groupingBy(
                        ArapSettlementEntity::getPartyId,
                        Collectors.reducing(BigDecimal.ZERO, ArapSettlementEntity::getTotalAmount, BigDecimal::add)));

        // 名称：单据侧 ∪ 抵扣侧（某单位只有抵扣无本期单据时仍需出名称）
        Set<Long> partyIds = new HashSet<>(docsByParty.keySet());
        partyIds.addAll(appliedByParty.keySet());
        Map<Long, String> names = customerSide
                ? customerNames(partyIds)
                : vendorNames(partyIds);

        List<PrepaymentBalancePartyVO> rows = new ArrayList<>();
        for (Map.Entry<Long, List<PrepaymentEntity>> e : docsByParty.entrySet()) {
            Long pId = e.getKey();
            List<PrepaymentEntity> partyDocs = e.getValue();

            BigDecimal currentCreated = partyDocs.stream()
                    .filter(p -> period.equals(effectivePeriod(p)) && p.getAmount() != null)
                    .map(PrepaymentEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal currentReversed = partyDocs.stream()
                    .filter(p -> ArapStatus.REVERSED.equals(p.getStatus())
                            && period.equals(effectivePeriod(p)) && p.getAmount() != null)
                    .map(PrepaymentEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal closingUnsettled = partyDocs.stream()
                    .filter(p -> OPEN_PREPAYMENT_STATUSES.contains(p.getStatus())
                            && effectivePeriod(p) != null
                            && effectivePeriod(p).compareTo(period) <= 0)
                    .map(PrepaymentEntity::getUnsettledAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal currentApplied = appliedByParty.getOrDefault(pId, BigDecimal.ZERO);
            BigDecimal openingUnsettled = closingUnsettled
                    .subtract(currentCreated)
                    .add(currentApplied)
                    .add(currentReversed);

            rows.add(new PrepaymentBalancePartyVO(
                    pId, names.get(pId), openingUnsettled, currentCreated,
                    currentApplied, currentReversed, closingUnsettled));
        }
        // 仅有抵扣、无本期单据的单位也补一行（纯抵扣单位防漏；opening=0−0+applied+0=applied）
        for (Long appliedParty : appliedByParty.keySet()) {
            if (docsByParty.containsKey(appliedParty)) continue;
            BigDecimal currentApplied = appliedByParty.get(appliedParty);
            rows.add(new PrepaymentBalancePartyVO(
                    appliedParty, names.get(appliedParty),
                    currentApplied, BigDecimal.ZERO,
                    currentApplied, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        rows.sort(Comparator.comparing(PrepaymentBalancePartyVO::partyId));
        return rows;
    }

    /** 有效期间：PrepaymentEntity.period 非空则用，否则回退 txDate 的 yyyyMM，均无则 null */
    private String effectivePeriod(PrepaymentEntity p) {
        if (StrUtil.isNotBlank(p.getPeriod())) {
            return p.getPeriod();
        }
        if (p.getTxDate() != null) {
            return p.getTxDate().format(YYYYMM);
        }
        return null;
    }

    private BigDecimal sumClosing(List<PrepaymentBalancePartyVO> rows) {
        return rows.stream()
                .map(PrepaymentBalancePartyVO::closingUnsettled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, String> customerNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) return names;
        customerMapper.selectBatchIds(ids).forEach(c -> names.put(c.getId(), c.getName()));
        return names;
    }

    private Map<Long, String> vendorNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) return names;
        vendorMapper.selectBatchIds(ids).forEach(v -> names.put(v.getId(), v.getName()));
        return names;
    }

    private boolean isConsistent(List<PrepaymentBalancePartyVO> rows) {
        for (PrepaymentBalancePartyVO r : rows) {
            // opening + created − applied − reversed == closing
            BigDecimal identity = r.openingUnsettled()
                    .add(r.currentCreated())
                    .subtract(r.currentApplied())
                    .subtract(r.currentReversed());
            if (identity.compareTo(r.closingUnsettled()) != 0) {
                log.warn("预收预付余额汇总恒等式断言失败: partyId={}, opening={}, created={}, applied={}, reversed={}, closing={}",
                        r.partyId(), r.openingUnsettled(), r.currentCreated(),
                        r.currentApplied(), r.currentReversed(), r.closingUnsettled());
                return false;
            }
        }
        return true;
    }

    private void requirePeriod(String period) {
        if (period == null || !period.matches("\\d{6}")) {
            throw BusinessException.badRequest("期间必填且必须为6位数字(YYYYMM)");
        }
    }

    private void requirePeriodExists(String period) {
        Long count = periodMapper.selectCount(
                new LambdaQueryWrapper<PeriodEntity>().eq(PeriodEntity::getPeriodCode, period));
        if (count == null || count == 0) {
            throw BusinessException.badRequest("会计期间不存在: " + period);
        }
    }

    private void normalizePartyType(String partyType) {
        if (partyType == null) return;
        if (!PARTY_TYPE_PRE_RECEIPT.equals(partyType) && !PARTY_TYPE_PRE_PAYMENT.equals(partyType)) {
            throw BusinessException.badRequest("party_type 非法: " + partyType
                    + "（允许 PRE_RECEIPT / PRE_PAYMENT）");
        }
    }
}
