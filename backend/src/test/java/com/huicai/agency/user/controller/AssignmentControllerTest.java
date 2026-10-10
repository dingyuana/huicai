package com.huicai.agency.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.agency.user.dto.AssignmentCreateDTO;
import com.huicai.agency.user.dto.AssignmentVO;
import com.huicai.agency.user.service.AgencyUserEnterpriseService;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import com.huicai.common.exception.BusinessException;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AssignmentController 单元测试 — 客户分配管理 API
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssignmentControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AgencyUserEnterpriseService agencyUserEnterpriseService;

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
    private void stubValidToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("token:blacklist:" + VALID_TOKEN))).thenReturn(null);
        when(jwtProvider.validateToken(eq(VALID_TOKEN))).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(eq(VALID_TOKEN))).thenReturn("admin");
        when(jwtProvider.getUserIdFromToken(eq(VALID_TOKEN))).thenReturn(1L);
        when(jwtProvider.getEnterpriseIdFromToken(eq(VALID_TOKEN))).thenReturn(null);
        when(jwtProvider.getAgencyIdFromToken(eq(VALID_TOKEN))).thenReturn(1L);
        when(jwtProvider.getUserTypeFromToken(eq(VALID_TOKEN))).thenReturn("AGENCY");
        when(jwtProvider.getAgencyRoleFromToken(eq(VALID_TOKEN))).thenReturn("AGENCY_ADMIN");

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$encoded");
        user.setUserType("AGENCY");
        user.setAgencyId(1L);
        user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(eq(1L))).thenReturn(List.of());
        when(menuMapper.selectBatchIds(anyList())).thenReturn(List.<MenuEntity>of());
    }

    /**
     * 变体：把登录用户降级为 {@code ACCOUNTANT}（非代理管理员）。
     *
     * <p>🔴 <b>用于 V176 的负向断言</b>：撤掉本表 RLS 后，读端点若仍无角色校验，
     * 任何登录用户都能遍历 {@code agencyUserId} 读出别家代理的派工名单。
     * 下面两条用例锁死「非代理管理员一律拒绝」，且断言
     * <b>service 层方法一次都没被调用</b> —— 只断言 403 是不够的：
     * 若实现是「先查后判」，数据已经被读出来了（§4.5 第 43 条的负向断言要求）。
     */
    @SuppressWarnings("unchecked")
    private void stubTokenAsAccountant() {
        stubValidToken();
        when(jwtProvider.getAgencyRoleFromToken(eq(VALID_TOKEN))).thenReturn("ACCOUNTANT");
        when(jwtProvider.getUserTypeFromToken(eq(VALID_TOKEN))).thenReturn("AGENCY");
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("accountant01");
        user.setPassword("$2a$10$encoded");
        user.setUserType("AGENCY");
        user.setAgencyId(1L);
        user.setAgencyRole("ACCOUNTANT");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(user);
    }

    @Test
    @DisplayName("V176 负向：非代理管理员读派工列表应被拒，且服务层一次都不被调用")
    void listByAgencyUserIdRejectsNonDispatcher() throws Exception {
        stubTokenAsAccountant();

        // ⚠️ 本项目约定：业务异常由 GlobalExceptionHandler 写进 body 的 code 字段，
        //    HTTP 状态码仍为 200（同本类既有的 testUnassignNotFound 断言口径）。
        //    故此处断言 $.code 而非 HTTP 403 —— 否则会把「约定」误判成「守卫失效」。
        mvc.perform(get("/api/v1/agency/assignments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("agencyUserId", "10"))
                .andExpect(jsonPath("$.code").value(403));

        // 关键负向断言：拒绝必须发生在**进入服务层之前**，
        // 否则说明数据已被读出、只是没返回 —— 那是「假拒绝」。
        verify(agencyUserEnterpriseService, never()).listByAgencyUserId(anyLong());
    }

    @Test
    @DisplayName("V176 负向：非代理管理员派工应被拒，且服务层一次都不被调用")
    void assignRejectsNonDispatcher() throws Exception {
        stubTokenAsAccountant();
        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        mvc.perform(post("/api/v1/agency/assignments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(jsonPath("$.code").value(403));

        verify(agencyUserEnterpriseService, never()).assign(any(AssignmentCreateDTO.class));
    }

    @Test
    @DisplayName("V176 负向：非代理管理员取消派工应被拒，且服务层一次都不被调用")
    void unassignRejectsNonDispatcher() throws Exception {
        stubTokenAsAccountant();

        mvc.perform(delete("/api/v1/agency/assignments/{id}", 1L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(jsonPath("$.code").value(403));

        verify(agencyUserEnterpriseService, never()).unassign(anyLong());
    }

    @Test
    @DisplayName("场景9: 分配客户企业给会计")
    void testAssignEnterprise() throws Exception {
        stubValidToken();
        doNothing().when(agencyUserEnterpriseService).assign(any(AssignmentCreateDTO.class));

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        mvc.perform(post("/api/v1/agency/assignments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(agencyUserEnterpriseService).assign(any(AssignmentCreateDTO.class));
    }

    @Test
    @DisplayName("分配参数校验失败")
    void testAssignValidationFails() throws Exception {
        stubValidToken();
        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        // 缺少必填字段

        mvc.perform(post("/api/v1/agency/assignments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("场景15: 取消客户分配")
    void testUnassignEnterprise() throws Exception {
        stubValidToken();
        doNothing().when(agencyUserEnterpriseService).unassign(1L);

        mvc.perform(delete("/api/v1/agency/assignments/{id}", 1L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(agencyUserEnterpriseService).unassign(1L);
    }

    @Test
    @DisplayName("取消不存在的分配记录")
    void testUnassignNotFound() throws Exception {
        stubValidToken();
        doThrow(BusinessException.notFound("分配记录不存在"))
                .when(agencyUserEnterpriseService).unassign(99L);

        mvc.perform(delete("/api/v1/agency/assignments/{id}", 99L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("查询代理用户的分配列表")
    void testListByAgencyUserId() throws Exception {
        stubValidToken();
        AssignmentVO vo = new AssignmentVO();
        vo.setId(1L);
        vo.setAgencyUserId(10L);
        vo.setEnterpriseId(100L);
        vo.setEnterpriseName("测试企业");
        vo.setAssignedAt(LocalDateTime.now());

        when(agencyUserEnterpriseService.listByAgencyUserId(10L)).thenReturn(List.of(vo));

        mvc.perform(get("/api/v1/agency/assignments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("agencyUserId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].enterpriseName").value("测试企业"));
    }
}