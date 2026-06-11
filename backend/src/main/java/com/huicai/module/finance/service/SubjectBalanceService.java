package com.huicai.module.finance.service;

import com.huicai.module.finance.entity.SubjectBalanceEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;

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

    Map<String, Object> checkTrialBalance(String period);
}
