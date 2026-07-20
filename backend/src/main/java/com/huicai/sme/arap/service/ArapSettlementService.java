package com.huicai.sme.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.dto.vo.ArapSettlementVO;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;

import java.util.List;

public interface ArapSettlementService {

    IPage<ArapSettlementVO> pageQueryWithPartyName(String status, String voucherNo, Integer current, Integer size);
    IPage<ArapSettlementEntity> pageQuery(String status, String voucherNo, Integer current, Integer size);
    ArapSettlementEntity getById(Long id);
    ArapSettlementEntity create(ArapSettlementEntity entity, List<ArapSettlementEntryEntity> entries);
    /** 提交核销单 — DRAFT → SUBMITTED */
    void submit(Long id);
    /** 审批通过 — SUBMITTED → CONFIRMED */
    ArapSettlementEntity approve(Long id);
    /** 驳回 — SUBMITTED → REJECTED（退回 DRAFT） */
    void reject(Long id, String reason);
    ArapSettlementEntity confirm(Long id);
    void delete(Long id);

        void cancel(Long id);

    void reject(Long id);

    List<ArapSettlementEntryEntity> getEntries(Long settlementId);

    /**
     * 核销单生成凭证 — 状态从 CONFIRMED → VOUCHERED
     */
    ArapSettlementVO getDetailWithPartyName(Long id);

    void generateVoucher(Long id);

    /**
     * 反核销 — 状态从 CONFIRMED → REVERSED，恢复应收/应付未结金额
     */
    void reverse(Long id);
}
