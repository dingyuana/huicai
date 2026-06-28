package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;

import java.util.List;

public interface ArapSettlementService {
    IPage<ArapSettlementEntity> pageQuery(String status, String voucherNo, Integer current, Integer size);
    ArapSettlementEntity getById(Long id);
    ArapSettlementEntity create(ArapSettlementEntity entity, List<ArapSettlementEntryEntity> entries);
    ArapSettlementEntity confirm(Long id);
    void delete(Long id);

    /**
     * 核销单生成凭证 — 状态从 CONFIRMED → VOUCHERED
     */
    void generateVoucher(Long id);

    /**
     * 反核销 — 状态从 CONFIRMED → REVERSED，恢复应收/应付未结金额
     */
    void reverse(Long id);
}
