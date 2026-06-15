package com.huicai.module.report.service.impl;

import com.huicai.module.report.mapper.ReportDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportDataMapper reportDataMapper;
    @InjectMocks private ReportServiceImpl service;

    @Test
    void subjectBalanceTable_returns_list() {
        List<Map<String, Object>> mock = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("code", "1002"); row.put("name", "银行存款");
        row.put("debit", 1000.0); row.put("credit", 0.0);
        mock.add(row);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(mock);

        List<Map<String, Object>> r = service.subjectBalanceTable("202606");
        assertEquals(1, r.size());
        assertEquals("1002", r.get(0).get("code"));
    }

    @Test
    void balanceSheet_returns_period_and_data() {
        Map<String, Object> agg = new HashMap<>();
        agg.put("totalAssets", 5000.0);
        agg.put("totalLiabilities", 3000.0);
        agg.put("totalEquity", 2000.0);
        when(reportDataMapper.balanceSheetAggregate("202606")).thenReturn(agg);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheet("202606");
        assertNotNull(r);
        assertEquals("202606", r.get("period"));
    }

    @Test
    void incomeStatement_returns_period_map() {
        Map<String, Object> periodData = new HashMap<>();
        periodData.put("revenue", 10000.0);
        periodData.put("profit", 3000.0);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(periodData);
        when(reportDataMapper.cumulativeData("202601", "202606"))
                .thenReturn(new HashMap<>());

        Map<String, Object> r = service.incomeStatement("202606");
        assertNotNull(r);
        assertEquals("202606", r.get("period"));
    }

    @Test
    void cashFlowStatement_returns_period_map() {
        when(reportDataMapper.cashFlowData("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.cashFlowStatement("202606");
        assertNotNull(r);
        assertEquals("202606", r.get("period"));
    }

    @Test
    void trend_returns_list_between_periods() {
        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, Object> p1 = new HashMap<>();
        p1.put("period", "202601"); p1.put("revenue", 10000.0); p1.put("profit", 2000.0);
        trend.add(p1);
        Map<String, Object> p2 = new HashMap<>();
        p2.put("period", "202602"); p2.put("revenue", 12000.0); p2.put("profit", 3000.0);
        trend.add(p2);
        when(reportDataMapper.trendData("202601", "202602")).thenReturn(trend);

        List<Map<String, Object>> r = service.trend("202601", "202602");
        assertEquals(2, r.size());
        assertEquals("202601", r.get(0).get("period"));
    }
}
