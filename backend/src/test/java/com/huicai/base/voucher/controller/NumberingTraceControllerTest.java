package com.huicai.base.voucher.controller;

import com.huicai.common.test.FastTest;
import com.huicai.base.voucher.dto.NumberingTraceVO;
import com.huicai.base.voucher.service.NumberingTraceService;
import com.huicai.base.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 编号关联体系 - 追溯接口测试 (L1 / FastTest)
 *
 * 验证 GET /api/base/voucher/v1/vouchers/trace?no={编号} 接口的 MockMvc 测试。
 * 使用 @SpringBootTest + @AutoConfigureMockMvc(addFilters = false) 
 * 避免 JWT 过滤器导致的 ApplicationContext 加载失败。
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("编号关联 - 追溯接口测试")
public class NumberingTraceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VoucherService voucherService;

    @MockBean
    private NumberingTraceService numberingTraceService;

    private NumberingTraceVO mockTrace;

    @BeforeEach
    void setup() {
        mockTrace = new NumberingTraceVO();
        mockTrace.setTraceType("OUTPUT_INVOICE");
        mockTrace.setTraceNo("9999.E2E.SALE.INV.001");

        NumberingTraceVO.TraceNode upstream = new NumberingTraceVO.TraceNode();
        upstream.setNodeType("INVOICE");
        upstream.setNodeNo("9999.E2E.SALE.INV.001");
        upstream.setSummary("销项发票");
        mockTrace.setUpstream(Collections.singletonList(upstream));

        NumberingTraceVO.TraceNode downstream = new NumberingTraceVO.TraceNode();
        downstream.setNodeType("RECEIVABLE");
        downstream.setNodeNo("9999.E2E.SALE.REC.001");
        downstream.setSummary("应收单");
        mockTrace.setDownstream(Collections.singletonList(downstream));
    }

    @Test
    @DisplayName("成功: 按发票号查追溯链路")
    void trace_success_by_invoiceNo() throws Exception {
        when(numberingTraceService.traceByNumber("9999.E2E.SALE.INV.001"))
            .thenReturn(mockTrace);

        mvc.perform(get("/api/base/voucher/v1/vouchers/trace")
                .param("no", "9999.E2E.SALE.INV.001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.traceType").value("OUTPUT_INVOICE"))
            .andExpect(jsonPath("$.data.traceNo").value("9999.E2E.SALE.INV.001"))
            .andExpect(jsonPath("$.data.upstream[0].nodeNo").value("9999.E2E.SALE.INV.001"))
            .andExpect(jsonPath("$.data.downstream[0].nodeNo").value("9999.E2E.SALE.REC.001"));
    }

    @Test
    @DisplayName("成功: 按凭证号查追溯链路")
    void trace_success_by_voucherNo() throws Exception {
        NumberingTraceVO voucherTrace = new NumberingTraceVO();
        voucherTrace.setTraceType("VOUCHER");
        voucherTrace.setTraceNo("9999.E2E.SALE.VCH.001");
        voucherTrace.setUpstream(Collections.emptyList());

        NumberingTraceVO.TraceNode dn = new NumberingTraceVO.TraceNode();
        dn.setNodeType("RECEIVABLE");
        dn.setNodeNo("9999.E2E.SALE.REC.001");
        dn.setSummary("应收单");
        voucherTrace.setDownstream(Collections.singletonList(dn));

        when(numberingTraceService.traceByNumber("9999.E2E.SALE.VCH.001"))
            .thenReturn(voucherTrace);

        mvc.perform(get("/api/base/voucher/v1/vouchers/trace")
                .param("no", "9999.E2E.SALE.VCH.001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.traceType").value("VOUCHER"));
    }

    @Test
    @DisplayName("成功: 按业务单号查追溯链路")
    void trace_success_by_businessDocNo() throws Exception {
        NumberingTraceVO docTrace = new NumberingTraceVO();
        docTrace.setTraceType("BUSINESS_DOC");
        docTrace.setTraceNo("9999.E2E.DOC.001");
        docTrace.setUpstream(Collections.emptyList());

        NumberingTraceVO.TraceNode dn = new NumberingTraceVO.TraceNode();
        dn.setNodeType("VOUCHER");
        dn.setNodeNo("9999.E2E.VCH.001");
        dn.setSummary("凭证");
        docTrace.setDownstream(Collections.singletonList(dn));

        when(numberingTraceService.traceByNumber("9999.E2E.DOC.001"))
            .thenReturn(docTrace);

        mvc.perform(get("/api/base/voucher/v1/vouchers/trace")
                .param("no", "9999.E2E.DOC.001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.traceType").value("BUSINESS_DOC"));
    }

    @Test
    @DisplayName("成功: 按核销单号查追溯链路")
    void trace_success_by_settlementNo() throws Exception {
        NumberingTraceVO settleTrace = new NumberingTraceVO();
        settleTrace.setTraceType("SETTLEMENT");
        settleTrace.setTraceNo("9999.E2E.SETTLE.001");

        NumberingTraceVO.TraceNode up = new NumberingTraceVO.TraceNode();
        up.setNodeType("RECEIVABLE");
        up.setNodeNo("9999.E2E.REC.001");
        up.setSummary("应收单");
        settleTrace.setUpstream(Collections.singletonList(up));

        NumberingTraceVO.TraceNode dn = new NumberingTraceVO.TraceNode();
        dn.setNodeType("VOUCHER");
        dn.setNodeNo("9999.E2E.VCH.001");
        dn.setSummary("凭证");
        settleTrace.setDownstream(Collections.singletonList(dn));

        when(numberingTraceService.traceByNumber("9999.E2E.SETTLE.001"))
            .thenReturn(settleTrace);

        mvc.perform(get("/api/base/voucher/v1/vouchers/trace")
                .param("no", "9999.E2E.SETTLE.001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.traceType").value("SETTLEMENT"))
            .andExpect(jsonPath("$.data.upstream[0].nodeNo").value("9999.E2E.REC.001"))
            .andExpect(jsonPath("$.data.downstream[0].nodeNo").value("9999.E2E.VCH.001"));
    }

    @Test
    @DisplayName("成功: 无效编号返回 UNKNOWN")
    void trace_invalid_no_returns_unknown() throws Exception {
        NumberingTraceVO unknownTrace = new NumberingTraceVO();
        unknownTrace.setTraceType("UNKNOWN");
        unknownTrace.setTraceNo("NONEXISTENT");
        unknownTrace.setUpstream(Collections.emptyList());
        unknownTrace.setDownstream(Collections.emptyList());

        when(numberingTraceService.traceByNumber("NONEXISTENT"))
            .thenReturn(unknownTrace);

        mvc.perform(get("/api/base/voucher/v1/vouchers/trace")
                .param("no", "NONEXISTENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.traceType").value("UNKNOWN"));
    }

    @Test
    @DisplayName("失败: 缺少 no 参数返回 500")
    void trace_missing_no_param_returns_500() throws Exception {
        mvc.perform(get("/api/base/voucher/v1/vouchers/trace"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("失败: no 参数为空返回 200（空字符串视为合法查询）")
    void trace_empty_no_param_returns_200() throws Exception {
        mvc.perform(get("/api/base/voucher/v1/vouchers/trace")
                .param("no", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
