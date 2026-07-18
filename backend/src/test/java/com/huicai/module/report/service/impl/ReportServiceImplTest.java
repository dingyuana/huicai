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
    void balanceSheet_groups_3xxx_as_equity() {
        // 模拟科目数据: 1xxx=资产, 2xxx=负债, 3xxx=权益, 4xxx=权益
        List<Map<String, Object>> balances = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>(); a1.put("code", "1002"); a1.put("name", "银行存款"); a1.put("end_balance", 5000.0); a1.put("direction", "debit"); balances.add(a1);
        Map<String, Object> l1 = new HashMap<>(); l1.put("code", "2001"); l1.put("name", "短期借款"); l1.put("end_balance", 3000.0); l1.put("direction", "credit"); balances.add(l1);
        Map<String, Object> e1 = new HashMap<>(); e1.put("code", "3001"); e1.put("name", "实收资本"); e1.put("end_balance", 1000.0); e1.put("direction", "credit"); balances.add(e1);
        Map<String, Object> e2 = new HashMap<>(); e2.put("code", "4001"); e2.put("name", "资本公积"); e2.put("end_balance", 500.0); e2.put("direction", "credit"); balances.add(e2);
        when(reportDataMapper.balanceSheetAggregate("202606")).thenReturn(new HashMap<>());
        when(reportDataMapper.subjectBalance("202606")).thenReturn(balances);

        Map<String, Object> r = service.balanceSheet("202606");
        List<Map<String, Object>> assets = (List<Map<String, Object>>) r.get("assets");
        List<Map<String, Object>> liab = (List<Map<String, Object>>) r.get("liabilities");
        List<Map<String, Object>> equity = (List<Map<String, Object>>) r.get("equity");

        assertEquals(1, assets.size(), "1xxx 应归为资产");
        assertEquals("1002", assets.get(0).get("code"));
        assertEquals(1, liab.size(), "2xxx 应归为负债");
        assertEquals("2001", liab.get(0).get("code"));
        assertEquals(2, equity.size(), "3xxx/4xxx 应归为权益");
        assertEquals("3001", equity.get(0).get("code"));
        assertEquals("4001", equity.get(1).get("code"));
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
