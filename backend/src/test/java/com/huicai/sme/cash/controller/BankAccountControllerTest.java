package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.service.BankAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BankAccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BankAccountService service;

    @Test
    @DisplayName("分页查询银行账户_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/cash/v1/bank-accounts/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询活跃账户_回调端点")
    void active_listEndpoint() throws Exception {
        when(service.listActive()).thenReturn(List.of());

        mvc.perform(get("/api/sme/cash/v1/bank-accounts/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询账户详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        BankAccountEntity entity = new BankAccountEntity();
        entity.setId(1L);
        entity.setAccountName("工行基本户");
        when(service.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/cash/v1/bank-accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.accountName").value("工行基本户"));
    }

    @Test
    @DisplayName("新增银行账户_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        BankAccountEntity input = new BankAccountEntity();
        input.setAccountName("建行一般户");

        BankAccountEntity created = new BankAccountEntity();
        created.setId(1L);
        when(service.create(any(BankAccountEntity.class))).thenReturn(created);

        mvc.perform(post("/api/sme/cash/v1/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新银行账户_RequestBody正确解析")
    void update_requestBody_parsedCorrectly() throws Exception {
        BankAccountEntity input = new BankAccountEntity();
        input.setAccountName("更新账户");

        BankAccountEntity updated = new BankAccountEntity();
        updated.setId(1L);
        when(service.update(eq(1L), any(BankAccountEntity.class))).thenReturn(updated);

        mvc.perform(put("/api/sme/cash/v1/bank-accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("删除银行账户_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(eq(1L));

        mvc.perform(delete("/api/sme/cash/v1/bank-accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}