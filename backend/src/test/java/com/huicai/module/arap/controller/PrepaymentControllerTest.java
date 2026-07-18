package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.arap.entity.PrepaymentEntity;
import com.huicai.module.arap.service.PrepaymentService;
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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PrepaymentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private PrepaymentService prepaymentService;

    @Test
    @DisplayName("分页查询预付款_默认参数正确生效")
    void pageQuery_defaultParams_applied() throws Exception {
        IPage<PrepaymentEntity> page = new Page<>(1, 20);
        when(prepaymentService.pageQuery(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/prepayment/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询预付款_自定义参数正确绑定")
    void pageQuery_customParams_boundCorrectly() throws Exception {
        IPage<PrepaymentEntity> page = new Page<>(2, 50);
        when(prepaymentService.pageQuery(eq(100L), eq(200L), eq("CONFIRMED"), eq(2), eq(50))).thenReturn(page);

        mvc.perform(get("/api/v1/prepayment/page")
                        .param("vendorId", "100")
                        .param("customerId", "200")
                        .param("status", "CONFIRMED")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("查询预付款详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(1L);
        entity.setAmount(new BigDecimal("5000.00"));
        entity.setStatus("CONFIRMED");
        when(prepaymentService.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/v1/prepayment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("新增预付款_RequestBody正确解析_Service被调用")
    void create_requestBody_parsedCorrectly() throws Exception {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setVendorId(10L);
        entity.setAmount(new BigDecimal("3000.00"));

        PrepaymentEntity created = new PrepaymentEntity();
        created.setId(1L);
        created.setStatus("DRAFT");
        when(prepaymentService.create(any())).thenReturn(created);

        mvc.perform(post("/api/v1/prepayment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(prepaymentService).create(argThat(e ->
                e.getVendorId() == 10L &&
                e.getAmount().compareTo(new BigDecimal("3000.00")) == 0
        ));
    }

    @Test
    @DisplayName("确认预付款_PathVariable正确绑定")
    void confirm_pathVariable_boundCorrectly() throws Exception {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(1L);
        entity.setStatus("CONFIRMED");
        when(prepaymentService.confirm(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/v1/prepayment/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(prepaymentService).confirm(eq(1L));
    }

    @Test
    @DisplayName("预付冲应付_多参数正确绑定")
    void applyToPayable_params_boundCorrectly() throws Exception {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(1L);
        entity.setStatus("APPLIED");
        when(prepaymentService.applyToPayable(anyLong(), anyLong(), any(), anyString(), anyLong(), anyString()))
                .thenReturn(entity);

        mvc.perform(post("/api/v1/prepayment/1/apply-to-payable/2")
                        .param("applyAmount", "1000.00")
                        .param("period", "2024-01")
                        .param("userId", "5")
                        .param("summary", "冲抵采购发票")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        verify(prepaymentService).applyToPayable(eq(1L), eq(2L), eq(new BigDecimal("1000.00")), eq("2024-01"), eq(5L), eq("冲抵采购发票"));
    }

    @Test
    @DisplayName("预收冲应收_多参数正确绑定")
    void applyToReceivable_params_boundCorrectly() throws Exception {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(1L);
        entity.setStatus("APPLIED");
        when(prepaymentService.applyToReceivable(anyLong(), anyLong(), any(), anyString(), anyLong(), anyString()))
                .thenReturn(entity);

        mvc.perform(post("/api/v1/prepayment/1/apply-to-receivable/2")
                        .param("applyAmount", "2000.00")
                        .param("period", "2024-02")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(prepaymentService).applyToReceivable(eq(1L), eq(2L), eq(new BigDecimal("2000.00")), eq("2024-02"), eq(0L), isNull());
    }

    @Test
    @DisplayName("反冲预付款_必选reason参数正确绑定")
    void reverse_requiredReason_boundCorrectly() throws Exception {
        doNothing().when(prepaymentService).reverse(anyLong(), anyLong(), anyString());

        mvc.perform(post("/api/v1/prepayment/1/reverse")
                        .param("reason", "业务取消")
                        .param("userId", "3")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(prepaymentService).reverse(eq(1L), eq(3L), eq("业务取消"));
    }

    @Test
    @DisplayName("查询供应商未结清预付款_PathVariable正确绑定")
    void getOpenPrepayments_pathVariable_boundCorrectly() throws Exception {
        when(prepaymentService.getOpenPrepayments(eq(100L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/prepayment/open/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("查询客户未结清预收款_PathVariable正确绑定")
    void getOpenPrepaymentsForCustomer_pathVariable_boundCorrectly() throws Exception {
        when(prepaymentService.getOpenPrepaymentsForCustomer(eq(200L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/prepayment/open-customer/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}