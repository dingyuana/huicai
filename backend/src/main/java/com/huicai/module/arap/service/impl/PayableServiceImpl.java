package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.dto.PayableVO;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.PayableService;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayableServiceImpl implements PayableService {

    private static final Logger log = LoggerFactory.getLogger(PayableServiceImpl.class);

    private final PayableMapper mapper;
    private final VendorMapper vendorMapper;
    private final BusinessDocMapper businessDocMapper;

    private static final List<String> AGING_BUCKETS = List.of(
            "current", "days_0_30", "days_31_60", "days_61_90",
            "days_91_180", "days_181_365", "over_365"
    );

    @Override
    public IPage<PayableVO> pageQuery(Long vendorId, String period, String docNo, String invoiceNo, String voucherNo, Integer current, Integer size) {
        Page<BusinessDocEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BusinessDocEntity::getSupplierId);
        if (vendorId != null) wrapper.eq(BusinessDocEntity::getSupplierId, vendorId);
        if (period != null && !period.isBlank()) wrapper.eq(BusinessDocEntity::getPeriod, period);
        if (docNo != null && !docNo.isBlank()) wrapper.eq(BusinessDocEntity::getDocNo, docNo);
        if (invoiceNo != null && !invoiceNo.isBlank()) wrapper.eq(BusinessDocEntity::getInvoiceNo, invoiceNo);
        if (voucherNo != null && !voucherNo.isBlank()) wrapper.eq(BusinessDocEntity::getVoucherNo, voucherNo);
        wrapper.gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO);
        wrapper.orderByDesc(BusinessDocEntity::getDocDate);
        IPage<BusinessDocEntity> entityPage = businessDocMapper.selectPage(page, wrapper);

        IPage<PayableVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<PayableVO> vos = entityPage.getRecords().stream()
                .map(this::toPayableVO).collect(Collectors.toList());
        populatePartyNames(vos);
        voPage.setRecords(vos);
        return voPage;
    }

    private PayableVO toPayableVO(BusinessDocEntity e) {
        PayableVO vo = new PayableVO();
        vo.setId(e.getId());
        vo.setVendorId(e.getSupplierId());
        vo.setDocId(e.getId());
        vo.setDocNo(e.getDocNo());
        vo.setInvoiceNo(e.getInvoiceNo());
        vo.setVoucherId(e.getVoucherId());
        vo.setVoucherNo(e.getVoucherNo());
        vo.setPeriod(e.getPeriod());
        vo.setTxDate(e.getDocDate());
        vo.setAmount(e.getAmount());
        vo.setSettledAmount(e.getSettledAmount());
        vo.setUnsettledAmount(e.getUnsettledAmount());
        vo.setDueDate(e.getDueDate());
        vo.setSummary(e.getSummary());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    @Override
    public PayableVO getById(Long id) {
        BusinessDocEntity entity = businessDocMapper.selectById(id);
        if (entity == null || entity.getSupplierId() == null) {
            throw new BusinessException("应付明细不存在");
        }
        PayableVO vo = toPayableVO(entity);
        populatePartyNames(List.of(vo));
        return vo;
    }

    @Override
    public PayableEntity create(PayableEntity entity) {
        if (entity.getSettledAmount() == null) entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(entity.getAmount().subtract(entity.getSettledAmount()));
        if (entity.getStatus() == null) entity.setStatus(ArapStatus.CONFIRMED);
        mapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Long userId) {
        PayableEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("应付单不存在");
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态的应付单可确认, 当前: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        entity.setAuditedBy(userId);       // 记录审核人
        entity.setAuditedAt(LocalDateTime.now());  // 记录审核时间
        entity.setVersion(null);
        mapper.updateById(entity);
        log.info("应付单确认: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSettled(Long id, Long userId) {
        PayableEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("应付单不存在");
        if (!ArapStatus.isConfirmed(entity.getStatus())) {
            throw new BusinessException("仅已确认状态的应付单可标记结清, 当前: " + entity.getStatus());
        }
        if (entity.getUnsettledAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("应付单未结清余额不为零, 不可标记结清");
        }
        entity.setStatus(ArapStatus.SETTLED);
        entity.setVersion(null);
        mapper.updateById(entity);
        log.info("应付单结清: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id, Long userId) {
        PayableEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("应付单不存在");
        if (!ArapStatus.isReversible(entity.getStatus())) {
            throw new BusinessException("仅已确认或已结清的应付单可冲销, 当前: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.REVERSED);
        entity.setVersion(null);
        mapper.updateById(entity);
        log.info("应付单冲销: id={}, userId={}", id, userId);
    }

    @Override
    public Map<String, Object> agingAnalysis(Long vendorId) {
        List<Map<String, Object>> rows = mapper.agingByVendor(vendorId);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String bucket : AGING_BUCKETS) {
            amounts.put(bucket, BigDecimal.ZERO);
            counts.put(bucket, 0);
        }
        for (Map<String, Object> row : rows) {
            String bucket = (String) row.get("aging_bucket");
            Object amountObj = row.get("amount");
            Object countObj = row.get("count");
            amounts.put(bucket, amountObj == null ? BigDecimal.ZERO :
                    new BigDecimal(amountObj.toString()));
            counts.put(bucket, countObj == null ? 0 : ((Number) countObj).intValue());
        }
        result.put("buckets", amounts.keySet());
        result.put("amounts", amounts.values());
        result.put("counts", counts.values());
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("total", total);
        return result;
    }

    private void populatePartyNames(List<PayableVO> vos) {
        if (vos.isEmpty()) return;
        List<Long> vendorIds = vos.stream()
                .map(PayableVO::getVendorId).filter(java.util.Objects::nonNull).distinct().toList();
        if (vendorIds.isEmpty()) return;
        Map<Long, String> nameMap = vendorMapper.selectBatchIds(vendorIds).stream()
                .collect(Collectors.toMap(com.huicai.module.arap.entity.VendorEntity::getId, com.huicai.module.arap.entity.VendorEntity::getName));
        for (PayableVO vo : vos) {
            if (vo.getVendorId() != null) vo.setVendorName(nameMap.get(vo.getVendorId()));
        }
    }
}
