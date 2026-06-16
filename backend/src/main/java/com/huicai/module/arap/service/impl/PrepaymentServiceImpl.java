package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.PrepaymentEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.PrepaymentMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.ReconciliationLogMapper;
import com.huicai.module.arap.service.PrepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 预付款/预收款服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepaymentServiceImpl implements PrepaymentService {

    private final PrepaymentMapper prepaymentMapper;
    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final ReconciliationLogMapper logMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity createPaymentPrepay(Long vendorId, BigDecimal amount, String period,
                                                  LocalDate txDate, String summary,
                                                  String sourceDocType, Long sourceDocId,
                                                  Long docId, Long voucherId, String createdBy) {
        if (vendorId == null) throw new BusinessException("供应商ID不能为空");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("预付款金额必须大于0");

        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setTenantId(1L);
        prepay.setPrepayType("PAYMENT_PREPAY");
        prepay.setVendorId(vendorId);
        prepay.setAmount(amount);
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(amount);
        prepay.setPeriod(period);
        prepay.setTxDate(txDate);
        prepay.setSummary(summary);
        prepay.setStatus("CONFIRMED");
        prepay.setSourceDocType(sourceDocType);
        prepay.setSourceDocId(sourceDocId);
        prepay.setDocId(docId);
        prepay.setVoucherId(voucherId);
        prepay.setCreatedBy(createdBy);
        prepaymentMapper.insert(prepay);
        log.info("P12 预付款创建: vendorId={}, prepayId={}, amount={}", vendorId, prepay.getId(), amount);
        return prepay;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity createReceiptPrepay(Long customerId, BigDecimal amount, String period,
                                                  LocalDate txDate, String summary,
                                                  String sourceDocType, Long sourceDocId,
                                                  Long docId, Long voucherId, String createdBy) {
        if (customerId == null) throw new BusinessException("客户ID不能为空");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("预收款金额必须大于0");

        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setTenantId(1L);
        prepay.setPrepayType("RECEIPT_PREPAY");
        prepay.setCustomerId(customerId);
        prepay.setAmount(amount);
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(amount);
        prepay.setPeriod(period);
        prepay.setTxDate(txDate);
        prepay.setSummary(summary);
        prepay.setStatus("CONFIRMED");
        prepay.setSourceDocType(sourceDocType);
        prepay.setSourceDocId(sourceDocId);
        prepay.setDocId(docId);
        prepay.setVoucherId(voucherId);
        prepay.setCreatedBy(createdBy);
        prepaymentMapper.insert(prepay);
        log.info("P12 预收款创建: customerId={}, prepayId={}, amount={}", customerId, prepay.getId(), amount);
        return prepay;
    }

    @Override
    public List<PrepaymentEntity> listPaymentPrepay(Long vendorId) {
        return prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getPrepayType, "PAYMENT_PREPAY")
                        .eq(vendorId != null, PrepaymentEntity::getVendorId, vendorId)
                        .orderByDesc(PrepaymentEntity::getCreatedAt));
    }

    @Override
    public List<PrepaymentEntity> listReceiptPrepay(Long customerId) {
        return prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getPrepayType, "RECEIPT_PREPAY")
                        .eq(customerId != null, PrepaymentEntity::getCustomerId, customerId)
                        .orderByDesc(PrepaymentEntity::getCreatedAt));
    }

    @Override
    public IPage<PrepaymentEntity> pagePaymentPrepay(Long vendorId, Integer current, Integer size) {
        Page<PrepaymentEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<PrepaymentEntity> wrapper = new LambdaQueryWrapper<PrepaymentEntity>()
                .eq(PrepaymentEntity::getPrepayType, "PAYMENT_PREPAY")
                .eq(vendorId != null, PrepaymentEntity::getVendorId, vendorId)
                .orderByDesc(PrepaymentEntity::getCreatedAt);
        return prepaymentMapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<PrepaymentEntity> pageReceiptPrepay(Long customerId, Integer current, Integer size) {
        Page<PrepaymentEntity> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        LambdaQueryWrapper<PrepaymentEntity> wrapper = new LambdaQueryWrapper<PrepaymentEntity>()
                .eq(PrepaymentEntity::getPrepayType, "RECEIPT_PREPAY")
                .eq(customerId != null, PrepaymentEntity::getCustomerId, customerId)
                .orderByDesc(PrepaymentEntity::getCreatedAt);
        return prepaymentMapper.selectPage(page, wrapper);
    }

    @Override
    public PrepaymentEntity getById(Long id) {
        return prepaymentMapper.selectById(id);
    }

    /**
     * 用预付款冲应付账款.
     * 冲销金额不能超过预付款的未结算金额与应付账款的未结算金额.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal settlePayable(Long prepaymentId, Long payableId, BigDecimal settleAmount, String remark) {
        PrepaymentEntity prepay = prepaymentMapper.selectById(prepaymentId);
        if (prepay == null) throw new BusinessException("预付款不存在: " + prepaymentId);
        if (!"CONFIRMED".equals(prepay.getStatus()))
            throw new BusinessException("预付款状态异常, 无法冲销: " + prepay.getStatus());
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("预付款已全部冲销");

        PayableEntity payable = payableMapper.selectById(payableId);
        if (payable == null) throw new BusinessException("应付账款不存在: " + payableId);
        if (payable.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("应付账款已全部结清");

        // 校验客商一致
        if (!prepay.getVendorId().equals(payable.getVendorId()))
            throw new BusinessException("预付款与应付账款供应商不一致");

        // 计算实际冲销金额: 取三者最小值
        BigDecimal actualSettle = settleAmount
                .min(prepay.getUnsettledAmount())
                .min(payable.getUnsettledAmount());
        if (actualSettle.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("冲销金额必须大于0");

        // 更新预付款
        prepay.setSettledAmount(prepay.getSettledAmount().add(actualSettle));
        prepay.setUnsettledAmount(prepay.getAmount().subtract(prepay.getSettledAmount()));
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            prepay.setStatus("SETTLED");
            prepay.setUnsettledAmount(BigDecimal.ZERO);
        }
        prepaymentMapper.updateById(prepay);

        // 更新应付账款
        payable.setSettledAmount(payable.getSettledAmount().add(actualSettle));
        payable.setUnsettledAmount(payable.getAmount().subtract(payable.getSettledAmount()));
        if (payable.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            payable.setUnsettledAmount(BigDecimal.ZERO);
        }
        payableMapper.updateById(payable);

        // 记录核销日志
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setTenantId(1L);
        log.setSourceDocType("PAYMENT_PREPAY");
        log.setSourceDocId(prepaymentId);
        log.setTargetDocType("INVOICE_IN");
        log.setTargetDocId(payableId);
        log.setAllocatedAmount(actualSettle);
        log.setDiscountAmount(BigDecimal.ZERO);
        log.setMatchScore(new BigDecimal("100"));
        log.setMatchMethod("AUTO");
        log.setStatus("EXECUTED");
        log.setRemark(remark != null ? remark : "预付款冲应付账款");
        logMapper.insert(log);

        log.info("P12 预付款冲应付完成: prepayId={}, payableId={}, amount={}", prepaymentId, payableId, actualSettle);
        return actualSettle;
    }

    /**
     * 用预收款冲应收账款.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal settleReceivable(Long prepaymentId, Long receivableId, BigDecimal settleAmount, String remark) {
        PrepaymentEntity prepay = prepaymentMapper.selectById(prepaymentId);
        if (prepay == null) throw new BusinessException("预收款不存在: " + prepaymentId);
        if (!"CONFIRMED".equals(prepay.getStatus()) && !"SETTLED".equals(prepay.getStatus())) {
            // CONFIRMED 或部分 SETTLED 都可继续冲销(部分结算)
        }
        if (prepay.getUnsettledAmount() == null || prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("预收款已全部冲销");

        ReceivableEntity recv = receivableMapper.selectById(receivableId);
        if (recv == null) throw new BusinessException("应收账款不存在: " + receivableId);
        if (recv.getUnsettledAmount() == null || recv.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("应收账款已全部结清");

        if (!prepay.getCustomerId().equals(recv.getCustomerId()))
            throw new BusinessException("预收款与应收账款客户不一致");

        BigDecimal actualSettle = settleAmount
                .min(prepay.getUnsettledAmount())
                .min(recv.getUnsettledAmount());
        if (actualSettle.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("冲销金额必须大于0");

        prepay.setSettledAmount(prepay.getSettledAmount().add(actualSettle));
        prepay.setUnsettledAmount(prepay.getAmount().subtract(prepay.getSettledAmount()));
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            prepay.setStatus("SETTLED");
            prepay.setUnsettledAmount(BigDecimal.ZERO);
        }
        prepaymentMapper.updateById(prepay);

        recv.setSettledAmount(recv.getSettledAmount().add(actualSettle));
        recv.setUnsettledAmount(recv.getAmount().subtract(recv.getSettledAmount()));
        if (recv.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            recv.setUnsettledAmount(BigDecimal.ZERO);
        }
        receivableMapper.updateById(recv);

        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setTenantId(1L);
        log.setSourceDocType("RECEIPT_PREPAY");
        log.setSourceDocId(prepaymentId);
        log.setTargetDocType("INVOICE_OUT");
        log.setTargetDocId(receivableId);
        log.setAllocatedAmount(actualSettle);
        log.setDiscountAmount(BigDecimal.ZERO);
        log.setMatchScore(new BigDecimal("100"));
        log.setMatchMethod("AUTO");
        log.setStatus("EXECUTED");
        log.setRemark(remark != null ? remark : "预收款冲应收账款");
        logMapper.insert(log);

        log.info("P12 预收款冲应收完成: prepayId={}, receivableId={}, amount={}", prepaymentId, receivableId, actualSettle);
        return actualSettle;
    }

    @Override
    public void reverseSettle(Long id) {
        PrepaymentEntity prepay = prepaymentMapper.selectById(id);
        if (prepay == null) throw new BusinessException("预付/预收记录不存在");
        if (!"SETTLED".equals(prepay.getStatus()) && !"CONFIRMED".equals(prepay.getStatus()))
            throw new BusinessException("只有已结算或确认状态可反冲");

        // 简单反冲: 恢复未结算金额
        prepay.setUnsettledAmount(prepay.getAmount());
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setStatus("CONFIRMED");
        prepaymentMapper.updateById(prepay);
        log.info("P12 预付/预收反冲销: prepayId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        PrepaymentEntity prepay = prepaymentMapper.selectById(id);
        if (prepay == null) throw new BusinessException("预付/预收不存在");
        if (!"DRAFT".equals(prepay.getStatus()))
            throw new BusinessException("只有草稿可确认, 当前: " + prepay.getStatus());
        prepay.setStatus("CONFIRMED");
        prepaymentMapper.updateById(prepay);
        log.info("P12 预付/预收确认: prepayId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PrepaymentEntity prepay = prepaymentMapper.selectById(id);
        if (prepay == null) throw new BusinessException("预付/预收不存在");
        if ("SETTLED".equals(prepay.getStatus()))
            throw new BusinessException("已结算的预付/预收不可取消, 请先反冲销");
        prepay.setStatus("CANCELLED");
        prepaymentMapper.updateById(prepay);
        log.info("P12 预付/预收取消: prepayId={}", id);
    }
}
