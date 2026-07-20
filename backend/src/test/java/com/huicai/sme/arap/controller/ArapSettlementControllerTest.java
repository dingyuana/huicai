package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.business.dto.vo.ArapSettlementVO;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.sme.arap.service.ArapSettlementService;
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
class ArapSettlementControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ArapSettlementService service;

    @Test
    @DisplayName("分页查询核销单_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        IPage<ArapSettlementVO> page = new Page<>(1, 20);
        when(service.pageQueryWithPartyName(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/arap-settlements/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询核销单_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<ArapSettlementVO> page = new Page<>(2, 50);
        when(service.pageQueryWithPartyName(eq("CONFIRMED"), eq("STL-2024-001"), eq(2), eq(50))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/arap-settlements/page")
                        .param("status", "CONFIRMED")
                        .param("voucherNo", "STL-2024-001")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("核销单详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        ArapSettlementVO vo = new ArapSettlementVO();
        vo.setId(1L);
        vo.setStatus("DRAFT");
        vo.setTotalAmount(new BigDecimal("10000.00"));
        when(service.getDetailWithPartyName(eq(1L))).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/arap-settlements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("创建核销单_RequestBody正确解析_Service被调用")
    void create_requestBody_parsedCorrectly() throws Exception {
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementType("MANUAL");
        settlement.setTotalAmount(new BigDecimal("5000.00"));

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettledAmount(new BigDecimal("5000.00"));

        ArapSettlementController.CreateRequest request = new ArapSettlementController.CreateRequest();
        request.settlement = settlement;
        request.entries = List.of(entry);

        ArapSettlementEntity created = new ArapSettlementEntity();
        created.setId(1L);
        created.setStatus("DRAFT");
        when(service.create(any(), anyList())).thenReturn(created);

        mvc.perform(post("/api/sme/arap/v1/arap-settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(service).create(argThat(s ->
                "MANUAL".equals(s.getSettlementType()) &&
                s.getTotalAmount().compareTo(new BigDecimal("5000.00")) == 0
        ), anyList());
    }

    @Test
    @DisplayName("确认核销_PathVariable正确绑定")
    void confirm_pathVariable_boundCorrectly() throws Exception {
        ArapSettlementEntity entity = new ArapSettlementEntity();
        entity.setId(1L);
        entity.setStatus("CONFIRMED");
        when(service.confirm(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/arap/v1/arap-settlements/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(service).confirm(eq(1L));
    }

    @Test
    @DisplayName("生成凭证_PathVariable正确绑定")
    void generateVoucher_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).generateVoucher(anyLong());

        mvc.perform(post("/api/sme/arap/v1/arap-settlements/1/generate-voucher"))
                .andExpect(status().isOk());

        verify(service).generateVoucher(eq(1L));
    }

    @Test
    @DisplayName("反核销_PathVariable正确绑定")
    void reverse_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).reverse(anyLong());

        mvc.perform(post("/api/sme/arap/v1/arap-settlements/1/reverse"))
                .andExpect(status().isOk());

        verify(service).reverse(eq(1L));
    }

    @Test
    @DisplayName("取消核销单_PathVariable正确绑定")
    void cancel_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).cancel(anyLong());

        mvc.perform(post("/api/sme/arap/v1/arap-settlements/1/cancel"))
                .andExpect(status().isOk());

        verify(service).cancel(eq(1L));
    }

    @Test
    @DisplayName("核销明细列表_PathVariable正确解析")
    void getEntries_pathVariable_parsedCorrectly() throws Exception {
        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setId(1L);
        entry.setSettlementId(1L);
        entry.setSettledAmount(new BigDecimal("5000.00"));
        when(service.getEntries(eq(1L))).thenReturn(List.of(entry));

        mvc.perform(get("/api/sme/arap/v1/arap-settlements/1/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].settlementId").value(1));
    }

    @Test
    @DisplayName("删除核销单_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(anyLong());

        mvc.perform(delete("/api/sme/arap/v1/arap-settlements/1"))
                .andExpect(status().isOk());

        verify(service).delete(eq(1L));
    }
}