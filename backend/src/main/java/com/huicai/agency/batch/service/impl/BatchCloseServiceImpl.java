package com.huicai.agency.batch.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchCloseService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class BatchCloseServiceImpl implements BatchCloseService {

    /**
     * D3：原实现对每个企业无条件回报「结账成功」但未调用任何结账逻辑。
     * 假结账会让人误以为账期已锁定（实际未锁），后续期间可被重复记账，
     * 比直接报错危险得多，故显式失败。
     */
    private static final String NOT_IMPLEMENTED =
            "批量结账功能尚未实现：该端点原会回报「结账成功」但未执行任何结账动作，"
                    + "已改为显式报错以避免假成功（REQ-2026-134 / P107 D3）";

    @Override
    public BatchResultVO closePeriods(List<Long> enterpriseIds, String period) {
        throw new BusinessException(501, NOT_IMPLEMENTED);
    }
}
