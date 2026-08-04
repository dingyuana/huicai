package com.huicai.base.balance.service;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.dto.SubjectBalanceVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 科目余额更新服务
 */
public interface SubjectBalanceService {

    void updateBalanceOnPost(VoucherEntity voucher, List<VoucherEntryEntity> entries);

    void initOpeningBalances(String period, Map<Long, BigDecimal> balances);

    List<SubjectBalanceEntity> queryByPeriod(String period);

    /**
     * 按期间查询科目余额，并注入科目编码/名称/方向（供列表展示）
     */
    List<SubjectBalanceVO> queryByPeriodWithSubject(String period);

    Map<String, Object> checkTrialBalance(String period);
}
