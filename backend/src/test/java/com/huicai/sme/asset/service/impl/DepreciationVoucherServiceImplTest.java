package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.dto.DepreciationVoucherResult;
import com.huicai.sme.asset.entity.AssetDepreciationEntity;
import com.huicai.sme.asset.mapper.AssetDepreciationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DepreciationVoucherServiceImpl 单元测试（P85-C 折旧自动制证）.
 *
 * 覆盖：
 * <ul>
 *   <li>正常路径：两行分录借贷平衡、凭证号固定 DEPR-{period}、凭证落 DRAFT</li>
 *   <li>溯源回写：t_asset_depreciation.voucher_id 回填（已回写的跳过）</li>
 *   <li>全部异常分支：期间未配置/已结账/幂等拒绝/无折旧数据/金额为空/金额非正/科目未配置</li>
 * </ul>
 *
 * 铁律验证：全程不出现自动审核/自动过账调用——本服务只生成 DRAFT 凭证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepreciationVoucherServiceImplTest {

    private static final String PERIOD = "202608";
    private static final Long USER_ID = 7L;
    private static final String EXPENSE_CODE = "6602";
    private static final String ACCUM_CODE = "1602";

    @Mock private AssetDepreciationMapper depreciationMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private PeriodService periodService;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;

    private DepreciationVoucherServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DepreciationVoucherServiceImpl(depreciationMapper, subjectMapper,
                periodService, voucherMapper, voucherEntryMapper);
    }

    // ==================== stub helpers ====================

    private void stubOpenPeriod() {
        PeriodEntity p = new PeriodEntity();
        p.setId(1L);
        p.setPeriodCode(PERIOD);
        p.setStatus("open");
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(p);
    }

    private Subject subjectOf(String code, Long id) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        s.setName(code.equals(EXPENSE_CODE) ? "管理费用" : "累计折旧");
        return s;
    }

    /**
     * 按调用顺序返回两个科目（借方 6602 → 贷方 1602）。
     * 注意：不能用 any(Wrapper) 两次 thenReturn 覆盖——第二次会吞掉第一次的 stub，
     * 导致两次查询都返回累计折旧科目。用 thenReturn(a).thenReturn(b) 按调用次序分发。
     */
    private void stubSubjects() {
        when(subjectMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(subjectOf(EXPENSE_CODE, 6602L))
                .thenReturn(subjectOf(ACCUM_CODE, 1602L));
    }

    private void stubNoExistingVoucher() {
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    }

    private AssetDepreciationEntity depRow(Long id, String amount, Long voucherId) {
        AssetDepreciationEntity r = new AssetDepreciationEntity();
        r.setId(id);
        r.setPeriod(PERIOD);
        r.setDepreciationAmount(amount == null ? null : new BigDecimal(amount));
        r.setVoucherId(voucherId);
        return r;
    }

    private void stubDepreciationRows(List<AssetDepreciationEntity> rows) {
        when(depreciationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rows);
    }

    // ==================== 正常路径 ====================

    @Nested
    @DisplayName("正常制证")
    class HappyPath {

        @Test
        @DisplayName("两行分录借贷平衡，凭证号固定 DEPR-{period}，状态 DRAFT")
        void generate_createsBalancedDraftVoucher() {
            stubOpenPeriod();
            stubNoExistingVoucher();
            stubDepreciationRows(Arrays.asList(
                    depRow(101L, "1200.00", null),
                    depRow(102L, "300.00", null),
                    depRow(103L, "800.00", null)));
            stubSubjects();
            when(voucherMapper.insert(any(VoucherEntity.class))).thenReturn(1);
            when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);

            DepreciationVoucherResult result = service.generate(PERIOD, USER_ID);

            assertEquals("DEPR-202608", result.voucherNo());
            assertEquals(new BigDecimal("2300.00"), result.totalDebit());
            assertEquals(new BigDecimal("2300.00"), result.totalCredit());
            assertEquals(3, result.detailCount());
            assertEquals("DRAFT", result.status());

            // 凭证头：期间/类型/来源/操作者
            ArgumentCaptor<VoucherEntity> vc = ArgumentCaptor.forClass(VoucherEntity.class);
            verify(voucherMapper).insert(vc.capture());
            VoucherEntity voucher = vc.getValue();
            assertEquals(PERIOD, voucher.getPeriod());
            assertEquals("DRAFT", voucher.getStatus());
            assertEquals("GENERATED", voucher.getSource());
            assertEquals(USER_ID, voucher.getCreatedBy());
            assertEquals(new BigDecimal("2300.00"), voucher.getTotalDebit());
            assertEquals(new BigDecimal("2300.00"), voucher.getTotalCredit());
            assertTrue(voucher.getSummary().contains(PERIOD));

            // 两行分录：借管理费用 / 贷累计折旧，金额一致
            org.mockito.ArgumentCaptor<VoucherEntryEntity> lineCap =
                    org.mockito.ArgumentCaptor.forClass(VoucherEntryEntity.class);
            verify(voucherEntryMapper, org.mockito.Mockito.times(2)).insert(lineCap.capture());
            List<VoucherEntryEntity> lines = lineCap.getAllValues();
            assertEquals(6602L, lines.get(0).getSubjectId());
            assertEquals(new BigDecimal("2300.00"), lines.get(0).getDebit());
            assertEquals(BigDecimal.ZERO, lines.get(0).getCredit());
            assertEquals(1602L, lines.get(1).getSubjectId());
            assertEquals(BigDecimal.ZERO, lines.get(1).getDebit());
            assertEquals(new BigDecimal("2300.00"), lines.get(1).getCredit());
        }

        @Test
        @DisplayName("溯源回写：voucher_id 为空的行回填，已回填的行跳过")
        void generate_backfillsVoucherIdOnlyWhenNull() {
            stubOpenPeriod();
            stubNoExistingVoucher();
            stubDepreciationRows(Arrays.asList(
                    depRow(101L, "100.00", null),
                    depRow(102L, "50.00", 999L)));
            stubSubjects();
            when(voucherMapper.insert(any(VoucherEntity.class))).thenReturn(1);
            when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);

            service.generate(PERIOD, USER_ID);

            // 只有 voucher_id 为空的第 1 行被回写；已有 voucher_id=999 的第 2 行不重复更新
            ArgumentCaptor<AssetDepreciationEntity> cap =
                    ArgumentCaptor.forClass(AssetDepreciationEntity.class);
            verify(depreciationMapper).updateById(cap.capture());
            assertEquals(101L, cap.getValue().getId());
            // 回写值应为本凭证ID（insert 回填后 voucher.getId()，此处为 1 以外的真实ID）
            assertEquals(1, cap.getAllValues().size());
        }
    }

    // ==================== 异常分支 ====================

    @Nested
    @DisplayName("前置校验失败（不产生任何写入）")
    class PreconditionFailures {

        @Test
        @DisplayName("期间未配置 → 拒绝")
        void periodNotFound_throws() {
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("未配置"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }

        @Test
        @DisplayName("期间已结账 → 拒绝（守 P87 期间锁）")
        void closedPeriod_throws() {
            PeriodEntity p = new PeriodEntity();
            p.setPeriodCode(PERIOD);
            p.setStatus("closed");
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(p);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("已结账"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }

        @Test
        @DisplayName("已存在 DEPR 凭证 → 幂等拒绝，且不覆盖旧凭证")
        void duplicateVoucher_throws() {
            stubOpenPeriod();
            when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("已存在"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
            verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        }

        @Test
        @DisplayName("当期无折旧数据 → 提示先去计提，不生成空凭证")
        void noDepreciationRecords_throws() {
            stubOpenPeriod();
            stubNoExistingVoucher();
            stubDepreciationRows(Collections.emptyList());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("折旧数据"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }
    }

    @Nested
    @DisplayName("数据质量校验失败")
    class DataQualityFailures {

        @Test
        @DisplayName("折旧金额为空 → 拒绝并指向具体记录")
        void nullAmount_throws() {
            stubOpenPeriod();
            stubNoExistingVoucher();
            stubDepreciationRows(Collections.singletonList(depRow(201L, null, null)));
            stubSubjects();
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("201"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }

        @Test
        @DisplayName("折旧金额非正 → 拒绝（防零元/负数凭证）")
        void nonPositiveAmount_throws() {
            stubOpenPeriod();
            stubNoExistingVoucher();
            stubDepreciationRows(Collections.singletonList(depRow(202L, "0", null)));
            stubSubjects();
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("非正"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }

        @Test
        @DisplayName("累计折旧科目未配置 → 拒绝（不做降级假设）")
        void missingSubject_throws() {
            stubOpenPeriod();
            stubNoExistingVoucher();
            stubDepreciationRows(Collections.singletonList(depRow(203L, "100.00", null)));
            when(subjectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(PERIOD, USER_ID));
            assertTrue(ex.getMessage().contains("未配置科目"));
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }
    }
}
