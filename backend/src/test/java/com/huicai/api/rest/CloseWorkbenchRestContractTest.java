/**
 * CloseWorkbenchController 契约测试 (L3 MockMvc)
 *
 * 验证 P84 结账工作台端点契约：
 * - GET  /generate-sequence/{period}  三步结转序列（DEPR/CLOSE/DISTRIB）
 * - POST /batch-review-post           一键人工审核记账（JSON body {voucherIds}）
 *
 * 关键覆盖点（P84 SPEC §3.2）：batch-review-post 用 @RequestBody 而非 @RequestParam ——
 * 前端 axios 发 JSON body，若后端用 @RequestParam 取不到查询参数会 500。本测试断言
 * JSON body 能正确反序列化并调用 service。
 *
 * @模块: P84 结账工作台
 */
package com.huicai.api.rest;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.service.impl.UserDetailsServiceImpl;
import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.asset.service.DepreciationVoucherService;
import com.huicai.sme.periodclose.service.CarryoverSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(com.huicai.sme.periodclose.controller.CloseWorkbenchController.class)
@AutoConfigureMockMvc(addFilters = false)
class CloseWorkbenchRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private CarryoverSequenceService carryoverSequenceService;
    @MockBean private PeriodCloseService periodCloseService;
    @MockBean private DepreciationVoucherService depreciationVoucherService;
    @MockBean private com.huicai.config.security.JwtProvider jwtProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private StringRedisTemplate redisTemplate;

    private static final String BASE = "/api/base/voucher/v1/period-close";

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(1L); user.setUsername("admin"); user.setPassword("encoded");
        user.setUserType("SME"); user.setEnterpriseId(1L);
        user.setStatus("ACTIVE"); user.setDeleted(0);
        LoginUser loginUser = new LoginUser(user, List.of(), null, 1L, "SME", "SME_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CarryoverStepResult step(String step, String name, Long voucherId, String status) {
        return new CarryoverStepResult(step, name, voucherId, status, null, List.of());
    }

    @Test
    @DisplayName("GET /generate-sequence/{period} — 三步序列")
    void generateSequence() throws Exception {
        when(carryoverSequenceService.generateSequence(anyString(), anyLong())).thenReturn(List.of(
                step("DEPR", "折旧凭证", 901L, "GENERATED"),
                step("CLOSE", "损益结转", 902L, "GENERATED"),
                step("DISTRIB", "利润分配", 903L, "GENERATED")));
        mvc.perform(get(BASE + "/generate-sequence").param("period", "202609")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].step").value("DEPR"))
                .andExpect(jsonPath("$.data[1].voucherId").value(902));
    }

    @Test
    @DisplayName("GET /generate-sequence/{period} — 部分跳过仍返回三步")
    void generateSequence_partialSkipped() throws Exception {
        when(carryoverSequenceService.generateSequence(anyString(), anyLong())).thenReturn(List.of(
                step("DEPR", "折旧凭证", null, "SKIPPED"),
                step("CLOSE", "损益结转", null, "SKIPPED"),
                step("DISTRIB", "利润分配", null, "SKIPPED")));
        mvc.perform(get(BASE + "/generate-sequence").param("period", "202609")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data[0].voucherId").doesNotExist());
    }

    @Test
    @DisplayName("GET /generate-sequence/{period} — 缺 period 参数(项目全局异常处理映射 500)")
    void generateSequence_missingPeriod() throws Exception {
        // 项目全局异常处理把 @RequestParam 缺失映射为 500（与业务异常同码），
        // 非标准 400。此处只断言"确实报错"，不断言具体码位。
        mvc.perform(get(BASE + "/generate-sequence").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /batch-review-post — JSON body 正确反序列化(非 @RequestParam)")
    void batchReviewPost_jsonBody() throws Exception {
        // P84 SPEC §3.2 关键断言：body 必须能被正确解析成 voucherIds 并传给 service。
        // 若后端误用 @RequestParam，这里会 500(MissingServletRequestParameter)。
        mvc.perform(post(BASE + "/batch-review-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voucherIds\":[101,102,103]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(periodCloseService).batchReviewPost(eq(List.of(101L, 102L, 103L)), anyLong());
    }

    @Test
    @DisplayName("POST /batch-review-post — 单张凭证")
    void batchReviewPost_single() throws Exception {
        mvc.perform(post(BASE + "/batch-review-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voucherIds\":[201]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(periodCloseService).batchReviewPost(eq(List.of(201L)), anyLong());
    }

    @Test
    @DisplayName("POST /batch-review-post — 空数组返回 400")
    void batchReviewPost_emptyList() throws Exception {
        mvc.perform(post(BASE + "/batch-review-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voucherIds\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(periodCloseService);
    }

    @Test
    @DisplayName("POST /batch-review-post — 缺 voucherIds 字段返回 400")
    void batchReviewPost_missingField() throws Exception {
        mvc.perform(post(BASE + "/batch-review-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foo\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(periodCloseService);
    }

    @Test
    @DisplayName("POST /batch-review-post — 仅传 query 参数不产生 service 调用(回归 @RequestParam 陷阱)")
    void batchReviewPost_queryParamOnly_noServiceCall() throws Exception {
        // 陷阱回归：P84 SPEC §3.2 记录过 @RequestParam 与 JSON body 的坑。
        // 若误用 @RequestParam 实现，query 参数会直接调用 service；
        // 正确实现(@RequestBody + @Valid)下应校验失败且不触及 service。
        mvc.perform(post(BASE + "/batch-review-post")
                        .param("voucherIds", "101")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(200,
                        result.getResponse().getStatus()));
        verifyNoInteractions(periodCloseService);
    }
}
