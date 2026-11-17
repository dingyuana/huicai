package com.huicai.agency.batch.service.impl;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.transaction.RequiresNewRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class AgencyBatchTransactionRunner {

    private final RequiresNewRunner requiresNewRunner;

    public <T> T run(Long enterpriseId, Supplier<T> action) {
        if (enterpriseId == null || enterpriseId <= 0) {
            throw BusinessException.badRequest("目标企业 ID 无效");
        }

        Long previousEnterpriseId = EnterpriseContextHolder.get();
        try {
            EnterpriseContextHolder.set(enterpriseId);
            return requiresNewRunner.run(action);
        } finally {
            if (previousEnterpriseId == null) {
                EnterpriseContextHolder.clear();
            } else {
                EnterpriseContextHolder.set(previousEnterpriseId);
            }
        }
    }
}
