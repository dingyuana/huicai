package com.huicai.module.finance.service;

import java.util.List;
import java.util.Map;

/**
 * 账簿查询服务
 */
public interface LedgerService {

    /**
     * 科目余额表：按期间列出所有末级科目的期初/借方/贷方/期末余额
     */
    List<Map<String, Object>> subjectBalance(String period);

    /**
     * 总分类账：按科目+期间，展示该科目的逐笔发生及余额
     */
    List<Map<String, Object>> generalLedger(Long subjectId, String period);

    /**
     * 明细账：按科目+期间+辅助项，展示逐笔分录明细
     */
    List<Map<String, Object>> subsidiaryLedger(Long subjectId, String period);
}
