package com.huicai.module.report.service;

import java.util.List;
import java.util.Map;

public interface ReportService {
    /**
     * 科目余额表
     */
    List<Map<String, Object>> subjectBalanceTable(String period);

    /**
     * 资产负债表
     */
    Map<String, Object> balanceSheet(String period);

    /**
     * 利润表
     */
    Map<String, Object> incomeStatement(String period);

    /**
     * 现金流量表
     */
    Map<String, Object> cashFlowStatement(String period);

    /**
     * 趋势数据
     */
    List<Map<String, Object>> trend(String startPeriod, String endPeriod);
}
