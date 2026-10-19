package com.huicai.sme.arap.service;

import com.huicai.base.business.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
// 🔴 类级事务（RLS 守卫 RlsTransactionStructureTest）：账龄分析读 t_business_doc（RLS 租户表）。
//   无事务 ⇒ GUC 恒 NULL ⇒ 谓词取 NULL ⇒ 应收/应付账龄恒为空。
@Transactional(rollbackFor = Exception.class)
public class BusinessDocAgingService {

    private final BusinessDocMapper businessDocMapper;

    public List<Map<String, Object>> getReceivableAging(Long customerId) {
        if (customerId == null) return Collections.emptyList();
        return businessDocMapper.agingByCustomer(customerId);
    }

    public List<Map<String, Object>> getPayableAging(Long vendorId) {
        if (vendorId == null) return Collections.emptyList();
        return businessDocMapper.agingByVendor(vendorId);
    }
}