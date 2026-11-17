package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultAccumulator;
import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchCloseService;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCloseServiceImpl implements BatchCloseService {

    private final PeriodCloseService periodCloseService;
    private final AgencyBatchTransactionRunner transactionRunner;

    @Override
    public BatchResultVO closePeriods(List<Long> enterpriseIds, String period, Long operatorId) {
        List<Long> ids = BatchRequestValidator.requireUniqueIds(enterpriseIds, "企业 ID 列表");
        String validPeriod = BatchRequestValidator.requirePeriod(period);
        if (operatorId == null || operatorId <= 0) {
            throw BusinessException.badRequest("操作人 ID 无效");
        }

        BatchResultAccumulator result = new BatchResultAccumulator();
        for (Long enterpriseId : ids) {
            try {
                transactionRunner.run(enterpriseId, () -> {
                    periodCloseService.closePeriod(validPeriod, operatorId);
                    return null;
                });
                result.success(enterpriseId, "期间 " + validPeriod + " 结账成功");
            } catch (BusinessException e) {
                log.warn("代理批量结账失败 enterpriseId={} period={}: {}",
                        enterpriseId, validPeriod, e.getMessage());
                result.failure(enterpriseId, e.getMessage());
            } catch (RuntimeException e) {
                log.error("代理批量结账发生系统异常 enterpriseId={} period={}",
                        enterpriseId, validPeriod, e);
                result.failure(enterpriseId, "系统处理失败，请联系管理员查看服务日志");
            }
        }
        return result.build();
    }
}
