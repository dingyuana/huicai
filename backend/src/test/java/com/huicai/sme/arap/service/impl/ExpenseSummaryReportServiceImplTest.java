package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import com.huicai.sme.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.sme.arap.service.ExpenseSummaryReportService.ExpenseSummaryRowVO;
import com.huicai.sme.arap.service.ExpenseSummaryReportService.ExpenseSummaryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 费用汇总报表服务单元测试（P76）
 * <p>
 * BDD 场景映射：
 * <ul>
 *   <li>场景 1 部门维度聚合正确 → deptGrouping_correct</li>
 *   <li>场景 2 仅生效状态计入 → onlyApprovedVoucheredCounted</li>
 *   <li>场景 3 同比无上年数据返回 null → yoyNullWhenNoData</li>
 *   <li>场景 4 期间参数守卫 → periodParamsGuard</li>
 *   <li>场景 5 人均仅 DEPT 维度输出 → perCapitaDeptOnly</li>
 *   <li>场景 6 数据权限隔离（service 不手工注入 enterprise_id，依赖拦截器）→ noEnterpriseFilter</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("费用汇总报表服务单元测试")
class ExpenseSummaryReportServiceImplTest {

    @Mock private ExpenseReimbursementMapper expenseMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private EmployeeMapper employeeMapper;

    @InjectMocks
    private ExpenseSummaryReportServiceImpl service;

    @BeforeAll
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExpenseReimbursementEntity.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), EmployeeEntity.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeptEntity.class);
    }

    // ===== helpers =====

    private ExpenseReimbursementEntity exp(Long id, Long deptId, Long empId, String expenseType,
                                           String amount, String status, String approvedAtYm) {
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setId(id);
        e.setDeptId(deptId);
        e.setEmployeeId(empId);
        e.setExpenseType(expenseType);
        e.setAmount(new BigDecimal(amount));
        e.setStatus(status);
        // approvedAt 落在指定期间的 01 日（approvedAtYm 形如 "202601" → "2026-01-01T00:00:00"）
        String isoDate = approvedAtYm.substring(0, 4) + "-" + approvedAtYm.substring(4, 6) + "-01T00:00:00";
        e.setApprovedAt(LocalDateTime.parse(isoDate));
        e.setCreatedAt(LocalDateTime.parse(isoDate));
        return e;
    }

    private DeptEntity dept(Long id, String name) {
        DeptEntity d = new DeptEntity();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private EmployeeEntity emp(Long id, String name, Long deptId) {
        EmployeeEntity e = new EmployeeEntity();
        e.setId(id);
        e.setName(name);
        e.setDeptId(deptId);
        e.setIsActive(true);
        return e;
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
                () -> "expected " + expected + " but got " + actual);
    }

    private void mockActiveEmployees() {
        when(employeeMapper.selectList(any())).thenReturn(List.of());
    }

    // ===== 场景 1：部门维度聚合正确 =====

    @Test
    @DisplayName("场景1_部门维度聚合（研发部3单600，市场部2单1100，合计1700）")
    void deptGrouping_correct() {
        when(expenseMapper.selectList(any())).thenReturn(List.of(
                exp(1L, 1L, 10L, "差旅费", "100", "APPROVED", "202601"),
                exp(2L, 1L, 11L, "办公费", "200", "VOUCHERED", "202602"),
                exp(3L, 1L, 10L, "差旅费", "300", "APPROVED", "202606"),
                exp(4L, 2L, 12L, "通讯费", "500", "APPROVED", "202603"),
                exp(5L, 2L, 12L, "招待费", "600", "VOUCHERED", "202604")));
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "研发部"), dept(2L, "市场部")));
        mockActiveEmployees();

        ExpenseSummaryVO vo = service.getSummary("202601", "202606", "DEPT", false, false);

        assertEquals("DEPT", vo.groupBy());
        assertEquals(5, vo.totalCount());
        assertMoney(new BigDecimal("1700"), vo.totalAmount());
        assertEquals(2, vo.rows().size());
        // 按 deptId 排序：1 在前
        ExpenseSummaryRowVO r1 = vo.rows().stream().filter(r -> r.dimId() == 1L).findFirst().orElseThrow();
        ExpenseSummaryRowVO r2 = vo.rows().stream().filter(r -> r.dimId() == 2L).findFirst().orElseThrow();
        assertEquals("研发部", r1.dimName());
        assertEquals(3, r1.count());
        assertMoney(new BigDecimal("600"), r1.amount());
        assertEquals("市场部", r2.dimName());
        assertEquals(2, r2.count());
        assertMoney(new BigDecimal("1100"), r2.amount());
    }

    // ===== 场景 2：仅生效状态计入（DRAFT/SUBMITTED/REJECTED 排除） =====

    @Test
    @DisplayName("场景2_仅APPROVED/VOUCHERED计入（DRAFT/SUBMITTED/REJECTED排除）")
    void onlyApprovedVoucheredCounted() {
        when(expenseMapper.selectList(any())).thenReturn(List.of(
                exp(1L, 1L, 10L, "差旅费", "100", "APPROVED", "202601"),
                exp(2L, 1L, 10L, "办公费", "200", "VOUCHERED", "202602"),
                exp(3L, 1L, 10L, "差旅费", "300", "DRAFT", "202603"),
                exp(4L, 1L, 10L, "通讯费", "400", "SUBMITTED", "202604"),
                exp(5L, 1L, 10L, "招待费", "500", "REJECTED", "202605")));
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "研发部")));
        mockActiveEmployees();

        ExpenseSummaryVO vo = service.getSummary("202601", "202606", "DEPT", false, false);

        assertEquals(1, vo.rows().size());
        ExpenseSummaryRowVO row = vo.rows().get(0);
        assertEquals(2, row.count(), "仅 2 张生效单计入");
        assertMoney(new BigDecimal("300"), row.amount());
    }

    // ===== 场景 3：同比无上年数据返回 null =====

    @Test
    @DisplayName("场景3_同比无上年数据返回null（环比正常）")
    void yoyNullWhenNoData() {
        // main: 202601 一单 100；yoy(202501) 无数据；mom 上一区间有数据
        when(expenseMapper.selectList(any()))
                .thenReturn(List.of(exp(1L, 1L, 10L, "差旅费", "100", "APPROVED", "202601")))  // main
                .thenReturn(List.of())                                                      // yoy (202501-202506)
                .thenReturn(List.of(exp(2L, 1L, 10L, "差旅费", "50", "APPROVED", "202512")));  // mom (202512)
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "研发部")));
        mockActiveEmployees();

        ExpenseSummaryVO vo = service.getSummary("202601", "202601", "DEPT", true, true);

        ExpenseSummaryRowVO row = vo.rows().get(0);
        assertNull(row.amountYoy(), "去年同期无数据 → amountYoy=null");
        assertNotNull(row.amountMom(), "上月有数据 → amountMom 非 null");
        assertMoney(new BigDecimal("50"), row.amountMom());
    }

    // ===== 场景 4：期间参数守卫 =====

    @Test
    @DisplayName("场景4_期间/维度参数守卫（from>to、非法格式、非法groupBy均400）")
    void periodParamsGuard() {
        BusinessException badRange = assertThrows(BusinessException.class,
                () -> service.getSummary("202606", "202601", "DEPT", null, null));
        assertEquals(400, badRange.getCode());
        assertTrue(badRange.getMessage().contains("period_from"));

        BusinessException badFormat = assertThrows(BusinessException.class,
                () -> service.getSummary("2026-01", "202606", "DEPT", null, null));
        assertEquals(400, badFormat.getCode());
        assertTrue(badFormat.getMessage().contains("YYYYMM"));

        BusinessException badGroup = assertThrows(BusinessException.class,
                () -> service.getSummary("202601", "202606", "FOO", null, null));
        assertEquals(400, badGroup.getCode());
        assertTrue(badGroup.getMessage().contains("group_by"));
    }

    // ===== 场景 5：人均仅 DEPT 维度输出 =====

    @Test
    @DisplayName("场景5_人均仅DEPT维度输出（DEPT: amount/在职数；EXPENSE_TYPE: null）")
    void perCapitaDeptOnly() {
        // 研发部 10 名在职员工，金额 152000 → perCapita = 15200
        when(expenseMapper.selectList(any())).thenReturn(List.of(
                exp(1L, 1L, 10L, "差旅费", "52000", "APPROVED", "202601"),
                exp(2L, 1L, 11L, "差旅费", "100000", "VOUCHERED", "202602")));
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "研发部")));
        when(employeeMapper.selectList(any())).thenReturn(
                java.util.Arrays.asList(
                        emp(1L, "员工1", 1L), emp(2L, "员工2", 1L), emp(3L, "员工3", 1L),
                        emp(4L, "员工4", 1L), emp(5L, "员工5", 1L), emp(6L, "员工6", 1L),
                        emp(7L, "员工7", 1L), emp(8L, "员工8", 1L), emp(9L, "员工9", 1L),
                        emp(10L, "员工10", 1L)));

        ExpenseSummaryVO vo = service.getSummary("202601", "202606", "DEPT", false, false);
        ExpenseSummaryRowVO row = vo.rows().get(0);
        assertMoney(new BigDecimal("152000"), row.amount());
        assertMoney(new BigDecimal("15200.00"), row.perCapita());

        // EXPENSE_TYPE 维度 perCapita 应为 null
        when(expenseMapper.selectList(any())).thenReturn(List.of(
                exp(1L, 1L, 10L, "差旅费", "52000", "APPROVED", "202601")));
        ExpenseSummaryVO vo2 = service.getSummary("202601", "202606", "EXPENSE_TYPE", false, false);
        assertTrue(vo2.rows().get(0).perCapita() == null, "EXPENSE_TYPE 维度不输出人均");
    }

    // ===== 场景 6：数据权限隔离（service 不手工注入 enterprise_id） =====

    @Test
    @DisplayName("场景6_查询不手工注入enterprise_id（数据权限由拦截器自动注入）")
    void noEnterpriseFilter() {
        when(expenseMapper.selectList(any())).thenReturn(List.of(
                exp(1L, 1L, 10L, "差旅费", "100", "APPROVED", "202601")));
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "研发部")));
        mockActiveEmployees();

        ExpenseSummaryVO vo = service.getSummary("202601", "202601", "DEPT", false, false);
        assertEquals(1, vo.rows().size());
        assertMoney(new BigDecimal("100"), vo.rows().get(0).amount());

        // 核心断言：service 层查询不手工注入 enterprise_id（数据权限由拦截器在 SQL 层补）
        ArgumentCaptor<LambdaQueryWrapper<ExpenseReimbursementEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(expenseMapper, atLeastOnce()).selectList(captor.capture());
        String sql = captor.getAllValues().stream()
                .map(LambdaQueryWrapper::getSqlSegment)
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(sql.contains("status"), "应含 status 生效过滤条件: " + sql);
        assertFalse(sql.contains("enterprise_id"),
                "service 不应手工注入 enterprise_id（由拦截器自动注入）: " + sql);
        // includeYoy/Mom=false 时只查 main 一次，不查 yoy/mom 历史区间
        verify(expenseMapper, times(1)).selectList(any());
    }
}
