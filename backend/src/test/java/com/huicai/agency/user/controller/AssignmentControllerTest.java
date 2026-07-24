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