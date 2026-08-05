package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.sme.cash.service.BankStatementService;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BankStatementControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BankStatementService service;

    @BeforeEach
    void setUpSecurityContext() {
        com.huicai.base.system.entity.UserEntity user = new com.huicai.base.system.entity.UserEntity();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("test123");
        user.setEnterpriseId(1L);
        user.setUserType("ENTERPRISE");
        LoginUser loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @Test
    @DisplayName("分页查询对账单_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(new Page<>());

        mvc.perform(get("/api/sme/cash/v1/bank-statements/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询对账单详情_PathVariable正确解析")
    void detail_pathVariable_parsedCorrectly() throws Exception {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setId(1L);
        entity.setSummary("测试流水");
        when(service.getDetail(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/cash/v1/bank-statements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.summary").value("测试流水"));
    }

    @Test
    @DisplayName("导入CSV_RequestBody正确解析")
    void importCsv_requestBody_parsedCorrectly() throws Exception {
        when(service.importFromCsv(eq(1L), anyString())).thenReturn(10);

        mvc.perform(post("/api/sme/cash/v1/bank-statements/import-csv")
                        .param("accountId", "1")
                        .content("date,amount\n2026-01-01,1000")
                        .contentType("text/plain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("审核_审核端点")
    void audit_confirmEndpoint() throws Exception {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setId(1L);
        entity.setReviewStatus("AUDITED");
        when(service.audit(eq(1L), anyLong())).thenReturn(entity);

        mvc.perform(post("/api/sme/cash/v1/bank-statements/1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("出纳确认_出纳确认端点")
    void review_confirmEndpoint() throws Exception {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setId(1L);
        entity.setReviewStatus("CONFIRMED");
        when(service.review(eq(1L), anyLong())).thenReturn(entity);

        mvc.perform(post("/api/sme/cash/v1/bank-statements/1/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("删除对账单_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).deleteStatement(eq(1L));

        mvc.perform(delete("/api/sme/cash/v1/bank-statements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("智能匹配_自动匹配端点")
    void autoMatch_autoMatchEndpoint() throws Exception {
        when(service.autoMatch(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/sme/cash/v1/bank-statements/auto-match")
                        .param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("确认匹配_匹配确认端点")
    void confirmMatch_confirmMatchEndpoint() throws Exception {
        when(service.confirmMatch(anyLong(), anyLong())).thenReturn(1);

        mvc.perform(post("/api/sme/cash/v1/bank-statements/1/confirm-match")
                        .param("journalId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分类_分类端点")
    void classify_classifyEndpoint() throws Exception {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setId(1L);
        entity.setClassification("business_receipt");
        when(service.classifySingle(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/cash/v1/bank-statements/1/classify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("批量审核_批量审核端点")
    void batchAudit_batchAuditEndpoint() throws Exception {
        when(service.batchAudit(anyList(), anyLong())).thenReturn(new BankStatementService.BatchResult(3, 3, List.of()));

        mvc.perform(post("/api/sme/cash/v1/bank-statements/batch-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2,3]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}