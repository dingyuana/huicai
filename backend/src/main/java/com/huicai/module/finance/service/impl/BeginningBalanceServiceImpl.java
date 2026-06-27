package com.huicai.module.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.SubjectBalanceEntity;
import com.huicai.module.finance.mapper.SubjectBalanceMapper;
import com.huicai.module.finance.service.BeginningBalanceService;
import com.huicai.module.finance.service.SubjectBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 期初建账服务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeginningBalanceServiceImpl implements BeginningBalanceService {

    private final SubjectBalanceService subjectBalanceService;
    private final SubjectBalanceMapper subjectBalanceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchInput(String period, Map<Long, BigDecimal> balances) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        if (balances == null || balances.isEmpty()) {
            throw BusinessException.badRequest("期初余额数据不能为空");
        }

        // 委托 SubjectBalanceService 完成核心录入 + 试算平衡校验
        subjectBalanceService.initOpeningBalances(period, balances);
        log.info("期初建账批量录入完成: period={}, 科目数={}", period, balances.size());
    }

    @Override
    public List<SubjectBalanceEntity> listByPeriod(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        return subjectBalanceService.queryByPeriod(period);
    }

    @Override
    public Map<String, Object> checkTrialBalance(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        return subjectBalanceService.checkTrialBalance(period);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearByPeriod(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }

        // 检查是否已有业务发生（借方或贷方发生额不为零）
        List<SubjectBalanceEntity> balances = subjectBalanceService.queryByPeriod(period);
        for (SubjectBalanceEntity balance : balances) {
            if (balance.getDebitTotal().compareTo(BigDecimal.ZERO) != 0
                    || balance.getCreditTotal().compareTo(BigDecimal.ZERO) != 0) {
                throw BusinessException.badRequest(
                        "期间 " + period + " 已有业务发生, 不可清空期初余额");
            }
        }

        // 批量删除
        LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectBalanceEntity::getPeriod, period);
        subjectBalanceMapper.delete(wrapper);
        log.info("清空期初余额: period={}", period);
    }
}