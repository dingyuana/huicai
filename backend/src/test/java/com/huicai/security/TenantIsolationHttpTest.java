package com.huicai.security;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AT-102-1 / 1b / 1c 的 **HTTP 层** 验证（REQ-2026-129，P102 M4）
 *
 * <p>与 {@link TenantIsolationSecurityTest} 的分工：后者验的是成员校验**规则**，
 * 本类验的是该规则**真的挂在请求链路上** —— 规则正确但没接进过滤器，
 * 同样等于没修（与 P104 覆盖率门禁假绿同族：组件在、但不生效）。
 *
 * <p>无 JWT 时请求本就会被 401 挡下，故本类只断言「匿名请求不会因为
 * 新增校验而拿到 200」，越权放行的正面验证交由规则层用例承担。
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@DisplayName("P102 越权切换企业 HTTP 层")
class TenantIsolationHttpTest extends AbstractMapperTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("AT-102-1 无凭证且带 X-Enterprise-Id 不得放行")
    void anonymousWithEnterpriseHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/system/report-data-stats")
                        .header("X-Enterprise-Id", "2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("负向：非法格式的 X-Enterprise-Id 不得导致 500")
    void malformedEnterpriseHeaderDoesNotCrash() throws Exception {
        mockMvc.perform(get("/api/v1/system/report-data-stats")
                        .header("X-Enterprise-Id", "not-a-number"))
                .andExpect(status().isUnauthorized());
    }
}
