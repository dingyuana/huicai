package com.huicai.sme.arap.service;

import com.huicai.sme.arap.mapper.BusinessDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
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