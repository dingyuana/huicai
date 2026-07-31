/**
 * BusinessDocController 契约测试 (L3 MockMvc)
 *
 * 验证 API 契约：HTTP 状态码、JSON 响应结构、错误码。
 *
 * @模块: 转换自 RestAssured 版本
 */
package com.huicai.api.rest;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.base.business.service.BusinessDocService;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.huicai.sme.arap.controller.BusinessDocController.class)
@AutoConfigureMockMvc(addFilters = false)
class BusinessDocRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private BusinessDocService docService;
    @MockBean private VoucherTemplateMapper templateMapper;
    private static final String BASE = "/api/sme/arap/v1/business-docs";

    @MockBean private com.huicai.config.security.JwtProvider jwtProvider;
    @MockBean private com.huicai.base.system.service.impl.UserDetailsServiceImpl userDetailsService;
    @MockBean private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(1L); user.setUsername("admin"); user.setPassword("encoded");
        user.setUserType("AGENCY"); user.setAgencyId(1L); user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE"); user.setDeleted(0);
        LoginUser loginUser = new LoginUser(user, List.of(), null, 1L, "AGENCY", "AGENCY_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test @DisplayName("POST /page — 分页查询")
    void page() throws Exception {
        when(docService.pageQuery(any())).thenReturn(null);
        mvc.perform(post(BASE + "/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /{id} — 详情")
    void getById() throws Exception {
        when(docService.getDetail(anyLong())).thenReturn(null);
        mvc.perform(get(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST — 创建")
    void create() throws Exception {
        when(docService.create(any(), any())).thenReturn(null);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("PUT /{id} — 更新")
    void update() throws Exception {
        when(docService.update(any(), any())).thenReturn(null);
        mvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("DELETE /{id} — 删除")
    void deleteDoc() throws Exception {
        doNothing().when(docService).delete(anyLong());
        mvc.perform(delete(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — DRAFT→SUBMITTED")
    void submit() throws Exception {
        doNothing().when(docService).submit(anyLong(), any());
        mvc.perform(post(BASE + "/1/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/approve — SUBMITTED→APPROVED")
    void approve() throws Exception {
        doNothing().when(docService).approve(anyLong(), any());
        mvc.perform(post(BASE + "/1/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reject — 驳回")
    void reject() throws Exception {
        doNothing().when(docService).reject(anyLong(), any());
        mvc.perform(post(BASE + "/1/reject").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/generate-voucher — 生成凭证")
    void generateVoucher() throws Exception {
        when(docService.generateVoucher(anyLong(), any())).thenReturn(null);
        mvc.perform(post(BASE + "/1/generate-voucher").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reverse — 红冲")
    void reverse() throws Exception {
        when(docService.reverse(anyLong(), any())).thenReturn(null);
        mvc.perform(post(BASE + "/1/reverse").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — 非法状态返回 400")
    void submit_error() throws Exception {
        doThrow(new BusinessException(400, "仅DRAFT状态可提交")).when(docService).submit(eq(1L), any());
        mvc.perform(post(BASE + "/1/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }
}