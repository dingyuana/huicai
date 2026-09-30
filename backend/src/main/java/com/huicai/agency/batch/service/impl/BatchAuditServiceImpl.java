package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchAuditService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAuditServiceImpl implements BatchAuditService {

    /**
     * D3：本服务尚未接入状态机，原实现对每个 id 无条件回报「审核成功」而数据库毫无变化。
     * 假成功比报错危险得多（用户据此认为已审核，铁律 #1「人是唯一审核主体」被架空），
     * 故在真正实现前显式失败，不返回任何暗示成功的批量结果。
     */
    private static final String NOT_IMPLEMENTED =
            "批量审核功能尚未实现：该端点原会回报「审核成功」但未执行任何审核动作，"
                    + "已改为显式报错以避免假成功（REQ-2026-134 / P107 D3）";

    @Override
    public BatchResultVO auditVouchers(List<Long> voucherIds, Long enterpriseId) {
        throw new BusinessException(501, NOT_IMPLEMENTED);
    }

    @Override
    public BatchResultVO auditInvoices(List<Long> invoiceIds, Long enterpriseId) {
        throw new BusinessException(501, NOT_IMPLEMENTED);
    }
}
