package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchAuditService;
import com.huicai.common.context.EnterpriseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAuditServiceImpl implements BatchAuditService {

    @Override
    public BatchResultVO auditVouchers(List<Long> voucherIds, Long enterpriseId) {
        return batchAudit(voucherIds, enterpriseId, "凭证");
    }

    @Override
    public BatchResultVO auditInvoices(List<Long> invoiceIds, Long enterpriseId) {
        return batchAudit(invoiceIds, enterpriseId, "发票");
    }

    private BatchResultVO batchAudit(List<Long> ids, Long enterpriseId, String type) {
        BatchResultVO result = new BatchResultVO();
        result.setTotal(ids.size());
        List<BatchResultVO.BatchItemResult> details = new ArrayList<>();

        EnterpriseContextHolder.set(enterpriseId);
        try {
            for (Long id : ids) {
                BatchResultVO.BatchItemResult item = new BatchResultVO.BatchItemResult();
                item.setId(id);
                try {
                    log.info("BatchAudit: auditing {} id={} for enterprise {}", type, id, enterpriseId);
                    // TODO: 调用对应状态机 confirm 方法
                    item.setSuccess(true);
                    item.setMessage("审核成功");
                } catch (Exception e) {
                    item.setSuccess(false);
                    item.setMessage("审核失败: " + e.getMessage());
                }
                details.add(item);
            }
        } finally {
            EnterpriseContextHolder.clear();
        }

        long success = details.stream().filter(BatchResultVO.BatchItemResult::isSuccess).count();
        result.setSuccess((int) success);
        result.setFailed(details.size() - (int) success);
        result.setDetails(details);
        return result;
    }
}
