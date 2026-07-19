package com.huicai.base.report.service;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Map<String, Object>> subjectBalanceTable(String period);
    Map<String, Object> balanceSheet(String period);
    Map<String, Object> incomeStatement(String period);
    Map<String, Object> cashFlowStatement(String period);
    List<Map<String, Object>> trend(String startPeriod, String endPeriod);

    void exportSubjectBalance(String period, HttpServletResponse response) throws IOException;
    void exportBalanceSheet(String period, HttpServletResponse response) throws IOException;
    void exportIncomeStatement(String period, HttpServletResponse response) throws IOException;
    void exportCashFlow(String period, HttpServletResponse response) throws IOException;
}
