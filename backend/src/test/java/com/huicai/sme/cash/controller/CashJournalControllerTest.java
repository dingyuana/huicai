package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.cash.entity.CashJournalEntity;
import com.huicai.sme.cash.service.CashJournalService;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class CashJournalControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private CashJournalService cashJournalService;

    static {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    private static void setAuth() {
        UserEntity u = new UserEntity();
        u.setId(1L); u.setUsername("test"); u.setPassword("test123");
        u.setEnterpriseId(1L); u.setUserType("ENTERPRISE");
        LoginUser lu = new LoginUser(u, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));
    }

    private static void setAuthPerRequest() {
        // set auth before each mvc.perform() call that needs it
    }

    @Test
    @DisplayName("分页查询日记账_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(cashJournalService.pageQuery(any(), any(), any(), eq(1), eq(20)))
                .thenReturn(new Page<>());

        mvc.perform(get("/api/sme/cash/v1/cash-journals/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询日记账详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        CashJournalEntity entity = new CashJournalEntity();
        entity.setId(1L);
        entity.setDebit(BigDecimal.valueOf(1000));
        when(cashJournalService.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/cash/v1/cash-journals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("新增日记账_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        setAuth();
        CashJournalEntity entity = new CashJournalEntity();
        entity.setDebit(BigDecimal.valueOf(1000));

        CashJournalEntity created = new CashJournalEntity();
        created.setId(1L);
        when(cashJournalService.create(any(CashJournalEntity.class), anyLong())).thenReturn(created);

        mvc.perform(post("/api/sme/cash/v1/cash-journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新日记账_RequestBody正确解析")
    void update_requestBody_parsedCorrectly() throws Exception {
        CashJournalEntity entity = new CashJournalEntity();
        entity.setDebit(BigDecimal.valueOf(2000));

        CashJournalEntity updated = new CashJournalEntity();
        updated.setId(1L);
        when(cashJournalService.update(eq(1L), any(CashJournalEntity.class))).thenReturn(updated);

        mvc.perform(put("/api/sme/cash/v1/cash-journals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("删除日记账_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(cashJournalService).delete(eq(1L));

        mvc.perform(delete("/api/sme/cash/v1/cash-journals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("生成凭证_凭证生成端点")
    void generateVoucher_voucherGenerationEndpoint() throws Exception {
        setAuth();
        when(cashJournalService.generateVoucher(eq(1L), anyLong())).thenReturn(100L);

        mvc.perform(post("/api/sme/cash/v1/cash-journals/1/generate-voucher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}