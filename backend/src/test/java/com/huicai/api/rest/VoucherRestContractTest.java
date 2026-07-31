/**
 * Voucher Controller 契约测试 (L3 MockMvc)
 *
 * 验证凭证管理 API 的 HTTP 契约：
 * - HTTP 状态码（200）
 * - JSON 响应结构（code/data/msg）
 * - 非法状态转换的 400 错误码
 *
 * @模块: Voucher Module
 */
package com.huicai.api.rest;

import com.huicai.base.voucher.controller.VoucherController;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.base.voucher.service.NumberingTraceService;
import com.huicai.common.exception.BusinessException;
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

@WebMvcTest(VoucherController.class)
@AutoConfigureMockMvc(addFilters = false)
class VoucherRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private VoucherService voucherService;
    @MockBean private NumberingTraceService numberingTraceService;
    @MockBean private com.huicai.config.security.JwtProvider jwtProvider;
    @MockBean private com.huicai.base.system.service.impl.UserDetailsServiceImpl userDetailsService;
    @MockBean private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final String BASE = "/api/base/voucher/v1/vouchers";

    @BeforeEach
    void setUp() {
        com.huicai.base.system.entity.UserEntity user = new com.huicai.base.system.entity.UserEntity();
        user.setId(1L); user.setUsername("admin"); user.setPassword("enc");
        user.setUserType("AGENCY"); user.setAgencyId(1L); user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE"); user.setDeleted(0);
        com.huicai.config.security.LoginUser loginUser =
                new com.huicai.config.security.LoginUser(user, List.of(), null, 1L, "AGENCY", "AGENCY_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test @DisplayName("POST /page — 分页查询成功")
    void page() throws Exception {
        when(voucherService.pageQuery(any())).thenReturn(null);
        mvc.perform(post(BASE + "/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /{id} — 详情")
    void getById() throws Exception {
        when(voucherService.getDetail(anyLong())).thenReturn(null);
        mvc.perform(get(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST — 创建")
    void create() throws Exception {
        when(voucherService.create(any(), anyLong())).thenReturn(null);
        String body = "{\"period\":\"202607\",\"voucherTypeId\":1,\"entries\":[{\"subjectId\":1,\"debit\":1000,\"credit\":0}]}";
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("PUT /{id} — 更新")
    void update() throws Exception {
        when(voucherService.update(any(), anyLong())).thenReturn(null);
        String body = "{\"id\":1,\"period\":\"202607\",\"voucherTypeId\":1,\"entries\":[{\"subjectId\":1,\"debit\":1000,\"credit\":0}]}";
        mvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("DELETE /{id} — 删除成功")
    void deleteVoucher() throws Exception {
        doNothing().when(voucherService).delete(anyLong());
        mvc.perform(delete(BASE + "/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — DRAFT→SUBMITTED")
    void submit() throws Exception {
        doNothing().when(voucherService).submit(anyLong(), anyLong());
        mvc.perform(post(BASE + "/1/submit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/audit — SUBMITTED→AUDITED")
    void audit() throws Exception {
        doNothing().when(voucherService).audit(anyLong(), anyLong());
        mvc.perform(post(BASE + "/1/audit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/post — AUDITED→POSTED")
    void postVoucher() throws Exception {
        doNothing().when(voucherService).post(anyLong(), anyLong());
        mvc.perform(post(BASE + "/1/post"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reverse — 红冲")
    void reverse() throws Exception {
        when(voucherService.reverse(anyLong(), anyLong())).thenReturn(null);
        mvc.perform(post(BASE + "/1/reverse"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reject — 驳回")
    void reject() throws Exception {
        doNothing().when(voucherService).reject(anyLong(), anyLong(), anyString());
        mvc.perform(post(BASE + "/1/reject").queryParam("reason", "驳回"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /batch-submit — 批量提交")
    void batchSubmit() throws Exception {
        doNothing().when(voucherService).batchSubmit(anyList(), anyLong());
        mvc.perform(post(BASE + "/batch-submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — 非法状态返回 400")
    void submit_error() throws Exception {
        doThrow(new BusinessException(400, "仅DRAFT状态可提交")).when(voucherService).submit(anyLong(), anyLong());
        mvc.perform(post(BASE + "/1/submit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }
}