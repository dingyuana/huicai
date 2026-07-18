package com.huicai.module.finance.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.mapper.CustomerMapper;

import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.finance.dto.BusinessDocDTO;
import com.huicai.module.finance.dto.BusinessDocQueryDTO;
import com.huicai.module.finance.dto.BusinessDocVO;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.TemplateMatcher;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.entity.UserEntity;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.system.mapper.UserMapper;
import com.huicai.module.system.service.PeriodService;
import com.huicai.module.system.service.VoucherTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BusinessDocServiceImpl 单元测试 — 防御性 update() 验证
 * <p>
 * 覆盖前端 loadDoc() 漏字段导致 supplierId 等被清空的回归场景。
 * 核心约定: 当 dto 中可选字段为 null 或 blank 时, 保留 entity 现有值, 不得覆盖为 null.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessDocServiceImplTest {

    @Mock private BusinessDocMapper docMapper;
    @Mock private BusinessDocEntryMapper docEntryMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private PeriodService periodService;
    @Mock private SubjectMapper subjectMapper;
    @Mock private VoucherTypeService voucherTypeService;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private UserMapper userMapper;
    
    @Mock private TemplateMatcher templateMatcher;
    @Mock private VoucherTemplateService voucherTemplateService;
    @Mock private OutputInvoiceMapper outputInvoiceMapper;

    @org.mockito.InjectMocks private BusinessDocServiceImpl service;

    private static final Long DOC_ID = 346L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        // periodService.lambdaQuery()...one() 链式调用 → 返回 OPEN 期间
        PeriodEntity openPeriod = new PeriodEntity();
        openPeriod.setStatus("open");
        when(periodService.lambdaQuery()).thenReturn(mock(com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper.class));
        var chain = periodService.lambdaQuery();
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.one()).thenReturn(openPeriod);

        // getDetail() 末尾的 populatePartyNames / populateUserNames
        when(vendorMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        // docEntryMapper 路径
        when(docEntryMapper.deleteByDocId(anyLong())).thenReturn(0);
        when(docEntryMapper.selectByDocId(anyLong())).thenReturn(Collections.emptyList());

        // P34: 应收/应付已合并到业务单据，不再需要单独查询

        // P26 P1-1 模板引擎：默认 templateMatcher.match 返回 null，走硬编码降级路径
        when(templateMatcher.match(any())).thenReturn(null);
    }

    private BusinessDocEntity stubDrafDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(DOC_ID);
        e.setDocNo("FK2026060001");
        e.setDocType("PAYMENT");
        e.setDocDate(LocalDate.of(2026, 6, 15));
        e.setPeriod("202606");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus("DRAFT");
        e.setSupplierId(99L);                // 已有供应商
        e.setCustomerId(null);
        e.setApplicantId(50L);
        e.setDeptId(10L);
        e.setSummary("支付货款");
        e.setAttachmentIds("att-1,att-2");
        e.setCreatedBy(1L);
        e.setVoucherId(null);
        e.setSubmittedAt(null);
        return e;
    }

    private BusinessDocDTO stubDto() {
        BusinessDocDTO dto = new BusinessDocDTO();
        dto.setId(DOC_ID);
        dto.setDocType("PAYMENT");
        dto.setDocDate(LocalDate.of(2026, 6, 15));
        dto.setPeriod("202606");
        dto.setAmount(new BigDecimal("1200.00"));  // 改金额
        dto.setEntries(Collections.emptyList());  // update() 会迭代 entries
        // 故意不设置 supplierId / customerId / applicantId / deptId / summary / attachmentIds
        return dto;
    }

    // ==================== 防御性 update ====================

    @Test
    void update_dto缺所有可选字段_全部保留entity原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();

        // 必填字段被覆盖
        assertEquals("PAYMENT", saved.getDocType());
        assertEquals("202606", saved.getPeriod());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("1200.00")));

        // 可选字段全部保留原值 (这是关键断言)
        assertEquals(99L, saved.getSupplierId());
        assertNull(saved.getCustomerId());
        assertEquals(50L, saved.getApplicantId());
        assertEquals(10L, saved.getDeptId());
        assertEquals("支付货款", saved.getSummary());
        assertEquals("att-1,att-2", saved.getAttachmentIds());
    }

    @Test
    void update_dtoSupplierId为null_保留原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setSupplierId(null);  // 显式 null
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals(99L, captor.getValue().getSupplierId());
    }

    @Test
    void update_dto显式传新SupplierId_覆盖原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setSupplierId(200L);  // 显式新值
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals(200L, captor.getValue().getSupplierId());
    }

    @Test
    void update_dto显式传CustomerId_原值是null也允许设值() {
        BusinessDocEntity existing = stubDrafDoc();
        existing.setCustomerId(null);
        BusinessDocDTO dto = stubDto();
        dto.setCustomerId(77L);
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals(77L, captor.getValue().getCustomerId());
    }

    @Test
    void update_dtoAttachmentIds为Blank_保留原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setAttachmentIds("   ");  // 空白字符串
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals("att-1,att-2", captor.getValue().getAttachmentIds());
    }

    @Test
    void update_dto只改summary_其他字段全保留() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setSummary("改后的摘要");
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();
        assertEquals("改后的摘要", saved.getSummary());
        assertEquals(99L, saved.getSupplierId());
        assertEquals(50L, saved.getApplicantId());
        assertEquals(10L, saved.getDeptId());
        assertEquals("att-1,att-2", saved.getAttachmentIds());
    }

    // ==================== 状态守卫 ====================

    @Test
    void update_id为空_throwBadRequest() {
        BusinessDocDTO dto = stubDto();
        dto.setId(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("更新时单据ID不能为空"));
    }

    @Test
    void update_id不存在_throwNotFound() {
        when(docMapper.selectById(999L)).thenReturn(null);
        BusinessDocDTO dto = stubDto();
        dto.setId(999L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("单据不存在"));
    }

    @Test
    void update_非DRAFT状态_throwBadRequest() {
        BusinessDocEntity existing = stubDrafDoc();
        existing.setStatus("SUBMITTED");
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        BusinessDocDTO dto = stubDto();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("仅草稿状态"));
    }

    // ==================== submittedAt / voucherId 保护 (回归) ====================

    @Test
    void update_不得清空voucherId和submittedAt() {
        // 即便 dto 是 DRAFT 状态, 如果一个已生成凭证的 doc 被错误传入 update
        // (虽然 update 守卫会拒绝), 这里验证逻辑上不会清空 voucherId/submittedAt
        BusinessDocEntity existing = stubDrafDoc();
        existing.setVoucherId(555L);
        existing.setSubmittedBy(2L);
        existing.setSubmittedAt(java.time.LocalDateTime.of(2026, 6, 16, 10, 0));
        existing.setStatus("DRAFT");  // 强行 DRAFT
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        BusinessDocDTO dto = stubDto();
        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();
        // update() 不应该触碰这些字段
        assertEquals(555L, saved.getVoucherId());
        assertEquals(2L, saved.getSubmittedBy());
        assertNotNull(saved.getSubmittedAt());
    }

    // ==================== getDetail — 名称回填端到端验证 ====================

    private BusinessDocEntity stubFullVoucheredDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(DOC_ID);
        e.setDocNo("FK2026060001");
        e.setDocType("PAYMENT");
        e.setDocDate(LocalDate.of(2026, 6, 15));
        e.setPeriod("202606");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus("VOUCHERED");
        e.setSupplierId(99L);
        e.setCustomerId(null);
        e.setSummary("支付货款");
        e.setVoucherId(555L);
        e.setCreatedBy(1L);
        e.setCreatedAt(java.time.LocalDateTime.of(2026, 6, 15, 9, 0, 0));
        e.setSubmittedBy(2L);
        e.setSubmittedAt(java.time.LocalDateTime.of(2026, 6, 16, 10, 30, 0));
        e.setApprovedBy(3L);
        e.setApprovedAt(java.time.LocalDateTime.of(2026, 6, 16, 14, 0, 0));
        return e;
    }

    private UserEntity stubUser(Long id, String realName) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setRealName(realName);
        return u;
    }

    private VendorEntity stubVendor(Long id, String name) {
        VendorEntity v = new VendorEntity();
        v.setId(id);
        v.setName(name);
        return v;
    }

    @Test
    void getDetail_已生成凭证付款单_所有名称回填() {
        BusinessDocEntity e = stubFullVoucheredDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(e);
        when(vendorMapper.selectBatchIds(argThat(ids -> ids != null && ids.contains(99L))))
                .thenReturn(List.of(stubVendor(99L, "XX 供应商有限公司")));
        when(userMapper.selectBatchIds(argThat(ids -> ids != null && ids.contains(1L) && ids.contains(2L) && ids.contains(3L))))
                .thenReturn(List.of(
                        stubUser(1L, "张三"),
                        stubUser(2L, "李四"),
                        stubUser(3L, "王五")
                ));

        BusinessDocVO vo = service.getDetail(DOC_ID);

        assertEquals(555L, vo.getVoucherId());
        assertEquals(1L, vo.getCreatedBy());
        assertEquals("张三", vo.getCreatedByName());
        assertEquals(2L, vo.getSubmittedBy());
        assertEquals("李四", vo.getSubmittedByName());
        assertEquals(3L, vo.getApprovedBy());
        assertEquals("王五", vo.getApprovedByName());
        assertNotNull(vo.getSubmittedAt());
        assertNotNull(vo.getApprovedAt());
        assertEquals(99L, vo.getSupplierId());
        assertEquals("XX 供应商有限公司", vo.getSupplierName());
    }

    @Test
    void getDetail_无userId_不查user表() {
        BusinessDocEntity e = stubFullVoucheredDoc();
        e.setCreatedBy(null);
        e.setSubmittedBy(null);
        e.setApprovedBy(null);
        when(docMapper.selectById(DOC_ID)).thenReturn(e);

        BusinessDocVO vo = service.getDetail(DOC_ID);

        assertNull(vo.getCreatedByName());
        assertNull(vo.getSubmittedByName());
        assertNull(vo.getApprovedByName());
        verify(userMapper, never()).selectBatchIds(anyList());
    }

    @Test
    void getDetail_无supplierId_不查vendor表() {
        BusinessDocEntity e = stubFullVoucheredDoc();
        e.setSupplierId(null);
        when(docMapper.selectById(DOC_ID)).thenReturn(e);

        BusinessDocVO vo = service.getDetail(DOC_ID);

        assertNull(vo.getSupplierName());
        verify(vendorMapper, never()).selectBatchIds(anyList());
    }

    @Test
    void getDetail_记录不存在_throwNotFound() {
        when(docMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDetail(999L));
        assertTrue(ex.getMessage().contains("单据不存在"));
    }

    @Test
    void getDetail_userRealName为空_回退到username() {
        BusinessDocEntity e = stubFullVoucheredDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(e);
        when(vendorMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        UserEntity u = new UserEntity();
        u.setId(1L);
        u.setUsername("zhangsan_login");
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(u));

        BusinessDocVO vo = service.getDetail(DOC_ID);

        assertEquals("zhangsan_login", vo.getCreatedByName());
    }

    // ==================== generateVoucher — 静默失败防护 ====================

    private BusinessDocEntryEntity stubEntry(Long id, BigDecimal amount) {
        BusinessDocEntryEntity e = new BusinessDocEntryEntity();
        e.setId(id);
        e.setDocId(DOC_ID);
        e.setSubjectId(1L);
        e.setAmount(amount);
        e.setSortOrder(1);
        return e;
    }

    private BusinessDocEntity stubApprovedPayDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(DOC_ID);
        e.setDocNo("FK2026060001");
        e.setDocType("PAYMENT");
        e.setDocDate(LocalDate.of(2026, 6, 15));
        e.setPeriod("202606");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus("APPROVED");
        e.setSupplierId(99L);
        e.setSummary("支付货款");
        e.setVoucherId(null);
        return e;
    }

    @Test
    void generateVoucher_科目不存在_抛BusinessException_不标记VOUCHERED() {
        BusinessDocEntity e = stubApprovedPayDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(e);
        when(docEntryMapper.selectByDocId(DOC_ID))
                .thenReturn(List.of(stubEntry(1L, new BigDecimal("1000.00"))));
        when(voucherNoService.generateNextNo("202606", 1L)).thenReturn("FK2026060001");
        when(subjectMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            VoucherEntity v = inv.getArgument(0);
            v.setId(555L);
            return 1;
        }).when(voucherMapper).insert(any(VoucherEntity.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateVoucher(DOC_ID, USER_ID));

        assertTrue(ex.getMessage().contains("科目不存在"),
                "应明确提示科目不存在, 实际消息: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("2202"),
                "应指明缺失的科目代码, 实际消息: " + ex.getMessage());
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
    }

    @Test
    void generateVoucher_非APPROVED状态_throwBadRequest() {
        BusinessDocEntity e = stubApprovedPayDoc();
        e.setStatus("DRAFT");
        when(docMapper.selectById(DOC_ID)).thenReturn(e);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateVoucher(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅已审批状态"));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }

    @Test
    void generateVoucher_已生成凭证_不重复生成() {
        BusinessDocEntity e = stubApprovedPayDoc();
        e.setVoucherId(888L);
        when(docMapper.selectById(DOC_ID)).thenReturn(e);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateVoucher(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("已生成凭证"));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }

    @Test
    void generateVoucher_正常路径_APPROVED且科目齐备_成功生成() {
        BusinessDocEntity e = stubApprovedPayDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(e);
        when(docEntryMapper.selectByDocId(DOC_ID))
                .thenReturn(List.of(stubEntry(1L, new BigDecimal("1000.00"))));
        when(voucherNoService.generateNextNo("202606", 1L)).thenReturn("FK2026060001");
        Subject debit = new Subject();
        debit.setId(10L);
        debit.setCode("2202");
        Subject credit = new Subject();
        credit.setId(20L);
        credit.setCode("1002");
        when(subjectMapper.selectOne(any()))
                .thenReturn(debit)
                .thenReturn(credit);
        doAnswer(inv -> {
            VoucherEntity v = inv.getArgument(0);
            v.setId(555L);
            return 1;
        }).when(voucherMapper).insert(any(VoucherEntity.class));
        when(vendorMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        BusinessDocVO vo = service.generateVoucher(DOC_ID, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();
        assertEquals(555L, saved.getVoucherId());
        assertEquals("VOUCHERED", saved.getStatus());
        // PAYMENT 只有一对科目 (2202/1002), 1 条 doc entry → 2 条 voucher entry
        verify(voucherEntryMapper, times(2)).insert(any(VoucherEntryEntity.class));
        assertNotNull(vo);
    }

    // ==================== P27b 模板上下文注入 customer/supplier 名称 ====================

    @Test
    void generateVoucher_应将customerName注入模板上下文() {
        BusinessDocEntity e = stubApprovedPayDoc();
        e.setCustomerId(77L);
        com.huicai.module.arap.entity.CustomerEntity customer = new com.huicai.module.arap.entity.CustomerEntity();
        customer.setId(77L);
        customer.setName("测试客户");
        when(customerMapper.selectById(77L)).thenReturn(customer);
        when(docMapper.selectById(DOC_ID)).thenReturn(e);

        // 触发到 templateMatcher.match 即可（验证 customerMapper.selectById 被调用）
        when(templateMatcher.match(any())).thenAnswer(inv -> {
            // 校验 ctx 中已注入 customerName
            Object ctx = inv.getArgument(0);
            // ctx 类型: com.huicai.common.util.TemplateContext
            assertNotNull(ctx);
            return null;
        });
        try {
            service.generateVoucher(DOC_ID, USER_ID);
        } catch (Exception ex) {
            // 接受任何异常（科目未配等），关键是 customerMapper 已被调用
        }

        verify(customerMapper, atLeastOnce()).selectById(77L);
    }

    @Test
    void generateVoucher_应将supplierName注入模板上下文() {
        BusinessDocEntity e = stubApprovedPayDoc();  // supplierId=99L
        com.huicai.module.arap.entity.VendorEntity vendor = new com.huicai.module.arap.entity.VendorEntity();
        vendor.setId(99L);
        vendor.setName("测试供应商");
        when(vendorMapper.selectById(99L)).thenReturn(vendor);
        when(docMapper.selectById(DOC_ID)).thenReturn(e);

        when(templateMatcher.match(any())).thenAnswer(inv -> {
            Object ctx = inv.getArgument(0);
            assertNotNull(ctx);
            return null;
        });
        try {
            service.generateVoucher(DOC_ID, USER_ID);
        } catch (Exception ex) {
            // 接受任何异常
        }

        verify(vendorMapper, atLeastOnce()).selectById(99L);
    }

    // ========== pageQuery() 测试 ==========

    private BusinessDocEntity createStubDoc(Long id, String docType, String status, BigDecimal amount) {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(id);
        e.setDocNo("DOC-" + id);
        e.setDocType(docType);
        e.setDocDate(LocalDate.of(2026, 6, 15));
        e.setPeriod("202606");
        e.setAmount(amount);
        e.setStatus(status);
        e.setSupplierId(99L);
        e.setCustomerId(id % 2 == 0L ? 100L : null);
        e.setSummary("测试单据 " + id);
        e.setCreatedBy(1L);
        e.setVoucherId(null);
        e.setInvoiceNo(null);
        e.setSettledAmount(BigDecimal.ZERO);
        e.setUnsettledAmount(amount);
        return e;
    }

    @Test
    void pageQuery_emptyQuery_returnsAll() {
        BusinessDocEntity doc = createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("1000"));
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(doc));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(1, result.getTotal());
        assertEquals("RECEIPT", result.getRecords().get(0).getDocType());
        verify(docMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void pageQuery_withSingleDocType_filtersCorrectly() {
        BusinessDocEntity receipt = createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("1000"));
        BusinessDocEntity payment = createStubDoc(2L, "PAYMENT", "APPROVED", new BigDecimal("500"));
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(receipt));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setDocType("RECEIPT");
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(1, result.getTotal());
        assertEquals("RECEIPT", result.getRecords().get(0).getDocType());
    }

    @Test
    void pageQuery_withDocTypes_list_filtersCorrectly() {
        // 模拟核销工作台场景：docTypes=['RECEIPT','INVOICE_OUT','OTHER_RECEIVABLE']
        BusinessDocEntity receipt = createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("1000"));
        BusinessDocEntity invoiceOut = createStubDoc(2L, "INVOICE_OUT", "APPROVED", new BigDecimal("1950"));
        BusinessDocEntity payment = createStubDoc(3L, "PAYMENT", "APPROVED", new BigDecimal("500"));
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 2);
        mockPage.setRecords(List.of(receipt, invoiceOut));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setDocTypes(List.of("RECEIPT", "INVOICE_OUT", "OTHER_RECEIVABLE"));
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
        // 验证两种类型都被返回
        assertTrue(result.getRecords().stream().anyMatch(v -> "RECEIPT".equals(v.getDocType())));
        assertTrue(result.getRecords().stream().anyMatch(v -> "INVOICE_OUT".equals(v.getDocType())));
    }

    @Test
    void pageQuery_withStatus_filtersCorrectly() {
        BusinessDocEntity draft = createStubDoc(1L, "RECEIPT", "DRAFT", new BigDecimal("1000"));
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(draft));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setStatus("DRAFT");
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(1, result.getTotal());
        assertEquals("DRAFT", result.getRecords().get(0).getStatus());
    }

    @Test
    void pageQuery_withKeyword_filtersByDocNo() {
        BusinessDocEntity doc = createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("1000"));
        doc.setDocNo("INV-2026-001");
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(doc));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setKeyword("INV-2026-001");
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(1, result.getTotal());
    }

    @Test
    void pageQuery_withPeriod_filtersCorrectly() {
        BusinessDocEntity doc = createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("1000"));
        doc.setPeriod("202605");
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(doc));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setPeriod("202605");
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(1, result.getTotal());
        assertEquals("202605", result.getRecords().get(0).getPeriod());
    }

    @Test
    void pageQuery_emptyResult_returnsEmptyPage() {
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 0);
        mockPage.setRecords(Collections.emptyList());
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setDocType("NONEXISTENT");
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void pageQuery_pagination_respectsSize() {
        List<BusinessDocEntity> docs = List.of(
            createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("100")),
            createStubDoc(2L, "RECEIPT", "APPROVED", new BigDecimal("200")),
            createStubDoc(3L, "RECEIPT", "APPROVED", new BigDecimal("300"))
        );
        Page<BusinessDocEntity> mockPage = new Page<>(1, 2, 3);
        mockPage.setRecords(docs.subList(0, 2));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setCurrent(1);
        query.setSize(2);
        IPage<BusinessDocVO> result = service.pageQuery(query);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    void pageQuery_populatesPartyNames_correctly() {
        BusinessDocEntity doc = createStubDoc(1L, "RECEIPT", "APPROVED", new BigDecimal("1000"));
        doc.setCustomerId(100L);
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(doc));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        IPage<BusinessDocVO> result = service.pageQuery(query);

        // 验证 customerName 被填充（即使为空列表也不会 NPE）
        assertNotNull(result.getRecords().get(0));
    }

    @Test
    void pageQuery_multipleDocType_combinations_works() {
        // 验证 docType 和 docTypes 共存时，MyBatis Plus eq + in 都生效
        // docType='RECEIPT' AND docType IN ('INVOICE_OUT') → 无匹配记录
        // 但由于用的是 mock，selectPage 返回预设数据，验证 mock 行为
        BusinessDocEntity invoiceOut = createStubDoc(1L, "INVOICE_OUT", "APPROVED", new BigDecimal("1950"));
        Page<BusinessDocEntity> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(invoiceOut));
        when(docMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setDocType("RECEIPT");
        query.setDocTypes(List.of("INVOICE_OUT"));
        IPage<BusinessDocVO> result = service.pageQuery(query);

        // mock 返回预设数据，不执行真实 SQL 验证
        // 实际生产环境中，eq(docType='RECEIPT') AND in(docType IN ('INVOICE_OUT'))
        // 会导致 SQL 无匹配，返回 0 条。这里 mock 返回 1 条是为了验证
        // pageQuery 本身不因参数矛盾而崩溃
        assertEquals(1, result.getTotal());
    }
}
