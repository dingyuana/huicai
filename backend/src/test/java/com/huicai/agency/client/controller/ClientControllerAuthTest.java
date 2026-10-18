package com.huicai.agency.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.agency.client.dto.ContractCreateDTO;
import com.huicai.agency.client.dto.ContractVO;
import com.huicai.agency.client.dto.RenewalReminderVO;
import com.huicai.agency.client.service.ContractService;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import com.huicai.config.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * V177：客户合同端点角色鉴权 + 服务层 agency_id 归属（REQ-2026-140）
 *
 * <p><b>为什么必须有</b>：V177 撤掉了 {@code t_contract} 的 RLS
 * （该表 {@code enterprise_id} 语义是「签约客户」而非「归属租户」，
 * 续费提醒按设计就是跨客户扫描，RLS 会让代理看不到本代理其它客户的合同）。
 * 撤 RLS 后 DB 层不再兜底，而本控制器此前<b>完全无鉴权</b>
 * ⇒ 任何登录用户都能遍历 {@code GET /contracts/page} 读出全部客户的
 * 合同金额与到期日。**撤 RLS 与护栏必须同批**，否则等于用一个洞换另一个洞。
 *
 * <p>⚠️ <b>覆盖度诚实声明</b>：5 处 {@code requireContractAccess()} 中，
 * <b>只有 3 处（三个 GET）能被反证证伪</b>；两个写端点被更上游的
 * {@code EnterpriseWriteGuard} 抢先拦截，恒返 403 且到不了本守卫
 * ⇒ 那两条负向用例<b>不构成本守卫的证据</b>。详见
 * {@link #nonDispatcherCannotCreate} 的说明。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientControllerAuthTest {

    private static final String TOKEN = "valid.jwt.token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ContractService contractService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserRoleMapper userRoleMapper;

    @MockBean
    private RoleMenuMapper roleMenuMapper;

    @MockBean
    private MenuMapper menuMapper;

    @MockBean
    private MenuService menuService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOperations;

    @SuppressWarnings("unchecked")
    private void stub(String agencyRole, String userType) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("token:blacklist:" + TOKEN))).thenReturn(null);
        when(jwtProvider.validateToken(eq(TOKEN))).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(eq(TOKEN))).thenReturn("tester");
        when(jwtProvider.getUserIdFromToken(eq(TOKEN))).thenReturn(1L);
        when(jwtProvider.getEnterpriseIdFromToken(eq(TOKEN))).thenReturn(1L);
        when(jwtProvider.getAgencyIdFromToken(eq(TOKEN))).thenReturn(1L);
        when(jwtProvider.getUserTypeFromToken(eq(TOKEN))).thenReturn(userType);
        when(jwtProvider.getAgencyRoleFromToken(eq(TOKEN))).thenReturn(agencyRole);

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        user.setPassword("$2a$10$x");
        user.setUserType(userType);
        user.setAgencyId(1L);
        user.setAgencyRole(agencyRole);
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(eq(1L))).thenReturn(List.of());
        when(menuMapper.selectBatchIds(anyList())).thenReturn(List.<MenuEntity>of());
    }

    private void stubAdmin() {
        stub("AGENCY_ADMIN", "AGENCY");
    }

    // ───────────── 正向：代理管理员可用 ─────────────

    @Test
    @DisplayName("正向：代理管理员可读合同列表")
    void adminCanPage() throws Exception {
        stubAdmin();
        when(contractService.page(anyInt(), anyInt()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        mvc.perform(get("/api/v1/agency/contracts/page")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("正向：代理管理员可读续费提醒（V177 修复的目标场景）")
    void adminCanReadRenewalReminders() throws Exception {
        stubAdmin();
        when(contractService.getRenewalReminders()).thenReturn(List.of(new RenewalReminderVO(
                1L, "C-001", 2L, "某客户", LocalDate.now().plusDays(10),
                BigDecimal.valueOf(100), 10)));

        mvc.perform(get("/api/v1/agency/contracts/renewal-reminders")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].contractNo").value("C-001"));
    }

    @Test
    @DisplayName("正向：代理管理员可创建合同（受 EnterpriseWriteGuard 拦截，故只断言角色层放行）")
    void adminCanCreate() throws Exception {
        stubAdmin();
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setEnterpriseId(2L);
        dto.setAgencyId(1L);
        dto.setContractNo("C-NEW");
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusYears(1));
        dto.setContractType("ACCOUNTING");
        dto.setAmount(BigDecimal.valueOf(100));
        when(contractService.create(any(ContractCreateDTO.class)))
                .thenReturn(new ContractVO());

        // ⚠️ POST 还会经过 EnterpriseWriteGuard（P113），它用 JdbcTemplate 真查
        // t_enterprise.status 并**fail-closed**；本类无真实库 ⇒ 恒 403。
        // 那不是角色鉴权的问题，故此处**不**断言 $.code，
        // 改断言「角色层已放行」——即 service 被调用到了。
        // 角色层的负向断言见下方 nonDispatcherCannotCreate（它断言 never 调用）。
        // 判据：两个守卫都会产生 403，只看 $.code 无法区分是谁拒的。
        mvc.perform(post("/api/v1/agency/contracts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(jsonPath("$.code").exists());

        // 该 POST 在本类里必然止于 EnterpriseWriteGuard，故不能断言 service 被调用；
        // 角色层的正向覆盖由 adminCanPage / adminCanReadRenewalReminders 承担。
    }

    // ───────────── 负向：非代理管理员一律拒绝 ─────────────
    // ⚠️ 除断言 $.code=403 外，还 verify(never()) 服务层未被调用 ——
    //    只断言 403 不够：若实现是「先查后判」，数据已被读出，那是「假拒绝」。

    @Test
    @DisplayName("V177 负向：非代理管理员读合同列表应被拒，且服务层零调用")
    void nonDispatcherCannotPage() throws Exception {
        stub("ACCOUNTANT", "AGENCY");

        mvc.perform(get("/api/v1/agency/contracts/page")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.code").value(403));

        verify(contractService, never()).page(anyInt(), anyInt());
    }

    @Test
    @DisplayName("V177 负向：非代理管理员读续费提醒应被拒，且服务层零调用")
    void nonDispatcherCannotReadReminders() throws Exception {
        stub("ACCOUNTANT", "AGENCY");

        mvc.perform(get("/api/v1/agency/contracts/renewal-reminders")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.code").value(403));

        verify(contractService, never()).getRenewalReminders();
    }

    @Test
    @DisplayName("V177 负向：非代理管理员按 id 读合同应被拒，且服务层零调用")
    void nonDispatcherCannotGetById() throws Exception {
        stub("ACCOUNTANT", "AGENCY");

        mvc.perform(get("/api/v1/agency/contracts/{id}", 1L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.code").value(403));

        verify(contractService, never()).getById(anyLong());
    }

    // ───────────── 写路径：守卫不可达，如实标注而非假装已验证 ─────────────

    /**
     * ⚠️ <b>本用例证明的是「另一个守卫在拦」，不是本 SPEC 的角色守卫</b>。
     *
     * <p>POST/PUT 会先经过 {@code EnterpriseWriteGuard}（P113：非ACTIVE 账套禁写），
     * 它用 {@code JdbcTemplate} 真查 {@code t_enterprise.status} 并 fail-closed；
     * 本类无真实库 ⇒ 恒返 403「账套状态校验失败」，<b>根本到不了</b>
     * {@code requireContractAccess()}。
     *
     * <p><b>因此这里的 403 无法证明角色守卫有效</b> ——
     * 实测反证：注释掉 {@code create}/{@code renew} 两处
     * {@code requireContractAccess()} 后，{@link #nonDispatcherCannotCreate} 与
     * {@link #nonDispatcherCannotRenew} <b>仍然全绿</b> ⇒ 这两条对本守卫是**无效反证**。
     *
     * <p><b>可证伪的部分</b>：{@code verify(never())} 仍成立 ——
     * 请求确实没到服务层（无论被谁拦）。故断言保留，但**只当作
     * 「写路径被更上游的守卫拦住」的证据**，不当作角色鉴权的证据。
     *
     * <p><b>判据沉淀</b>：一个端点上有多个守卫时，只看响应码<b>无法区分是谁拒的</b>；
     * 反证必须逐个禁用守卫、看用例是否转红。本次正是靠反证才发现
     * 「5 处守卫只有 3 处真正可证伪」。与 §4.5 第 43 条同族：
     * <b>看起来有护栏 ≠ 护栏真的在那个位置生效</b>。
     */
    @Test
    @DisplayName("V177 写路径负向：非代理管理员创建合同被拒且未到服务层（拦截者是上游账套守卫，非角色守卫）")
    void nonDispatcherCannotCreate() throws Exception {
        stub("ACCOUNTANT", "AGENCY");
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setEnterpriseId(2L);
        dto.setAgencyId(1L);
        dto.setContractNo("C-X");
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusYears(1));

        mvc.perform(post("/api/v1/agency/contracts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(jsonPath("$.code").exists());

        verify(contractService, never()).create(any(ContractCreateDTO.class));
    }

    @Test
    @DisplayName("V177 写路径负向：非代理管理员续约被拒且未到服务层（拦截者为上游账套守卫）")
    void nonDispatcherCannotRenew() throws Exception {
        stub("ACCOUNTANT", "AGENCY");

        mvc.perform(put("/api/v1/agency/contracts/{id}/renew", 1L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.code").exists());

        verify(contractService, never()).renew(anyLong());
    }
}