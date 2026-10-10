package com.huicai.agency.user.service.impl;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.user.dto.AssignmentCreateDTO;
import com.huicai.agency.user.dto.AssignmentVO;
import com.huicai.agency.user.entity.AgencyUserEnterpriseEntity;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import com.huicai.base.system.entity.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgencyUserEnterpriseService 单元测试 — 客户分配管理
 */
@ExtendWith(MockitoExtension.class)
class AgencyUserEnterpriseServiceTest {

    @Mock
    private AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    @Mock
    private AgencyUserMapper agencyUserMapper;
    @Mock
    private EnterpriseMapper enterpriseMapper;

    @InjectMocks
    private AgencyUserEnterpriseServiceImpl service;

    /**
     * 建立登录上下文。
     *
     * <p>⚠️ <b>{@code agencyId} 必须设</b>（REQ-2026-140 / V176 起）：
     * {@code listByAgencyUserId} 现会按 {@code agency_id} 校验归属
     * （该表已撤 RLS，隔离落到应用层），而 {@code SecurityUtils.getCurrentAgencyId()}
     * 取的正是这个字段。首版夹具未设它 ⇒ 两个 list 用例报「代理用户不存在」。
     *
     * <p>🔴 <b>这正是 §4.5 第 10 条的形态</b>：夹具与真实上下文不符时，
     * 新加的守卫会以「业务异常」的形式报错，而报错文案指向的是被测逻辑，
     * **不会提示你夹具缺字段** —— 容易误判成守卫写错。
     */
    private void setSecurityContext(String userType, String agencyRole) {
        setSecurityContext(userType, agencyRole, 1L);
    }

    private void setSecurityContext(String userType, String agencyRole, Long agencyId) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("pass");
        user.setUserType(userType);
        user.setAgencyRole(agencyRole);
        user.setAgencyId(agencyId);
        LoginUser loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    /** 造一个属于 agencyId 的代理用户（供 requireSameAgency 的归属校验通过）。 */
    private AgencyUserEntity agencyUserOf(Long id, Long agencyId) {
        AgencyUserEntity au = new AgencyUserEntity();
        au.setId(id);
        au.setAgencyId(agencyId);
        au.setAgencyRole("ACCOUNTANT");
        au.setDeleted(0);
        return au;
    }

    @BeforeEach
    void setUp() {
        setSecurityContext("AGENCY", "AGENCY_ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== assign ====================

    @Test
    @DisplayName("场景9: 为会计分配客户企业")
    void testAssignEnterprise() {
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setAgencyId(1L);
        agencyUser.setAgencyRole("ACCOUNTANT");
        agencyUser.setDeleted(0);
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUser);

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(100L);
        enterprise.setAgencyId(1L);
        enterprise.setEnterpriseName("测试企业");
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise);

        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of());
        when(agencyUserEnterpriseMapper.insert(any(AgencyUserEnterpriseEntity.class))).thenReturn(1);

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        assertDoesNotThrow(() -> service.assign(dto));
        verify(agencyUserEnterpriseMapper).insert(Mockito.<AgencyUserEnterpriseEntity>any());
    }

    @Test
    @DisplayName("场景18: 跨代理公司分配被阻止")
    void testCrossAgencyBlocked() {
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setAgencyId(1L);
        agencyUser.setAgencyRole("ACCOUNTANT");
        agencyUser.setDeleted(0);
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUser);

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(100L);
        enterprise.setAgencyId(2L); // 不同代理公司
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise);

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(dto));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不能跨代理公司分配客户"));
        verify(agencyUserEnterpriseMapper, never()).insert(Mockito.<AgencyUserEnterpriseEntity>any());
    }

    @Test
    @DisplayName("不能为非会计/助理角色分配客户")
    void testAssignNonAccountantFails() {
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setAgencyId(1L);
        agencyUser.setAgencyRole("AGENCY_ADMIN");
        agencyUser.setDeleted(0);
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUser);

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(dto));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("只能为会计或助理分配客户"));
    }

    @Test
    @DisplayName("重复分配被阻止")
    void testAssignDuplicateFails() {
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setAgencyId(1L);
        agencyUser.setAgencyRole("ACCOUNTANT");
        agencyUser.setDeleted(0);
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUser);

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(100L);
        enterprise.setAgencyId(1L);
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise);

        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of(100L));

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(dto));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("该客户已分配给此用户"));
    }

    @Test
    @DisplayName("分配时企业不存在")
    void testAssignEnterpriseNotFound() {
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setAgencyId(1L);
        agencyUser.setAgencyRole("ACCOUNTANT");
        agencyUser.setDeleted(0);
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUser);
        when(enterpriseMapper.selectById(100L)).thenReturn(null);

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(dto));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非管理员无权分配客户")
    void testNonAdminCannotAssign() {
        setSecurityContext("AGENCY", "ACCOUNTANT");

        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setAgencyUserId(10L);
        dto.setEnterpriseId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(dto));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权分配客户"));
    }

    // ==================== unassign ====================

    @Test
    @DisplayName("场景15: 取消客户分配")
    void testUnassignEnterprise() {
        AgencyUserEnterpriseEntity assignment = new AgencyUserEnterpriseEntity();
        assignment.setId(1L);
        assignment.setAgencyUserId(10L);
        assignment.setEnterpriseId(100L);
        assignment.setDeleted(0);
        when(agencyUserEnterpriseMapper.selectById(1L)).thenReturn(assignment);
        when(agencyUserEnterpriseMapper.updateById(any(AgencyUserEnterpriseEntity.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.unassign(1L));

        assertEquals(1, assignment.getDeleted());
        assertNotNull(assignment.getUnassignedBy());
        assertNotNull(assignment.getUnassignedAt());
        verify(agencyUserEnterpriseMapper).updateById(assignment);
    }

    @Test
    @DisplayName("取消不存在的分配记录")
    void testUnassignNotFound() {
        when(agencyUserEnterpriseMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.unassign(99L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非管理员无权取消分配")
    void testUnassignNonAdminFails() {
        setSecurityContext("AGENCY", "ACCOUNTANT");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.unassign(1L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权取消分配"));
    }

    // ==================== listByAgencyUserId ====================

    @Test
    @DisplayName("场景10: 会计只看到自己分配的企业")
    void testAccountantOnlySeesOwn() {
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUserOf(10L, 1L));
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of(100L));

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(100L);
        enterprise.setEnterpriseName("测试企业");
        enterprise.setTaxId("91110000MA12345678");
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise);

        AgencyUserEnterpriseEntity record = new AgencyUserEnterpriseEntity();
        record.setId(1L);
        record.setAgencyUserId(10L);
        record.setEnterpriseId(100L);
        record.setAssignedBy(1L);
        when(agencyUserEnterpriseMapper.selectList(any())).thenReturn(List.of(record));

        List<AssignmentVO> result = service.listByAgencyUserId(10L);

        assertEquals(1, result.size());
        assertEquals("测试企业", result.get(0).getEnterpriseName());
        assertEquals("91110000MA12345678", result.get(0).getTaxId());
    }

    @Test
    @DisplayName("查询空分配列表")
    void testListEmpty() {
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUserOf(10L, 1L));
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of());

        List<AssignmentVO> result = service.listByAgencyUserId(10L);

        assertTrue(result.isEmpty());
    }

    // ==================== V176 归属校验（REQ-2026-140）====================

    @Test
    @DisplayName("V176：查看别家代理公司的派工记录应 403，且不得触达查询")
    void crossAgencyLookupRejected() {
        // 操作者属 agency 1，但目标代理用户属 agency 2
        when(agencyUserMapper.selectById(10L)).thenReturn(agencyUserOf(10L, 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listByAgencyUserId(10L));
        assertEquals(403, ex.getCode());

        // 负向断言：拒绝必须发生在**读数据之前**，否则是「查完再拒」的假拒绝
        verify(agencyUserEnterpriseMapper, never()).getEnterpriseIdsByAgencyUserId(anyLong());
    }

    @Test
    @DisplayName("V176：目标代理用户不存在应 404")
    void missingAgencyUserRejected() {
        when(agencyUserMapper.selectById(10L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listByAgencyUserId(10L));
        assertEquals(404, ex.getCode());
        verify(agencyUserEnterpriseMapper, never()).getEnterpriseIdsByAgencyUserId(anyLong());
    }

    @Test
    @DisplayName("V176：SUPER_ADMIN 豁免归属校验（否则超管无法运维）")
    void superAdminBypassesAgencyCheck() {
        setSecurityContext("SUPER_ADMIN", "AGENCY_ADMIN", 1L);
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of());

        // 刻意**不** stub agencyUserMapper.selectById：超管应在查目标用户之前就返回，
        // 若实现顺序反了（先查后判），strict-stubs 会因「多余桩」之外的
        // 「桩未使用」报错而暴露顺序问题。
        assertTrue(service.listByAgencyUserId(10L).isEmpty());
        verify(agencyUserMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("V176：登录上下文缺 agencyId 时必须 fail-loud，不得静默拒绝")
    void missingAgencyIdInContextFailsLoud() {
        // 模拟「LoginUser 未带 agencyId」的历史缺陷形态：
        // 若这里被当成「无权」处理，代理管理员会被锁在门外且文案误导。
        setSecurityContext("AGENCY", "AGENCY_ADMIN", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listByAgencyUserId(10L));
        assertTrue(ex.getMessage().contains("agencyId"),
                "报错须指明缺失字段，实际：" + ex.getMessage());
        // fail-loud 必须发生在**查库之前**：agencyId 缺失时不该再去查目标用户
        verify(agencyUserMapper, never()).selectById(anyLong());
        verify(agencyUserEnterpriseMapper, never()).getEnterpriseIdsByAgencyUserId(anyLong());
    }

    // ==================== getEnterpriseIdsByAgencyUserId ====================

    @Test
    @DisplayName("获取代理用户负责的企业ID列表")
    void testGetEnterpriseIdsByAgencyUserId() {
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of(100L, 200L));

        List<Long> result = service.getEnterpriseIdsByAgencyUserId(10L);

        assertEquals(2, result.size());
        assertTrue(result.contains(100L));
        assertTrue(result.contains(200L));
    }
}
