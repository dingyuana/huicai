package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.constant.BusinessDocStatus;
import com.huicai.sme.arap.service.ArapBalanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 应收应付余额汇总实现（P75）。
 * <p>
 * 口径（对齐 SPEC P75 V1.1）：
 * <ul>
 *   <li>单据侧：docType ∈ (INVOICE_OUT, OTHER_RECEIVABLE) / (INVOICE_IN, OTHER_PAYABLE)，period ≤ 查询期间，
 *       status ∈ (APPROVED, VOUCHERED, PARTIALLY_RECONCILED, FULLY_RECONCILED)；期间内经有效状态过滤</li>
 *   <li>核销侧：period = 查询期间，partyType = CUSTOMER(应收)/VENDOR(应付)，status ∈ (CONFIRMED, VOUCHERED)，
 *       totalAmount &gt; 0（排除 -H 红字对冲单）</li>
 *   <li>期初推导：openingUnsettled = closingUnsettled − currentAmount + currentSettled（会计恒等式）</li>
 * </ul>
 * 数据权限由 EnterpriseDataPermissionInterceptor 自动注入 enterprise_id，服务不做手工过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArapBalanceReportServiceImpl implements ArapBalanceReportService {

    private static final List<String> RECEIVABLE_DOC_TYPES = List.of("INVOICE_OUT", "OTHER_RECEIVABLE");
    private static final List<String> PAYABLE_DOC_TYPES = List.of("INVOICE_IN", "OTHER_PAYABLE");

    private static final List<String> OPEN_DOC_STATUSES = List.of(
            BusinessDocStatus.APPROVED,
            BusinessDocStatus.VOUCHERED,
            BusinessDocStatus.PARTIALLY_RECONCILED,
            BusinessDocStatus.FULLY_RECONCILED);

    private static final List<String> SETTLED_STATUSES = List.of(
            ArapStatus.CONFIRMED,
            ArapStatus.VOUCHERED);

    private final BusinessDocMapper businessDocMapper;
    private final ArapSettlementMapper settlementMapper;
    private final PeriodMapper periodMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;

    @Override
    public ArapBalanceSummaryVO getBalanceSummary(String period, Long customerId, Long vendorId) {
        requirePeriod(period);
        requirePeriodExists(period);

        List<ArapBalancePartyVO> receivables = buildSide(period, RECEIVABLE_DOC_TYPES, "CUSTOMER", customerId, true);
        List<ArapBalancePartyVO> payables = buildSide(period, PAYABLE_DOC_TYPES, "VENDOR", vendorId, false);

        BigDecimal receivableTotal = receivables.stream()
                .map(ArapBalancePartyVO::closingUnsettled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payableTotal = payables.stream()
                .map(ArapBalancePartyVO::closingUnsettled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean consistent = isConsistent(receivables) && isConsistent(payables);
        log.info("余额汇总: period={}, 应收行={}, 应付行={}, consistent={}",
                period, receivables.size(), payables.size(), consistent);
        return new ArapBalanceSummaryVO(period, consistent, receivableTotal, payableTotal, receivables, payables);
    }

    /**
     * 构建单侧（应收/应付）余额行。
     * <p>
     * 调用顺序约定：应收侧先于应付侧（businessDocMapper.selectList 与 settlementMapper.selectList 各两次，
     * Mockito thenReturn 按调用顺序消费，应收在前应付在后）。
     */
    private List<ArapBalancePartyVO> buildSide(String period, List<String> docTypes, String partyType,
                                               Long partyIdFilter, boolean receivableSide) {
        List<BusinessDocEntity> docs = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .in(BusinessDocEntity::getDocType, docTypes)
                        .le(BusinessDocEntity::getPeriod, period)
                        .in(BusinessDocEntity::getStatus, OPEN_DOC_STATUSES)
                        .eq(partyIdFilter != null,
                                receivableSide ? BusinessDocEntity::getCustomerId : BusinessDocEntity::getSupplierId,
                                partyIdFilter));

        List<ArapSettlementEntity> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntity>()
                        .eq(ArapSettlementEntity::getPeriod, period)
                        .eq(ArapSettlementEntity::getPartyType, partyType)
                        .in(ArapSettlementEntity::getStatus, SETTLED_STATUSES)
                        .gt(ArapSettlementEntity::getTotalAmount, BigDecimal.ZERO));

        Map<Long, List<BusinessDocEntity>> docsByParty = docs.stream()
                .filter(d -> receivableSide ? d.getCustomerId() != null : d.getSupplierId() != null)
                .collect(Collectors.groupingBy(
                        d -> receivableSide ? d.getCustomerId() : d.getSupplierId()));

        Map<Long, BigDecimal> settledByParty = settlements.stream()
                .filter(s -> s.getPartyId() != null)
                .filter(s -> s.getTotalAmount() != null && s.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(s -> SETTLED_STATUSES.contains(s.getStatus()))
                .filter(s -> partyType.equals(s.getPartyType()))
                .filter(s -> period.equals(s.getPeriod()))
                .collect(Collectors.groupingBy(
                        ArapSettlementEntity::getPartyId,
                        Collectors.reducing(BigDecimal.ZERO, ArapSettlementEntity::getTotalAmount, BigDecimal::add)));

        Map<Long, String> names = receivableSide
                ? customerNames(docsByParty.keySet())
                : vendorNames(docsByParty.keySet());

        List<ArapBalancePartyVO> rows = new ArrayList<>();
        for (Map.Entry<Long, List<BusinessDocEntity>> e : docsByParty.entrySet()) {
            Long partyId = e.getKey();
            List<BusinessDocEntity> partyDocs = e.getValue();
            BigDecimal closingUnsettled = partyDocs.stream()
                    .map(BusinessDocEntity::getUnsettledAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal currentAmount = partyDocs.stream()
                    .filter(d -> period.equals(d.getPeriod()) && d.getAmount() != null)
                    .map(BusinessDocEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal currentSettled = settledByParty.getOrDefault(partyId, BigDecimal.ZERO);
            BigDecimal openingUnsettled = closingUnsettled.subtract(currentAmount).add(currentSettled);
            rows.add(new ArapBalancePartyVO(partyId, names.get(partyId),
                    openingUnsettled, currentAmount, currentSettled, closingUnsettled));
        }
        rows.sort(Comparator.comparing(ArapBalancePartyVO::partyId));
        return rows;
    }

    private Map<Long, String> customerNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        customerMapper.selectBatchIds(ids)
                .forEach(c -> names.put(c.getId(), c.getName()));
        return names;
    }

    private Map<Long, String> vendorNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        vendorMapper.selectBatchIds(ids)
                .forEach(v -> names.put(v.getId(), v.getName()));
        return names;
    }

    private boolean isConsistent(List<ArapBalancePartyVO> rows) {
        for (ArapBalancePartyVO r : rows) {
            BigDecimal identity = r.openingUnsettled().add(r.currentAmount()).subtract(r.currentSettled());
            if (identity.compareTo(r.closingUnsettled()) != 0) {
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
}