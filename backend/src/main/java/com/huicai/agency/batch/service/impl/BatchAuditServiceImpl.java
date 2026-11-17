package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultAccumulator;
import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchAuditService;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAuditServiceImpl implements BatchAuditService {

    private final VoucherService voucherService;
    private final OutputInvoiceStateMachineService outputInvoiceStateMachineService;
    private final InputInvoiceStateMachineService inputInvoiceStateMachineService;
    private final AgencyBatchTransactionRunner transactionRunner;

    @Override
    public BatchResultVO auditVouchers(List<Long> voucherIds, Long enterpriseId, Long operatorId) {
        List<Long> ids = BatchRequestValidator.requireUniqueIds(voucherIds, "凭证 ID 列表");
        requireOperator(operatorId);
        requireEnterprise(enterpriseId);

        BatchResultAccumulator result = new BatchResultAccumulator();
        for (Long id : ids) {
            process(id, "凭证", result, () -> transactionRunner.run(enterpriseId, () -> {
                voucherService.audit(id, operatorId);
                return null;
            }));
        }
        return result.build();
    }

    @Override
    public BatchResultVO auditInvoices(List<Long> invoiceIds, Long enterpriseId,
                                       Long operatorId, String invoiceType) {
        List<Long> ids = BatchRequestValidator.requireUniqueIds(invoiceIds, "发票 ID 列表");
        String type = BatchRequestValidator.requireInvoiceType(invoiceType);
        requireOperator(operatorId);
        requireEnterprise(enterpriseId);

        BatchResultAccumulator result = new BatchResultAccumulator();
        for (Long id : ids) {
            process(id, "发票", result, () -> transactionRunner.run(enterpriseId, () -> {
                if ("input".equals(type)) {
                    inputInvoiceStateMachineService.confirm(id, operatorId);
                } else {
                    outputInvoiceStateMachineService.confirm(id, operatorId);
                }
                return null;
            }));
        }
        return result.build();
    }

    private void process(Long id, String label, BatchResultAccumulator result, Runnable action) {
        try {
            action.run();
            result.success(id, label + "审核成功");
        } catch (BusinessException e) {
            log.warn("批量{}审核失败 id={}: {}", label, id, e.getMessage());
            result.failure(id, e.getMessage());
        } catch (RuntimeException e) {
            log.error("批量{}审核发生系统异常 id={}", label, id, e);
            result.failure(id, "系统处理失败，请联系管理员查看服务日志");
        }
    }

    private static void requireEnterprise(Long enterpriseId) {
        if (enterpriseId == null || enterpriseId <= 0) {
            throw BusinessException.badRequest("目标企业 ID 无效");
        }
    }

    private static void requireOperator(Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw BusinessException.badRequest("操作人 ID 无效");
        }
    }
}
