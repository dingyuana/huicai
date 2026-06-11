package com.huicai.module.finance.service;

import java.util.List;
import java.util.Map;

public interface BankReconciliationService {
    /**
     * 余额调节表
     * 银行对账单余额 - 企业日记账余额 = 未达账项合计
     */
    Map<String, Object> generateAdjustment(Long accountId, String period);
    /**
     * 对账汇总: 已对账/未对账/企业已记银行未记/银行已记企业未记
     */
    Map<String, Object> summarize(Long accountId, String period);
    /**
     * 未达账项列表
     */
    List<Map<String, Object>> unmatchedItems(Long accountId, String period);
}
