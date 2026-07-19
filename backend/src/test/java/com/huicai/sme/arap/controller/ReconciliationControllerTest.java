package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.arap.entity.ReconciliationLogEntity;
import com.huicai.sme.arap.service.ReconciliationService;
import com.huicai.sme.arap.service.impl.ReconciliationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 核销 Controller 层测试 (P0).
 *
 * <p>测试目标：验证 HTTP 路由、参数绑定、请求/响应序列化、
 * Service 调用链完整性。
 *
 * <p>策略：@SpringBootTest + MockMvc + @MockBean
 * - 真实 Controller
 * - Mock Service 层
 * - 验证 HTTP 层面的正确性
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ReconciliationServiceImpl reconciliationService;

    // ==================== 推荐接口测试 ====================

    @Test
    @DisplayName("收款核销推荐_参数正确绑定_Service被调用")
    void recommendReceipt_paramsBound_serviceCalled() throws Exception {
        // given
        when(reconciliationService.recommendReceipt(anyLong(), anyString(), anyLong(), any(), any(), any()))
                .thenReturn(null);

        // when & then
        mvc.perform(post("/api/sme/arap/v1/reconciliation/receipt/1/recommend")
                        .param("sourceDocType", "RECEIPT")
                        .param("customerId", "10")
                        .param("amount", "500.00")
                        .param("summary", "客户回款")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reconciliationService).recommendReceipt(eq(1L), eq("RECEIPT"), eq(10L), eq(new BigDecimal("500.00")), eq("客户回款"), isNull());
    }

    @Test
    @DisplayName("付款核销推荐_BigDecimal参数正确解析")
    void recommendPayment_decimalParamParsedCorrectly() throws Exception {
        // given
        when(reconciliationService.recommendPayment(anyLong(), anyString(), anyLong(), any(), any(), any()))
                .thenReturn(null);

        mvc.perform(post("/api/sme/arap/v1/reconciliation/payment/1/recommend")
                        .param("sourceDocType", "PAYMENT")
                        .param("vendorId", "20")
                        .param("amount", "1234.56")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(reconciliationService).recommendPayment(eq(1L), eq("PAYMENT"), eq(20L), eq(new BigDecimal("1234.56")), isNull(), isNull());
    }

    // 注意：缺少参数返回 400 是 Spring MVC 框架层面的行为
    // 此处专注验证参数正确传递时 Service 被正确调用

    // ==================== 执行核销接口测试 ====================

    @Test
    @DisplayName("执行核销_RequestBody正确解析_Service被调用")
    void execute_requestBodyParsed_serviceCalled() throws Exception {
        // given
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(100L);
        log.setStatus("EXECUTED");
        when(reconciliationService.execute(any())).thenReturn(log);

        ReconciliationService.ExecuteRequest request = new ReconciliationService.ExecuteRequest(
                "bank_txn", 1L, "INVOICE_OUT", 10L, new BigDecimal("500.00"),
                null, null, null, null, null, null
        );

        // when & then
        mvc.perform(post("/api/sme/arap/v1/reconciliation/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));

        verify(reconciliationService).execute(argThat(r ->
                "bank_txn".equals(r.sourceDocType()) &&
                r.sourceDocId() == 1L &&
                "INVOICE_OUT".equals(r.targetDocType()) &&
                r.targetDocId() == 10L &&
                r.amount().compareTo(new BigDecimal("500.00")) == 0
        ));
    }

    @Test
    @DisplayName("带差额调整的核销_请求参数组合正确")
    void executeWithAdjustment_paramCombinationCorrect() throws Exception {
        // given
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(100L);
        when(reconciliationService.executeWithAdjustment(any(), any(), any(), anyLong())).thenReturn(log);

        ReconciliationService.ExecuteRequest request = new ReconciliationService.ExecuteRequest(
                "receipt", 1L, "INVOICE_OUT", 10L, new BigDecimal("490.00"),
                null, null, null, null, null, null
        );

        // when & then - body + query params 组合
        mvc.perform(post("/api/sme/arap/v1/reconciliation/execute-with-adjustment")
                        .param("adjustAmount", "10.00")
                        .param("adjustType", "FEE")
                        .param("adjustSubjectId", "123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(reconciliationService).executeWithAdjustment(
                any(),
                eq(new BigDecimal("10.00")),
                eq("FEE"),
                eq(123L)
        );
    }

    // ==================== 状态转换接口测试 ====================

    @Test
    @DisplayName("反核销_PathVariable+RequestParam正确绑定")
    void reverse_pathVariableAndRequestParam_boundCorrectly() throws Exception {
        // given
        doNothing().when(reconciliationService).reverse(anyLong(), anyString());

        // when & then
        mvc.perform(post("/api/sme/arap/v1/reconciliation/123/reverse")
                        .param("reason", "操作失误")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(reconciliationService).reverse(eq(123L), eq("操作失误"));
    }

    @Test
    @DisplayName("审批核销_PathVariable正确解析")
    void approve_pathVariable_boundCorrectly() throws Exception {
        // given
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(999L);
        when(reconciliationService.approve(anyLong())).thenReturn(log);

        // when & then
        mvc.perform(post("/api/sme/arap/v1/reconciliation/999/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(999));

        verify(reconciliationService).approve(eq(999L));
    }

    @Test
    @DisplayName("驳回核销_可选reason参数正确处理")
    void reject_optionalReason_handledCorrectly() throws Exception {
        // given
        doNothing().when(reconciliationService).reject(anyLong(), any());

        // when & then - 不传 reason
        mvc.perform(post("/api/sme/arap/v1/reconciliation/999/reject"))
                .andExpect(status().isOk());

        verify(reconciliationService).reject(eq(999L), isNull());
    }

    // ==================== 查询接口测试 ====================

    @Test
    @DisplayName("分页查询核销日志_默认参数正确生效")
    void pageLogs_defaultParams_applied() throws Exception {
        // given
        IPage<ReconciliationLogEntity> page = new Page<>(1, 20);
        when(reconciliationService.pageLogs(isNull(), eq(1), eq(20))).thenReturn(page);

        // when & then - 不传 current 和 size，使用默认值
        mvc.perform(get("/api/sme/arap/v1/reconciliation/logs/page"))
                .andExpect(status().isOk());

        verify(reconciliationService).pageLogs(isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("分页查询核销日志_自定义参数正确绑定")
    void pageLogs_customParams_boundCorrectly() throws Exception {
        // given
        IPage<ReconciliationLogEntity> page = new Page<>(2, 50);
        when(reconciliationService.pageLogs(eq("receipt"), eq(2), eq(50))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/sme/arap/v1/reconciliation/logs/page")
                        .param("sourceDocType", "receipt")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(reconciliationService).pageLogs(eq("receipt"), eq(2), eq(50));
    }

    @Test
    @DisplayName("查询核销记录_必选参数正确绑定")
    void getRecords_requiredParams_boundCorrectly() throws Exception {
        // given
        when(reconciliationService.getRecords(anyString(), anyLong())).thenReturn(List.of());

        // when & then
        mvc.perform(get("/api/sme/arap/v1/reconciliation/records")
                        .param("sourceDocType", "bank_txn")
                        .param("sourceDocId", "1"))
                .andExpect(status().isOk());

        verify(reconciliationService).getRecords(eq("bank_txn"), eq(1L));
    }

        // 参数正确时 Service 被调用，框架层面的验证省略

    // ==================== 批量操作测试 ====================

    @Test
    @DisplayName("批量核销_List参数正确解析")
    void batchExecute_listParsed_serviceCalled() throws Exception {
        // given
        when(reconciliationService.batchExecute(anyList())).thenReturn(List.of());

        List<ReconciliationService.ExecuteRequest> requests = List.of(
                createRequest("receipt", 1L, "INVOICE_OUT", 10L, "100.00"),
                createRequest("receipt", 2L, "INVOICE_OUT", 20L, "200.00")
        );

        // when & then
        mvc.perform(post("/api/sme/arap/v1/reconciliation/batch-execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(reconciliationService).batchExecute(argThat(list -> list.size() == 2));
    }

    private ReconciliationService.ExecuteRequest createRequest(
            String sourceType, Long sourceId, String targetType, Long targetId, String amount) {
        return new ReconciliationService.ExecuteRequest(
                sourceType, sourceId, targetType, targetId, new BigDecimal(amount),
                null, null, null, null, null, null
        );
    }
}
