package com.huicai.agency.dashboard.service.impl;

import com.huicai.agency.dashboard.entity.ServiceProgressEntity;
import com.huicai.agency.dashboard.mapper.ServiceProgressMapper;
import com.huicai.agency.dashboard.service.ServiceProgressService.ServiceProgressRowVO;
import com.huicai.agency.dashboard.service.ServiceProgressService.WorkloadVO;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.service.AuditLogService;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 代理服务进度 + 工作量统计单元测试（P79）
 * <p>
 * BDD 场景映射（对齐 SPEC P79 场景 1/2/3/4/5/6）：
 * <ul>
 *   <li>场景1 事件推进 → advance_creates_done_row</li>
 *   <li>场景2 事件幂等 → advance_idempotent_when_done</li>
 *   <li>乱序忽略 → advance_out_of_order_ignored</li>
 *   <li>场景3 force-done 权限+审计 → force_done（经理成功 / 会计 403 / 缺 remark 400 / 已 DONE 409）</li>
 *   <li>场景4 超期预警 → overtime_excludes_done</li>
 *   <li>场景5 工作量 USER 维度 → workload_user_dimension</li>
 * </ul>
 * 说明：advanceStage 不碰 SecurityContext（createdBy 交给 DB 默认），forceDone 角色由参数传入，
 * 故 mock 单测无需 Spring Security 上下文。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("代理服务进度与工作量统计单元测试")
class ServiceProgressServiceImplTest {

    @Mock private ServiceProgressMapper progressMapper;
    @Mock private EnterpriseMapper enterpriseMapper;
    @Mock private AgencyUserMapper agencyUserMapper;
    @Mock private AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    @Mock private UserMapper userMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ServiceProgressServiceImpl service;

    /** MyBatis-Plus lambda 元数据未随 Spring 上下文初始化，需手动补（对齐 P78 做法） */
    @BeforeAll
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void initLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), ServiceProgressEntity.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), AgencyUserEntity.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), UserEntity.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.huicai.agency.user.entity.AgencyUserEnterpriseEntity.class);
    }

    private ServiceProgressEntity row(long id, long agency, long ent, String period, String stage, String status) {
        ServiceProgressEntity r = new ServiceProgressEntity();
        r.setId(id);
        r.setAgencyId(agency);
        r.setEnterpriseId(ent);
        r.setPeriod(period);
        r.setStage(stage);
        r.setStatus(status);
        r.setDeleted(0);
        return r;
    }

    // ── 场景1：事件推进 → 惰性创建 DONE 行 ──

    @Test
    @DisplayName("场景1 事件推进：目标节点无行 → 惰性创建为 DONE")
    void advance_creates_done_row() {
        when(progressMapper.selectOne(any())).thenReturn(null); // INTAKE 无行

        boolean changed = service.advanceStage(9L, 101L, "202609", "INTAKE");

        assertTrue(changed, "无既有行时应惰性创建并返回 true");
        verify(progressMapper, times(1)).insert(any(ServiceProgressEntity.class));
        verify(progressMapper, never()).updateById(any(ServiceProgressEntity.class));
    }

    // ── 场景2：事件幂等（已 DONE 不重复推进） ──

    @Test
    @DisplayName("场景2 事件幂等：BOOKING 前置 INTAKE 已 DONE 且自身已 DONE → 不变更")
    void advance_idempotent_when_done() {
        // 第1次 selectOne = isStageDone(INTAKE) → 已 DONE；第2次 = findProgress(BOOKING) → 已 DONE
        when(progressMapper.selectOne(any()))
                .thenReturn(row(1, 9, 101, "202609", "INTAKE", "DONE"),
                         row(2, 9, 101, "202609", "BOOKING", "DONE"));

        boolean changed = service.advanceStage(9L, 101L, "202609", "BOOKING");

        assertFalse(changed, "已 DONE 节点重触发应幂等返回 false");
        verify(progressMapper, never()).insert(any(ServiceProgressEntity.class));
        verify(progressMapper, never()).updateById(any(ServiceProgressEntity.class));
    }

    // ── 乱序忽略（前序未 DONE） ──

    @Test
    @DisplayName("乱序事件：REVIEW 的前序 BOOKING 未 DONE → 忽略")
    void advance_out_of_order_ignored() {
        // isStageDone(BOOKING) → 第1次 selectOne 返回 BOOKING PENDING（未 DONE）
        when(progressMapper.selectOne(any()))
                .thenReturn(row(2, 9, 101, "202609", "BOOKING", "PENDING"));

        boolean changed = service.advanceStage(9L, 101L, "202609", "REVIEW");

        assertFalse(changed, "前序节点未 DONE 时乱序事件应忽略");
        verify(progressMapper, never()).insert(any(ServiceProgressEntity.class));
        verify(progressMapper, never()).updateById(any(ServiceProgressEntity.class));
    }

    // ── 场景3：force-done 权限 + 审计 ──

    @Test
    @DisplayName("场景3a force-done：经理成功，写审计 + 状态 DONE")
    void force_done_manager_ok() {
        ServiceProgressEntity inProg = row(10, 9, 101, "202609", "FILING", "IN_PROGRESS");
        when(progressMapper.selectById(10L)).thenReturn(inProg);

        service.forceDone(10L, 7L, "张经理", "MANAGER", "客户暂停经营");

        assertEquals("DONE", inProg.getStatus());
        assertNotNull(inProg.getFinishedAt());
        assertEquals("客户暂停经营", inProg.getRemark());
        verify(progressMapper).updateById(inProg);
        verify(auditLogService).recordStatusChange(eq("SERVICE_PROGRESS"), eq(10L),
                eq("status"), eq("IN_PROGRESS"), eq("DONE"));
    }

    @Test
    @DisplayName("场景3b force-done：会计/助理 → 403 P79_003")
    void force_done_accountant_denied() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.forceDone(10L, 8L, "王会计", "ACCOUNTANT", "备注"));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("P79_003"));
        // 角色拒绝在查库前，不碰 DB
        verify(progressMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("场景3c force-done：缺 remark → 400 P79_001")
    void force_done_missing_remark() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.forceDone(10L, 7L, "张经理", "MANAGER", "  "));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("P79_001"));
    }

    @Test
    @DisplayName("场景3d force-done：目标已 DONE → 409 P79_002（幂等拒绝）")
    void force_done_already_done() {
        when(progressMapper.selectById(11L)).thenReturn(row(11, 9, 101, "202609", "FILING", "DONE"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.forceDone(11L, 7L, "张经理", "MANAGER", "备注"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("P79_002"));
    }

    // ── 场景4：超期预警（DONE 不计） ──

    @Test
    @DisplayName("场景4 超期预警：due_date<今天 且 非 DONE 且未提醒才返回")
    void overtime_excludes_done() {
        ServiceProgressEntity overtime = row(1, 9, 101, "202609", "FILING", "IN_PROGRESS");
        overtime.setDueDate(LocalDate.now().minusDays(1));
        when(progressMapper.selectList(any())).thenReturn(List.of(overtime));
        when(enterpriseMapper.selectList(any())).thenReturn(List.of());
        when(agencyUserMapper.selectList(any())).thenReturn(List.of());

        List<ServiceProgressRowVO> out = service.listOvertime(9L, LocalDate.now());

        assertEquals(1, out.size());
        assertTrue(out.get(0).overtime());
        // 负向：wrapper 应排除 DONE（ne status DONE）+ isNull notified_at
    }

    // ── 场景5：工作量 USER 维度 ──

    @Test
    @DisplayName("场景5 工作量 USER 维度：assignedCustomers×4 为基数算完成率")
    void workload_user_dimension() {
        // 代理用户 5 → user 100，分配 2 客户
        AgencyUserEntity au = new AgencyUserEntity();
        au.setId(5L);
        au.setAgencyId(9L);
        au.setUserId(100L);
        au.setDeleted(0);
        when(agencyUserMapper.selectList(any())).thenReturn(List.of(au));
        UserEntity user = new UserEntity();
        user.setId(100L);
        user.setRealName("王会计");
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(agencyUserEnterpriseMapper.countByUserId(5L)).thenReturn(2);
        // 区间内该代理用户 4 节点：3 DONE + 1 IN_PROGRESS（assignedTo=5 经办）
        List<ServiceProgressEntity> rows4 = List.of(
                row(1, 9, 101, "202601", "INTAKE", "DONE"),
                row(2, 9, 101, "202601", "BOOKING", "DONE"),
                row(3, 9, 101, "202601", "REVIEW", "DONE"),
                row(4, 9, 101, "202601", "FILING", "IN_PROGRESS"));
        rows4.forEach(r -> r.setAssignedTo(5L));
        when(progressMapper.selectList(any())).thenReturn(rows4);
        when(enterpriseMapper.selectList(any())).thenReturn(List.of());

        WorkloadVO vo = service.getWorkload(9L, "202601", "202606", "USER");

        assertEquals(1, vo.rows().size());
        var r = vo.rows().get(0);
        assertEquals(5L, r.userId());
        assertEquals("王会计", r.userName());
        assertEquals(2, r.assignedCustomers());
        // 完成率 = 3 DONE / (2 客户 × 4 节点) = 0.375
        assertEquals(0, new java.math.BigDecimal("0.3750").compareTo(r.completionRate()));
        assertEquals(1, r.inProgress());
    }

    // ── 期间参数守卫 ──

    @Test
    @DisplayName("期间倒挂 periodFrom>periodTo → 400")
    void periodRange_invalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getWorkload(9L, "202606", "202601", "USER"));
        assertEquals(400, ex.getCode());
    }
}
