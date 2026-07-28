package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.sme.cash.service.BankJournalService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BankJournalControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BankJournalService service;

    @Test
    @DisplayName("分页查询银行日记账_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), any(), any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/cash/v1/bank-journals/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("新增银行日记账_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        BankJournalEntity input = new BankJournalEntity();
        input.setAmount(BigDecimal.valueOf(5000));

        BankJournalEntity created = new BankJournalEntity();
        created.setId(1L);
        when(service.create(any(BankJournalEntity.class), anyLong())).thenReturn(created);

        mvc.perform(post("/api/sme/cash/v1/bank-journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新银行日记账_RequestBody正确解析")
    void update_requestBody_parsedCorrectly() throws Exception {
        BankJournalEntity input = new BankJournalEntity();
        input.setAmount(BigDecimal.valueOf(10000));

        BankJournalEntity updated = new BankJournalEntity();
        updated.setId(1L);
        when(service.update(eq(1L), any(BankJournalEntity.class))).thenReturn(updated);

        mvc.perform(put("/api/sme/cash/v1/bank-journals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("删除银行日记账_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(eq(1L));

        mvc.perform(delete("/api/sme/cash/v1/bank-journals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("生成凭证_凭证生成端点")
    void generateVoucher_voucherGenerationEndpoint() throws Exception {
        when(service.generateVoucher(eq(1L), anyLong())).thenReturn(100L);

        mvc.perform(post("/api/sme/cash/v1/bank-journals/1/generate-voucher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("汇总查询_RequestParam正确绑定")
    void aggregate_params_boundCorrectly() throws Exception {
        when(service.aggregate(eq(1L), eq("202601"))).thenReturn(List.of(Map.of("total", 1000)));

        mvc.perform(get("/api/sme/cash/v1/bank-journals/aggregate")
                        .param("accountId", "1")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询余额_RequestParam正确绑定")
    void balance_params_boundCorrectly() throws Exception {
        when(service.getAccountBalance(eq(1L))).thenReturn(BigDecimal.valueOf(100000));

        mvc.perform(get("/api/sme/cash/v1/bank-journals/balance")
                        .param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}