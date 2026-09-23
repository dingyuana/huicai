package com.huicai.sme.periodclose.service.impl;

import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.base.voucher.dto.SequenceVoucherVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.dto.DepreciationVoucherResult;
import com.huicai.sme.asset.service.DepreciationVoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CarryoverSequenceServiceImpl 单元测试.
 *
 * <p>核心验证点：单步失败<b>不中断</b>序列——结果始终包含三步，
 * 让操作员看到完整状态而非中断在半路。
 */
@ExtendWith(MockitoExtension.class)
class CarryoverSequenceServiceImplTest {

    @Mock private DepreciationVoucherService depreciationVoucherService;
    @Mock private PeriodCloseService periodCloseService;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherTypeService voucherTypeService;

    private CarryoverSequenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CarryoverSequenceServiceImpl(
                depreciationVoucherService, periodCloseService,
                voucherMapper, voucherEntryMapper, voucherTypeService);
    }

    /** 造一张凭证实体（含类型 id）。 */
    private VoucherEntity voucher(long id, long typeId, String no, String status) {
        VoucherEntity e = new VoucherEntity();
        e.setId(id);
        e.setVoucherNo(no);
        e.setVoucherTypeId(typeId);
        e.setStatus(status);
        e.setTotalDebit(new BigDecimal("100.00"));
        e.setTotalCredit(new BigDecimal("100.00"));
        return e;
    }

    /** 让 toVO 能查到凭证 + 类型 + 分录。 */
    private void stubView(long voucherId, long typeId) {
        when(voucherMapper.selectById(voucherId))
                .thenReturn(voucher(voucherId, typeId, "NO-" + voucherId, "DRAFT"));
        VoucherTypeEntity type = new VoucherTypeEntity();
        type.setId(typeId);
        type.setName("TYPE-" + typeId);
        when(voucherTypeService.getById(typeId)).thenReturn(type);
        when(voucherEntryMapper.selectByVoucherId(voucherId)).thenReturn(List.of(new VoucherEntryEntity()));
    }

    private DepreciationVoucherResult deprResult(long id) {
        return new DepreciationVoucherResult(id, "DEPR-202609",
                new BigDecimal("100.00"), new BigDecimal("100.00"), 2, "DRAFT");
    }

    @Test
    @DisplayName("三步全部成功: 返回 3 条 GENERATED, 顺序 DEPR→CLOSE→DISTRIB")
    void generateSequence_allSuccess_threeGenerated() {
        when(depreciationVoucherService.generate(eq("202609"), anyLong())).thenReturn(deprResult(101L));
        when(periodCloseService.generateProfitCarryOver(eq("202609"), anyLong())).thenReturn(102L);
        when(periodCloseService.generateProfitDistribution(eq("202609"), anyLong())).thenReturn(103L);
        stubView(101L, 1L);
        stubView(102L, 2L);
        stubView(103L, 3L);

        List<CarryoverStepResult> results = service.generateSequence("202609", 1L);

        assertEquals(3, results.size());
        assertArrayEquals(
                new String[]{"DEPR", "CLOSE", "DISTRIB"},
                results.stream().map(CarryoverStepResult::step).toArray(String[]::new));
        results.forEach(r -> {
            assertEquals(CarryoverStepResult.STATUS_GENERATED, r.status());
            assertEquals(1, r.vouchers().size());
        });
    }

    @Test
    @DisplayName("DEPR 抛业务异常(无折旧数据): 记 SKIPPED, 不中断后续两步")
    void generateSequence_deprSkipped_continuesOtherSteps() {
        // DEPR 抛业务异常 → 记 SKIPPED
        doThrow(new BusinessException("当期无折旧计提数据"))
                .when(depreciationVoucherService).generate(eq("202609"), anyLong());
        when(periodCloseService.generateProfitCarryOver(eq("202609"), anyLong())).thenReturn(102L);
        when(periodCloseService.generateProfitDistribution(eq("202609"), anyLong())).thenReturn(103L);
        stubView(102L, 2L);
        stubView(103L, 3L);

        List<CarryoverStepResult> results = service.generateSequence("202609", 1L);

        assertEquals(3, results.size());
        assertEquals(CarryoverStepResult.STATUS_SKIPPED, results.get(0).status());
        assertTrue(results.get(0).reason().contains("无折旧"));
        assertNull(results.get(0).voucherId());
        // 关键: 后续两步仍成功
        assertEquals(CarryoverStepResult.STATUS_GENERATED, results.get(1).status());
        assertEquals(CarryoverStepResult.STATUS_GENERATED, results.get(2).status());
    }

    @Test
    @DisplayName("CLOSE 抛业务异常: 记 SKIPPED, DEPR 与 DISTRIB 仍成功")
    void generateSequence_closeSkipped_bothNeighborsSucceed() {
        when(depreciationVoucherService.generate(eq("202609"), anyLong())).thenReturn(deprResult(101L));
        doThrow(new BusinessException("期间 202609 无可结转的损益数据"))
                .when(periodCloseService).generateProfitCarryOver(eq("202609"), anyLong());
        when(periodCloseService.generateProfitDistribution(eq("202609"), anyLong())).thenReturn(103L);
        stubView(101L, 1L);
        stubView(103L, 3L);

        List<CarryoverStepResult> results = service.generateSequence("202609", 1L);

        assertEquals(CarryoverStepResult.STATUS_GENERATED, results.get(0).status());
        assertEquals(CarryoverStepResult.STATUS_SKIPPED, results.get(1).status());
        assertTrue(results.get(1).reason().contains("无可结转"));
        assertEquals(CarryoverStepResult.STATUS_GENERATED, results.get(2).status());
    }

    @Test
    @DisplayName("任一步抛非业务运行时异常: 记 FAILED 而非 SKIPPED, 且不中断")
    void generateSequence_runtimeError_recordsFailed() {
        when(depreciationVoucherService.generate(eq("202609"), anyLong())).thenReturn(deprResult(101L));
        // 模拟数据库等系统性故障, 非业务校验
        doThrow(new RuntimeException("数据库连接超时"))
                .when(periodCloseService).generateProfitCarryOver(eq("202609"), anyLong());
        when(periodCloseService.generateProfitDistribution(eq("202609"), anyLong())).thenReturn(103L);
        stubView(101L, 1L);
        stubView(103L, 3L);

        List<CarryoverStepResult> results = service.generateSequence("202609", 1L);

        assertEquals(CarryoverStepResult.STATUS_GENERATED, results.get(0).status());
        assertEquals(CarryoverStepResult.STATUS_FAILED, results.get(1).status());
        assertEquals("数据库连接超时", results.get(1).reason());
        assertEquals(CarryoverStepResult.STATUS_GENERATED, results.get(2).status());
    }

    @Test
    @DisplayName("三步全部失败: 仍返回 3 条结果(保证前端始终能渲染完整步骤条)")
    void generateSequence_allFail_stillReturnsThreeSteps() {
        doThrow(new BusinessException("无折旧")).when(depreciationVoucherService).generate(any(), anyLong());
        doThrow(new BusinessException("无损益")).when(periodCloseService)
                .generateProfitCarryOver(any(), anyLong());
        doThrow(new BusinessException("无利润")).when(periodCloseService)
                .generateProfitDistribution(any(), anyLong());

        List<CarryoverStepResult> results = service.generateSequence("202609", 1L);

        assertEquals(3, results.size());
        assertEquals(CarryoverStepResult.STATUS_SKIPPED, results.get(0).status());
        assertEquals(CarryoverStepResult.STATUS_SKIPPED, results.get(1).status());
        assertEquals(CarryoverStepResult.STATUS_SKIPPED, results.get(2).status());
        results.forEach(r -> assertNull(r.voucherId()));
    }

    @Test
    @DisplayName("getVoucherView: 返回完整卡片视图(类型名 + 分录行数)")
    void getVoucherView_returnsFullCard() {
        stubView(501L, 7L);

        SequenceVoucherVO vo = service.getVoucherView(501L);

        assertNotNull(vo);
        assertEquals(501L, vo.voucherId());
        assertEquals("NO-501", vo.voucherNo());
        assertEquals("TYPE-7", vo.voucherTypeName());
        assertEquals(1, vo.entryCount());
        assertEquals("DRAFT", vo.status());
    }

    @Test
    @DisplayName("getVoucherView: 凭证不存在返回 null")
    void getVoucherView_notFound_returnsNull() {
        when(voucherMapper.selectById(404L)).thenReturn(null);

        assertNull(service.getVoucherView(404L));
    }

    @Test
    @DisplayName("getVoucherView: 类型不存在时类型名留空但不中断")
    void getVoucherView_typeMissing_nameNull() {
        when(voucherMapper.selectById(502L)).thenReturn(voucher(502L, 99L, "NO-502", "DRAFT"));
        when(voucherTypeService.getById(99L)).thenThrow(new IllegalArgumentException("not found"));
        when(voucherEntryMapper.selectByVoucherId(502L)).thenReturn(List.of());

        SequenceVoucherVO vo = service.getVoucherView(502L);

        assertNotNull(vo);
        assertNull(vo.voucherTypeName());
        assertEquals(0, vo.entryCount());
    }
}
