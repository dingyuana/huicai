package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.business.dto.vo.ArapSettlementVO;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.voucher.service.VoucherTemplateService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.ReconciliationLogEntity;
import com.huicai.sme.arap.mapper.ReconciliationLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("核销单 ArapSettlementService 单元测试")
class ArapSettlementServiceImplTest {

    @Mock private ArapSettlementMapper mapper;
    @Mock private ArapSettlementEntryMapper entryMapper;
    @Mock private BusinessDocMapper businessDocMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private VoucherTemplateService voucherTemplateService;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private ReconciliationLogMapper logMapper;
    @Mock private OutputInvoiceStateMachineService outputInvoiceStateMachineService;
    @Mock private InputInvoiceStateMachineService inputInvoiceStateMachineService;
    @Mock private BankStatementMapper bankStatementMapper;

    @InjectMocks
    private ArapSettlementServiceImpl service;

    private ArapSettlementEntity settlement(Long id, String status) {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(id);
        e.setSettlementNo("JS-202608-ABC123");
        e.setSettlementType("RECEIVE");
        e.setPeriod("202608");
        e.setStatus(status);
        e.setTotalAmount(new BigDecimal("1000.00"));
        e.setDiscountAmount(BigDecimal.ZERO);
        return e;
    }

    private BusinessDocEntity approvedDoc(Long id, BigDecimal amount) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(id);
        doc.setDocNo("INV-001");
        doc.setDocType("INVOICE_OUT");
        doc.setStatus("APPROVED");
        doc.setAmount(amount);
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(amount);
        return doc;
    }

    private ArapSettlementEntryEntity entryWithDoc(Long businessDocId, String amount) {
        ArapSettlementEntryEntity e = new ArapSettlementEntryEntity();
        e.setId(1L);
        e.setSettlementId(1L);
        e.setBusinessDocId(businessDocId);
        e.setSettledAmount(new BigDecimal(amount));
        return e;
    }

    // ─── pageQuery / 查询 ─────────────────────────────────────────────────

    @Test
    @DisplayName("pageQuery - 传状态+凭证号过滤，按创建时间倒序")
    void pageQuery_filtersAndOrders() {
        Page<ArapSettlementEntity> page = new Page<>(1, 20);
        when(mapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Page<ArapSettlementEntity> p = inv.getArgument(0);
            p.setRecords(List.of(settlement(1L, ArapStatus.DRAFT)));
            return p;
        });

        IPage<ArapSettlementEntity> result = service.pageQuery("DRAFT", "V-202608-001", 1, 20);

        assertEquals("DRAFT", result.getRecords().get(0).getStatus());
        verify(mapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("pageQuery - 无参数时默认页码页大小")
    void pageQuery_defaultsPageParams() {
        when(mapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(new Page<>());

        service.pageQuery(null, null, null, null);

        verify(mapper).selectPage(argThat(p -> p.getCurrent() == 1 && p.getSize() == 20), any());
    }

    @Test
    @DisplayName("getById - 不存在抛异常")
    void getById_notFound_throws() {
        when(mapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(999L));
        assertTrue(ex.getMessage().contains("核销单不存在"));
    }

    @Test
    @DisplayName("getDetailWithPartyName - 客户类型解析客户名称")
    void getDetailWithPartyName_resolvesCustomerName() {
        ArapSettlementEntity e = settlement(1L, ArapStatus.CONFIRMED);
        e.setPartyId(88L);
        e.setPartyType("CUSTOMER");
        when(mapper.selectById(1L)).thenReturn(e);

        CustomerEntity customer = new CustomerEntity();
        customer.setId(88L);
        customer.setName("北京华信");
        when(customerMapper.selectById(88L)).thenReturn(customer);

        ArapSettlementVO vo = service.getDetailWithPartyName(1L);

        assertEquals("北京华信", vo.getCustomerName());
        assertEquals("JS-202608-ABC123", vo.getSettlementNo());
    }

    @Test
    @DisplayName("getDetailWithPartyName - 供应商类型解析供应商名称")
    void getDetailWithPartyName_resolvesVendorName() {
        ArapSettlementEntity e = settlement(1L, ArapStatus.CONFIRMED);
        e.setPartyId(77L);
        e.setPartyType("VENDOR");
        when(mapper.selectById(1L)).thenReturn(e);

        VendorEntity vendor = new VendorEntity();
        vendor.setId(77L);
        vendor.setName("上海腾达");
        when(vendorMapper.selectById(77L)).thenReturn(vendor);

        ArapSettlementVO vo = service.getDetailWithPartyName(1L);

        assertEquals("上海腾达", vo.getVendorName());
    }

    // ─── create / 创建 ───────────────────────────────────────────────────

    @Test
    @DisplayName("create - 自动生成编号、默认DRAFT、合计明细金额")
    void create_autoFillAndSumAmount() {
        ArapSettlementEntity entity = new ArapSettlementEntity();
        entity.setSettlementType("RECEIVE");
        entity.setPeriod("202608");

        ArapSettlementEntryEntity e1 = entryWithDoc(1L, "300.00");
        ArapSettlementEntryEntity e2 = entryWithDoc(2L, "700.00");
        when(mapper.insert(any(ArapSettlementEntity.class))).thenReturn(1);

        ArapSettlementEntity result = service.create(entity, List.of(e1, e2));

        assertEquals(ArapStatus.DRAFT, result.getStatus());
        assertNotNull(result.getSettlementNo());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getDiscountAmount()));
        verify(entryMapper).insert(e1);
        verify(entryMapper).insert(e2);
        assertEquals(result.getId(), e1.getSettlementId());
    }

    // ─── 状态机 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete - 非草稿状态拒绝删除")
    void delete_nonDraft_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.SUBMITTED));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("仅草稿状态可删除"));
        verify(entryMapper, never()).delete(any());
    }

    @Test
    @DisplayName("delete - 草稿状态删除明细并删除主表")
    void delete_draft_deletesEntriesAndEntity() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.DRAFT));
        when(entryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
        when(mapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(entryMapper).delete(any(LambdaQueryWrapper.class));
        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("submit - DRAFT→SUBMITTED 并写日志")
    void submit_draft_marksSubmittedAndLogs() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.DRAFT));
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        service.submit(1L);

        verify(mapper).updateById(argThat((ArapSettlementEntity e) -> ArapStatus.SUBMITTED.equals(e.getStatus())));
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    @DisplayName("submit - 非DRAFT状态拒绝")
    void submit_nonDraft_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.CONFIRMED));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(1L));
        assertTrue(ex.getMessage().contains("状态不允许提交"));
        verify(mapper, never()).updateById(any(ArapSettlementEntity.class));
    }

    @Test
    @DisplayName("confirm - DRAFT 串联 submit→approve")
    void confirm_draft_chainsSubmitAndApprove() {
        // confirm() 内部: getById(selectById) → submit(getById+updateById) → approve(getById+updateById)
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.DRAFT));
        when(entryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        ArapSettlementEntity result = service.confirm(1L);

        // 最终状态由 approve() 写入 CONFIRMED
        assertEquals(ArapStatus.CONFIRMED, result.getStatus());
        // 中间态: submit() 曾把状态置为 SUBMITTED 并 updateById；最终 approve() 置 CONFIRMED
        verify(mapper, times(2)).updateById(argThat((ArapSettlementEntity e) -> ArapStatus.CONFIRMED.equals(e.getStatus())));
        verify(logMapper, atLeast(1)).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    @DisplayName("approve - SUBMITTED→CONFIRMED 更新单据已核销金额")
    void approve_updatesBusinessDocSettledAmount() {
        ArapSettlementEntity entity = settlement(1L, ArapStatus.SUBMITTED);
        entity.setTotalAmount(new BigDecimal("1000.00"));
        when(mapper.selectById(1L)).thenReturn(entity);

        BusinessDocEntity doc = approvedDoc(10L, new BigDecimal("1000.00"));
        when(entryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(entryWithDoc(10L, "1000.00")));
        when(businessDocMapper.selectById(10L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        ArapSettlementEntity result = service.approve(1L);

        assertEquals(ArapStatus.CONFIRMED, result.getStatus());
        // 单据全额核销 → FULLY_RECONCILED
        assertEquals("FULLY_RECONCILED", doc.getStatus());
        assertEquals(0, new BigDecimal("1000.00").compareTo(doc.getSettledAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(doc.getUnsettledAmount()));
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    @DisplayName("approve - 目标单据非审批状态拒绝")
    void approve_docNotApproved_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.SUBMITTED));

        BusinessDocEntity doc = approvedDoc(10L, new BigDecimal("1000.00"));
        doc.setStatus("DRAFT");
        when(entryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(entryWithDoc(10L, "1000.00")));
        when(businessDocMapper.selectById(10L)).thenReturn(doc);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
        assertTrue(ex.getMessage().contains("仅已审批状态的业务单据可核销"));
        verify(businessDocMapper, never()).updateById(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("approve - 核销金额超过未核销余额拒绝")
    void approve_amountExceedsUnsettled_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.SUBMITTED));

        BusinessDocEntity doc = approvedDoc(10L, new BigDecimal("500.00"));
        when(entryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(entryWithDoc(10L, "1000.00")));
        when(businessDocMapper.selectById(10L)).thenReturn(doc);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
        assertTrue(ex.getMessage().contains("核销金额超过未核销余额"));
        verify(businessDocMapper, never()).updateById(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("approve - 旧格式receivableId拒绝")
    void approve_legacyReceivableId_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.SUBMITTED));

        ArapSettlementEntryEntity legacy = new ArapSettlementEntryEntity();
        legacy.setId(1L);
        legacy.setSettlementId(1L);
        legacy.setReceivableId(5L);
        legacy.setSettledAmount(new BigDecimal("100.00"));
        when(entryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(legacy));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
        assertTrue(ex.getMessage().contains("旧格式(receivableId)"));
    }

    @Test
    @DisplayName("approve - 来源单据是收款单时同步已核销金额")
    void approve_syncesSourceReceiptDoc() {
        ArapSettlementEntity entity = settlement(1L, ArapStatus.SUBMITTED);
        entity.setSourceDocId(50L);
        entity.setSourceDocType("receipt");
        when(mapper.selectById(1L)).thenReturn(entity);

        when(entryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BusinessDocEntity sourceDoc = approvedDoc(50L, new BigDecimal("1000.00"));
        when(businessDocMapper.selectById(50L)).thenReturn(sourceDoc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        service.approve(1L);

        assertEquals(0, new BigDecimal("1000.00").compareTo(sourceDoc.getSettledAmount()));
        verify(businessDocMapper, atLeastOnce()).updateById(any(BusinessDocEntity.class));
    }

    // ─── 驳回/取消 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("reject - SUBMITTED→REJECTED 记录原因")
    void reject_recordsReason() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.SUBMITTED));
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        service.reject(1L, "客户资料待补");

        verify(mapper).updateById(argThat((ArapSettlementEntity e) -> ArapStatus.REJECTED.equals(e.getStatus())));
        verify(logMapper).insert(argThat((ReconciliationLogEntity log) -> "客户资料待补".equals(log.getRemark())));
    }

    @Test
    @DisplayName("reject - 非SUBMITTED状态拒绝")
    void reject_wrongStatus_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.CONFIRMED));

        assertThrows(BusinessException.class, () -> service.reject(1L));
    }

    @Test
    @DisplayName("cancel - DRAFT→CANCELLED")
    void cancel_draft_cancels() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.DRAFT));
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        service.cancel(1L);

        verify(mapper).updateById(argThat((ArapSettlementEntity e) -> ArapStatus.CANCELLED.equals(e.getStatus())));
    }

    @Test
    @DisplayName("cancel - 已确认状态拒绝")
    void cancel_confirmed_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.CONFIRMED));

        assertThrows(BusinessException.class, () -> service.cancel(1L));
    }

    // ─── 生成凭证 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateVoucher - 已生成凭证拒绝重复生成")
    void generateVoucher_alreadyHasVoucher_throws() {
        ArapSettlementEntity entity = settlement(1L, ArapStatus.CONFIRMED);
        entity.setVoucherId(99L);
        when(mapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateVoucher(1L));
        assertTrue(ex.getMessage().contains("已生成凭证"));
        verify(voucherTemplateService, never()).matchByClassification(anyString());
    }

    @Test
    @DisplayName("generateVoucher - 未配置模板抛异常")
    void generateVoucher_noTemplate_throws() {
        ArapSettlementEntity entity = settlement(1L, ArapStatus.CONFIRMED);
        when(mapper.selectById(1L)).thenReturn(entity);
        when(entryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(voucherTemplateService.matchByClassification("settlement_receivable")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateVoucher(1L));
        assertTrue(ex.getMessage().contains("核销单凭证模板"));
    }

    @Test
    @DisplayName("generateVoucher - 成功生成凭证并回写核销单状态")
    void generateVoucher_success() {
        ArapSettlementEntity entity = settlement(1L, ArapStatus.CONFIRMED);
        entity.setPeriod("202608");
        when(mapper.selectById(1L)).thenReturn(entity);
        when(entryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        VoucherTemplateEntity template = new VoucherTemplateEntity();
        template.setId(7L);
        when(voucherTemplateService.matchByClassification("settlement_receivable")).thenReturn(template);
        when(voucherTemplateService.getLines(7L)).thenReturn(List.of(simpleLine("debit"), simpleLine("credit")));
        when(voucherNoService.generateNextNo("202608", 2L)).thenReturn("SK-202608-0001");
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            ((VoucherEntity) inv.getArgument(0)).setId(555L);
            return 1;
        });
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);
        when(voucherMapper.updateById(any(VoucherEntity.class))).thenReturn(1);
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        service.generateVoucher(1L);

        assertEquals(ArapStatus.VOUCHERED, entity.getStatus());
        assertEquals(555L, entity.getVoucherId());
        assertEquals("SK-202608-0001", entity.getVoucherNo());
        verify(voucherEntryMapper, atLeast(2)).insert(any(VoucherEntryEntity.class));
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    private VoucherTemplateLineEntity simpleLine(String direction) {
        VoucherTemplateLineEntity line = new VoucherTemplateLineEntity();
        line.setId(1L);
        line.setSubjectId(1L);
        line.setDirection(direction);
        line.setSummaryTemplate("往来核销 {{settlementNo}}");
        return line;
    }

    // ─── 反核销 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("reverse - 非确认/已记账状态拒绝")
    void reverse_wrongStatus_throws() {
        when(mapper.selectById(1L)).thenReturn(settlement(1L, ArapStatus.DRAFT));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(1L));
        assertTrue(ex.getMessage().contains("仅已确认或已记账"));
    }

    @Test
    @DisplayName("reverse - 创建负额对冲单并回滚单据金额")
    void reverse_createsReversalAndRestoresDoc() {
        ArapSettlementEntity entity = settlement(1L, ArapStatus.CONFIRMED);
        entity.setTotalAmount(new BigDecimal("1000.00"));
        when(mapper.selectById(1L)).thenReturn(entity);

        ArapSettlementEntryEntity entry = entryWithDoc(10L, "1000.00");
        when(entryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entry));

        BusinessDocEntity doc = approvedDoc(10L, new BigDecimal("1000.00"));
        doc.setSettledAmount(new BigDecimal("1000.00"));
        doc.setUnsettledAmount(BigDecimal.ZERO);
        doc.setStatus("FULLY_RECONCILED");
        when(businessDocMapper.selectById(10L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);
        when(mapper.insert(any(ArapSettlementEntity.class))).thenReturn(1);
        when(entryMapper.insert(any(ArapSettlementEntryEntity.class))).thenReturn(1);
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);

        service.reverse(1L);

        // 对冲单金额取负
        verify(mapper).insert(argThat((ArapSettlementEntity reversal) -> new BigDecimal("-1000.00").compareTo(reversal.getTotalAmount()) == 0
                && "REVERSAL".equals(reversal.getSettlementType())));
        // 原单据金额回滚
        assertEquals(0, BigDecimal.ZERO.compareTo(doc.getSettledAmount()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(doc.getUnsettledAmount()));
        // 原核销单状态 REVERSED
        assertEquals(ArapStatus.REVERSED, entity.getStatus());
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }
}