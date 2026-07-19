package com.huicai.sme.arap.service;

import com.huicai.sme.arap.dto.ReconciliationToleranceDTO;
import com.huicai.sme.arap.dto.vo.ReconciliationToleranceVO;
import com.huicai.sme.arap.entity.ReconciliationToleranceEntity;

import java.math.BigDecimal;

public interface ReconciliationToleranceService {

    ReconciliationToleranceEntity getTolerance(Long partyId, String partyType);

    BigDecimal getToleranceAmount(Long partyId, String partyType);

    BigDecimal getToleranceRate(Long partyId, String partyType);

    ReconciliationToleranceVO getDefaultConfig();

    ReconciliationToleranceVO getByParty(Long partyId, String partyType);

    ReconciliationToleranceVO create(ReconciliationToleranceDTO dto);

    ReconciliationToleranceVO update(Long id, ReconciliationToleranceDTO dto);

    void delete(Long id);
}
