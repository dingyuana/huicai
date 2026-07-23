package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchCloseService;
import com.huicai.common.context.EnterpriseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCloseServiceImpl implements BatchCloseService {

    @Override
    public BatchResultVO closePeriods(List<Long> enterpriseIds, String period) {
        BatchResultVO result = new BatchResultVO();
        result.setTotal(enterpriseIds.size());
        List<BatchResultVO.BatchItemResult> details = new ArrayList<>();

        for (Long enterpriseId : enterpriseIds) {
            BatchResultVO.BatchItemResult item = new BatchResultVO.BatchItemResult();
            item.setId(enterpriseId);
            EnterpriseContextHolder.set(enterpriseId);
            try {
                log.info("BatchClose: closing period {} for enterprise {}", period, enterpriseId);
                // TODO: 调用 PeriodCloseService.closePeriod
                item.setSuccess(true);
                item.setMessage("结账成功");
            } catch (Exception e) {
                item.setSuccess(false);
                item.setMessage("结账失败: " + e.getMessage());
            } finally {
                EnterpriseContextHolder.clear();
            }
            details.add(item);
        }

        long success = details.stream().filter(BatchResultVO.BatchItemResult::isSuccess).count();
        result.setSuccess((int) success);
        result.setFailed(details.size() - (int) success);
        result.setDetails(details);
        return result;
    }
}
