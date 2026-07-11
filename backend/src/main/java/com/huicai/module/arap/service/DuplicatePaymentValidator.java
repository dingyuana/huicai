package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 重复付款拦截校验（P53 M4）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicatePaymentValidator {

    private final BusinessDocMapper businessDocMapper;

    /**
     * 校验同一发票+供应商是否已有重复付款
     *
     * @param invoiceNo 发票号
     * @param vendorId  供应商ID
     * @param strict    严格模式（已核销则抛异常）/ 宽松模式（未核销仅告警）
     * @throws BusinessException 重复付款时抛出
     */
    public void validate(String invoiceNo, Long vendorId, boolean strict) {
        if (invoiceNo == null || invoiceNo.isBlank()) return; // 无发票号跳过校验

        List<BusinessDocEntity> existing = businessDocMapper.selectList(
            new LambdaQueryWrapper<BusinessDocEntity>()
                .eq(BusinessDocEntity::getInvoiceNo, invoiceNo)
                .eq(BusinessDocEntity::getSupplierId, vendorId)
                .eq(BusinessDocEntity::getDocType, "PAYMENT")
                .eq(BusinessDocEntity::getDeleted, 0)
        );

        if (existing.isEmpty()) return;

        // 检查是否有已核销的付款
        boolean hasSettled = existing.stream()
            .anyMatch(doc -> doc.getSettledAmount() != null
                && doc.getSettledAmount().compareTo(BigDecimal.ZERO) > 0);

        if (hasSettled) {
            throw new BusinessException("发票号 " + invoiceNo
                + " 已有已核销的付款记录，请确认是否重复付款");
        }

        if (strict) {
            throw new BusinessException("发票号 " + invoiceNo
                + " 已有未核销的付款记录，请确认");
        }

        log.warn("发票号 {} 存在未核销付款记录，已跳过严格校验", invoiceNo);
    }
}