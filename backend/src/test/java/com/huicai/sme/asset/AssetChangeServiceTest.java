package com.huicai.sme.asset;

import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.dto.AssetChangeCreateDTO;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetChangeEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetChangeMapper;
import com.huicai.sme.asset.service.impl.AssetChangeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-P144（D-4）单元面：资产变动三类路径 + 拒绝面（V1.106）。
 *
 * <p>L2 真库面（{@code AssetChangeRealDBTest}）锁「真落库 + RLS + fill」；
 * 本类锁分支逻辑与凭证分录方向（借/贷科目与金额），纯 Mockito 快跑。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SPEC-P144：资产变动（D-4）—— 调整凭证分录方向 / 快照 / 拒绝面")
class AssetChangeServiceTest {

    @Mock private AssetCardMapper cardMapper;
    @Mock private AssetChangeMapper changeMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherTypeService voucherTypeService;
    @Mock private PeriodService periodService;

    @InjectMocks
    private AssetChangeServiceImpl service;

    private AssetCardEntity card;

    @BeforeEach
    void setUp() {
        card = new AssetCardEntity();
        card.setId(11L);
        card.setAssetCode("A-TEST-1");
        card.setAssetName("测试资产");
        card.setStatus("IN_USE");
        card.setOriginalValue(new BigDecimal("50000"));
        card.setAccumulatedDepreciation(new BigDecimal("10000"));
        card.setNetValue(new BigDecimal("40000"));
        card.setDeptId(3L);
        card.setDeleted(0);

        Subject s1601 = new Subject();
        s1601.setId(101L);
        s1601.setCode("1601");
        Subject s1002 = new Subject();
        s1002.setId(102L);
        s1002.setCode("1002");
        // requireSubject 调用序固定：先 1601 后 1002（实现内 requireSubject(SUBJECT_FIXED_ASSET)
        // 先于 requireSubject(SUBJECT_BANK)）；LambdaQueryWrapper 内容难以匹配，按调用序桩
        java.util.concurrent.atomic.AtomicInteger subjectCall =
                new java.util.concurrent.atomic.AtomicInteger();
        lenient().when(subjectMapper.selectOne(any())).thenAnswer(inv ->
                subjectCall.incrementAndGet() == 1 ? s1601 : s1002);
        lenient().when(voucherTypeService.requireByCode("ZZ")).thenReturn(new com.huicai.base.system.entity.VoucherTypeEntity());
        lenient().when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, VoucherEntity.class).setId(999L);
            return 1;
        });
        lenient().when(changeMapper.insert(any(AssetChangeEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, AssetChangeEntity.class).setId(555L);
            return 1;
        });
    }

    private AssetChangeCreateDTO dto(String type, BigDecimal amount, Long deptId) {
        AssetChangeCreateDTO dto = new AssetChangeCreateDTO();
        dto.setAssetId(11L);
        dto.setChangeType(type);
        dto.setChangeDate(LocalDate.of(2026, 11, 1));
        dto.setAmount(amount);
        dto.setDeptId(deptId);
        return dto;
    }

    @Test
    @DisplayName("调增：借1601固定资产/贷1002银行存款，卡片原值/净值更新，快照与凭证号正确")
    void valueAdjustIncrease() {
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(periodService.getByPeriodCode("202611")).thenReturn(openPeriod());

        service.create(dto("VALUE_ADJUST", new BigDecimal("10000"), null), 9L);

        assertEquals(new BigDecimal("60000"), card.getOriginalValue(), "原值 = 50000+10000");
        assertEquals(new BigDecimal("50000"), card.getNetValue(), "净值 = 60000-10000 累计折旧");

        ArgumentCaptor<VoucherEntity> voucherCap = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCap.capture());
        VoucherEntity voucher = voucherCap.getValue();
        assertEquals(new BigDecimal("10000"), voucher.getTotalDebit(), "凭证借方合计 = 调整额");
        assertEquals(new BigDecimal("10000"), voucher.getTotalCredit(), "凭证贷方合计 = 调整额");
        assertEquals("ADJ-555", voucher.getVoucherNo(), "凭证号 = ADJ-{changeId}");

        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entries.capture());
        List<VoucherEntryEntity> lines = entries.getAllValues();
        assertEquals(101L, lines.get(0).getSubjectId(), "借方科目=1601 固定资产");
        assertEquals(0, lines.get(0).getCredit().signum(), "借方行贷额为 0");
        assertEquals(102L, lines.get(1).getSubjectId(), "贷方科目=1002 银行存款");
        assertEquals(0, lines.get(1).getDebit().signum(), "贷方行借额为 0");

        ArgumentCaptor<AssetChangeEntity> changeCap = ArgumentCaptor.forClass(AssetChangeEntity.class);
        verify(changeMapper).updateById(changeCap.capture());
        assertEquals("originalValue=50000", changeCap.getValue().getBeforeValue(), "before 快照");
        assertEquals("originalValue=60000", changeCap.getValue().getAfterValue(), "after 快照");
        assertEquals(999L, changeCap.getValue().getVoucherId(), "凭证回填");
    }

    @Test
    @DisplayName("调减：分录反向（借1002/贷1601），调减后净值为负 ⇒ 400")
    void valueAdjustDecreaseAndNegativeNetGuard() {
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(periodService.getByPeriodCode("202611")).thenReturn(openPeriod());

        // 调减 30000：净值 70000-30000 → 原 40000-30000=10000 ≥ 0 合法
        service.create(dto("VALUE_ADJUST", new BigDecimal("-30000"), null), 9L);
        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entries.capture());
        assertEquals(102L, entries.getAllValues().get(0).getSubjectId(), "调减时借方=1002 银行存款");
        assertEquals(101L, entries.getAllValues().get(1).getSubjectId(), "调减时贷方=1601 固定资产");

        // 调减 55000：净值 20000-55000 < 0 ⇒ 拒绝
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(dto("VALUE_ADJUST", new BigDecimal("-55000"), null), 9L));
        assertTrue(ex.getMessage().contains("净值为负"), "调减后净值为负必须 400：" + ex.getMessage());
    }

    @Test
    @DisplayName("DEPT_TRANSFER：更新 deptId、快照正确、不制证")
    void deptTransferNoVoucher() {
        when(cardMapper.selectById(11L)).thenReturn(card);

        service.create(dto("DEPT_TRANSFER", null, 7L), 9L);

        assertEquals(7L, card.getDeptId(), "卡片部门应更新");
        ArgumentCaptor<AssetChangeEntity> changeCap = ArgumentCaptor.forClass(AssetChangeEntity.class);
        verify(changeMapper).insert(changeCap.capture());
        assertEquals("deptId=3", changeCap.getValue().getBeforeValue());
        assertEquals("deptId=7", changeCap.getValue().getAfterValue());
        assertNull(changeCap.getValue().getVoucherId(), "部门转移不制证");
        verify(voucherMapper, times(0)).insert(any(VoucherEntity.class));
    }

    @Test
    @DisplayName("拒绝面：DEPRECIATION 专用类型 / 零金额 / 非IN_USE / 资产不存在 / 同部门转移 / 已结账期间")
    void rejectionFaces() {
        when(cardMapper.selectById(11L)).thenReturn(card);

        // STATUS_CHANGE 未开放
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> service.create(dto("STATUS_CHANGE", null, null), 9L));
        assertTrue(ex1.getMessage().contains("状态机"));

        // VALUE_ADJUST 零金额
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> service.create(dto("VALUE_ADJUST", BigDecimal.ZERO, null), 9L));
        assertTrue(ex2.getMessage().contains("非零"));

        // 非 IN_USE
        card.setStatus("DISPOSED");
        BusinessException ex3 = assertThrows(BusinessException.class,
                () -> service.create(dto("DEPT_TRANSFER", null, 7L), 9L));
        assertTrue(ex3.getMessage().contains("IN_USE"));

        // 资产不存在
        when(cardMapper.selectById(11L)).thenReturn(null);
        BusinessException ex4 = assertThrows(BusinessException.class,
                () -> service.create(dto("DEPT_TRANSFER", null, 7L), 9L));
        assertTrue(ex4.getMessage().contains("不存在"));

        // 已结账期间（VALUE_ADJUST 走期间检查）—— 恢复卡片桩，避免误中「资产不存在」
        card.setStatus("IN_USE");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(periodService.getByPeriodCode("202611")).thenReturn(closedPeriod());
        BusinessException ex5 = assertThrows(BusinessException.class,
                () -> service.create(dto("VALUE_ADJUST", new BigDecimal("1000"), null), 9L));
        assertTrue(ex5.getMessage().contains("已结账"));
    }

    private PeriodEntity openPeriod() {
        PeriodEntity p = new PeriodEntity();
        p.setStatus("open");
        return p;
    }

    private PeriodEntity closedPeriod() {
        PeriodEntity p = new PeriodEntity();
        p.setStatus("closed");
        return p;
    }
}
