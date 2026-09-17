package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.entity.AssetDepreciationEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import com.huicai.sme.asset.mapper.AssetDepreciationMapper;
import com.huicai.sme.asset.service.AssetReportService.AssetCategorySummaryRowVO;
import com.huicai.sme.asset.service.AssetReportService.AssetCategorySummaryVO;
import com.huicai.sme.asset.service.AssetReportService.AssetDepreciationSummaryVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 折旧与资产统计报表单元测试（P77）
 * <p>
 * BDD 场景映射：
 * <ul>
 *   <li>场景 1 分类汇总 → categorySummary_groupsAndTotals</li>
 *   <li>场景 2 恒等式成立 → depreciationSummary_consistent</li>
 *   <li>场景 3 恒等式破坏 → depreciationSummary_inconsistent_flagged</li>
 *   <li>场景 4 期间必填/格式守卫 → periodRequired_throws400</li>
 *   <li>场景 5 区间倒挂 → rangeOrder_throws400</li>
 *   <li>附加：groupBy 非法守卫 → groupByIllegal_throws400</li>
 *   <li>附加：数据权限隔离（service 不手工注入 enterprise_id）→ wrapper_noEnterpriseFilter</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("折旧与资产统计报表单元测试")
class AssetReportServiceImplTest {

    @Mock private AssetCardMapper cardMapper;
    @Mock private AssetDepreciationMapper depreciationMapper;
    @Mock private AssetCategoryMapper categoryMapper;
    @Mock private DeptMapper deptMapper;

    @InjectMocks
    private AssetReportServiceImpl service;

    /** 单跑本测试类时 MyBatis-Plus lambda 元数据未随 Spring 上下文初始化，需手动补（对齐 P78 做法） */
    @BeforeAll
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void initLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), AssetCardEntity.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), AssetDepreciationEntity.class);
    }

    // ── helpers ──

    private AssetCardEntity card(long id, long cat, long dept, String status,
                                 String original, String acc, String net) {
        AssetCardEntity c = new AssetCardEntity();
        c.setId(id);
        c.setCategoryId(cat);
        c.setDeptId(dept);
        c.setStatus(status);
        c.setOriginalValue(bd(original));
        c.setAccumulatedDepreciation(bd(acc));
        c.setNetValue(bd(net));
        return c;
    }

    private AssetDepreciationEntity dep(long id, long asset, String period, String amount, String acc) {
        AssetDepreciationEntity d = new AssetDepreciationEntity();
        d.setId(id);
        d.setAssetId(asset);
        d.setPeriod(period);
        d.setDepreciationAmount(bd(amount));
        d.setAccumulatedDepreciation(bd(acc));
        return d;
    }

    private AssetCategoryEntity cat(long id, String name) {
        AssetCategoryEntity c = new AssetCategoryEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private DeptEntity dept(long id, String name) {
        DeptEntity d = new DeptEntity();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private BigDecimal bd(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    // ── 场景 1：分类汇总 ──

    @Test
    @DisplayName("场景1 分类汇总：类别分组 + 数量/原值/净值/本期已提 + 净值率")
    void categorySummary_groupsAndTotals() {
        // 有效卡片：cat1(A1 IN_USE) + cat2(A2 IDLE)；A3 DISPOSED 须被剔除
        when(cardMapper.selectList(any())).thenReturn(List.of(
                card(1, 1, 10, "IN_USE", "1000", "400", "600"),
                card(2, 2, 20, "IDLE", "5000", "2000", "3000"),
                card(3, 1, 10, "DISPOSED", "999", "999", "0")));
        when(categoryMapper.selectList(null)).thenReturn(List.of(cat(1, "设备"), cat(2, "车辆")));
        // 本期(202606)已提：A1=100, A2=50
        when(depreciationMapper.selectList(any())).thenReturn(List.of(
                dep(1, 1, "202606", "100", "400"),
                dep(2, 2, "202606", "50", "2000")));

        AssetCategorySummaryVO vo = service.getCategorySummary("202606", null);

        assertEquals(2, vo.totalQty(), "DISPOSED 卡片应被剔除，totalQty=2");
        assertMoney("6000", vo.totalOriginalValue());
        assertMoney("3600", vo.totalNetValue());
        assertEquals(2, vo.rows().size());
        // 按原值降序：cat2(5000) 在前
        AssetCategorySummaryRowVO first = vo.rows().get(0);
        assertEquals(2L, first.categoryId());
        assertEquals("车辆", first.categoryName());
        assertEquals(1, first.qty());
        assertMoney("5000", first.originalValue());
        assertMoney("3000", first.netValue());
        assertMoney("50", first.currentDepreciation());
        assertMoney("0.6000", first.netRatio());
    }

    // ── 场景 2：恒等式成立 ──

    @Test
    @DisplayName("场景2 计提汇总：期初+区间计提=期末 → consistent=true")
    void depreciationSummary_consistent() {
        when(cardMapper.selectList(any())).thenReturn(List.of(
                card(1, 1, 10, "IN_USE", "1000", "450", "550"),
                card(2, 2, 20, "IN_USE", "5000", "230", "4770")));
        when(categoryMapper.selectList(null)).thenReturn(List.of(cat(1, "设备"), cat(2, "车辆")));
        when(deptMapper.selectList(null)).thenReturn(List.of(dept(10, "研发部"), dept(20, "行政部")));
        when(depreciationMapper.selectList(any())).thenReturn(List.of(
                // A1：期初 202512 acc=300；区间 202603(100)/202606(50)；期末 202606 acc=450 → 300+150=450 ✓
                dep(1, 1, "202512", null, "300"),
                dep(2, 1, "202603", "100", "400"),
                dep(3, 1, "202606", "50", "450"),
                // A2：无期初；区间 202602(200)/202606(30)；期末 202606 acc=230 → 0+230=230 ✓
                dep(4, 2, "202602", "200", "200"),
                dep(5, 2, "202606", "30", "230")));

        AssetDepreciationSummaryVO vo =
                service.getDepreciationSummary("202601", "202606", "CATEGORY");

        assertTrue(vo.consistent(), "两组资产恒等式均成立，consistent 应为 true");
        assertMoney("380", vo.totalDepreciated());
        assertEquals(2, vo.rows().size());
        // 按区间计提降序：cat2(230=200+30) 在前，cat1(150=100+50) 在后
        assertEquals("2", vo.rows().get(0).dimKey());
        assertMoney("230", vo.rows().get(0).depreciated());
        assertMoney("0", vo.rows().get(0).openingAccumulated());
        assertMoney("230", vo.rows().get(0).closingAccumulated());
        assertEquals("1", vo.rows().get(1).dimKey());
        assertMoney("150", vo.rows().get(1).depreciated());
        assertMoney("300", vo.rows().get(1).openingAccumulated());
        assertMoney("450", vo.rows().get(1).closingAccumulated());
    }

    // ── 场景 3：恒等式破坏 ──

    @Test
    @DisplayName("场景3 计提汇总：期末累计与 期初+计提 不符 → consistent=false（不阻断）")
    void depreciationSummary_inconsistent_flagged() {
        when(cardMapper.selectList(any())).thenReturn(List.of(
                card(1, 1, 10, "IN_USE", "1000", "460", "540")));
        when(categoryMapper.selectList(null)).thenReturn(List.of(cat(1, "设备")));
        when(deptMapper.selectList(null)).thenReturn(List.of(dept(10, "研发部")));
        // A1：期初 300 + 区间计提 150 = 450，但期末累计 460 → 不一致
        when(depreciationMapper.selectList(any())).thenReturn(List.of(
                dep(1, 1, "202512", null, "300"),
                dep(2, 1, "202603", "100", "400"),
                dep(3, 1, "202606", "50", "460")));

        AssetDepreciationSummaryVO vo =
                service.getDepreciationSummary("202601", "202606", "CATEGORY");

        assertFalse(vo.consistent(), "期初(300)+计提(150)=450 ≠ 期末(460)，应标 consistent=false");
        // 不阻断：rows 仍完整返回
        assertEquals(1, vo.rows().size());
        assertMoney("450", vo.rows().get(0).openingAccumulated().add(vo.rows().get(0).depreciated()));
    }

    // ── 场景 4：期间格式守卫 ──

    @Test
    @DisplayName("场景4 期间非 6 位数字 → 400")
    void periodRequired_throws400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getCategorySummary("2026", null));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("P77_001"));

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> service.getCategorySummary(null, null));
        assertTrue(ex2.getMessage().contains("P77_001"));
    }

    // ── 场景 5：区间倒挂 ──

    @Test
    @DisplayName("场景5 periodFrom > periodTo → 400")
    void rangeOrder_throws400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDepreciationSummary("202606", "202601", "CATEGORY"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("P77_004"));
    }

    // ── 附加：groupBy 非法 ──

    @Test
    @DisplayName("groupBy 非法值 → 400")
    void groupByIllegal_throws400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDepreciationSummary("202601", "202606", "FOO"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("P77_003"));
    }

    // ── 附加：数据权限隔离 ──

    @Test
    @DisplayName("service 不手工注入 enterprise_id（依赖 EnterpriseDataPermissionInterceptor）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void wrapper_noEnterpriseFilter() {
        when(cardMapper.selectList(any())).thenReturn(List.of());
        service.getCategorySummary("202606", null);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cardMapper, atLeastOnce()).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        // 仅含状态负向过滤（dual-defense），绝不手工写 enterprise_id —— 隔离交给拦截器
        assertTrue(sql.contains("status"), "wrapper 应含 status 负向过滤");
        assertFalse(sql.contains("enterprise_id"), "service 不得手工注入 enterprise_id");
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual, "金额不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + " 实际 " + actual);
    }
}
