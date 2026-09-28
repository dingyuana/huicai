package com.huicai.base.report.service;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Map<String, Object>> subjectBalanceTable(String period);
    Map<String, Object> balanceSheet(String period);

    /**
     * 报表诊断（P97/REQ-100 + REQ-102，阶段 E）。
     *
     * <p>返回规则命中列表，每项含 ruleId / title / detail。纯只读：只调既有查询，不改任何数据，
     * 不自动修数（铁律 #1/#2）。首期规则：零收入但有费用、期末现金骤降超半数、期初余额不连续。
     */
    List<Map<String, Object>> diagnostics(String period);

    /**
     * 资产负债表（含重分类列报，P97/REQ-098）。
     * 与 {@link #balanceSheet} 的差别仅在列报：资产类科目的贷方余额重分类为负债。
     * 纯只读，不改账不出凭证（铁律 #1）。
     */
    Map<String, Object> balanceSheetWithReclassification(String period);

    /** 辅助核算明细（P97/REQ-097 阶段 C-2）：按 assist_json 值分组，schema 对上游透明 */
    List<Map<String, Object>> auxiliaryDetail(String period);
    Map<String, Object> incomeStatement(String period);
    Map<String, Object> cashFlowStatement(String period);
    List<Map<String, Object>> trend(String startPeriod, String endPeriod);

    void exportSubjectBalance(String period, HttpServletResponse response) throws IOException;
    void exportBalanceSheet(String period, HttpServletResponse response) throws IOException;
    void exportIncomeStatement(String period, HttpServletResponse response) throws IOException;
    void exportCashFlow(String period, HttpServletResponse response) throws IOException;
}
