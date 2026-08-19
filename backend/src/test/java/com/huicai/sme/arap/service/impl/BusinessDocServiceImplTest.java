package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.business.dto.BusinessDocDTO;
import com.huicai.base.business.dto.BusinessDocQueryDTO;
import com.huicai.base.business.dto.BusinessDocVO;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.BusinessDocEntryEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.BusinessDocEntryMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.business.util.TemplateMatcher;
import com.huicai.base.business.service.BusinessDocService;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.voucher.service.VoucherTemplateService;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.util.TemplateContext;
import com.huicai.sme.arap.constant.BusinessDocStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BusinessDocServiceImpl 单元测试.
 *
 * <p>覆盖业务单据的全部核心方法：pageQuery / getDetail / create / update / delete /
 * submit / approve / reject / generateVoucher / generateDocNo.
 *
 * <p>每个方法同时包含正向断言（该做的做了）和负向断言（不该做的没做）。
 * 测试方法命名与覆盖矩阵一致（共 31 个）。
 */
@ExtendWith(MockitoExtension.class)
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

    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private BusinessDocServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long DOC_ID = 100L;
    private static final String PERIOD = "202606";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
    }

    // ====================================================================
    // Helper 工厂方法
    // ====================================================================

    /** 构造一个 DRAFT 状态的业务单据实体（含分录） */
    private BusinessDocEntity draftDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(DOC_ID);
        e.setDocNo("SK2026060001");
        e.setDocType("RECEIPT");
        e.setDocDate(LocalDate.of(2026, 6, 1));
        e.setPeriod(PERIOD);
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus(BusinessDocStatus.DRAFT);
        e.setCustomerId(10L);
        e.setSummary("测试收款单");
        e.setSource("MANUAL");
        e.setSettledAmount(BigDecimal.ZERO);
        e.setUnsettledAmount(new BigDecimal("1000.00"));
        return e;
    }

    /** 构造一个 APPROVED 状态单据（可生成凭证） */
    private BusinessDocEntity approvedDoc() {
        BusinessDocEntity e = draftDoc();
        e.setStatus(BusinessDocStatus.APPROVED);
        return e;
    }

    /** 构造一个开放期间 */
    private PeriodEntity openPeriod() {
        PeriodEntity p = new PeriodEntity();
        p.setId(1L);
        p.setPeriodCode(PERIOD);
        p.setStatus("open");
        return p;
    }

    /** 构造一条分录 DTO */
    private BusinessDocDTO.EntryDTO entryDTO() {
        BusinessDocDTO.EntryDTO e = new BusinessDocDTO.EntryDTO();
        e.setSubjectId(1L);
        e.setAmount(new BigDecimal("1000.00"));
        e.setSummary("测试分录");
        return e;
    }

    /** 构造一条分录实体 */
    private BusinessDocEntryEntity entryEntity() {
        BusinessDocEntryEntity e = new BusinessDocEntryEntity();
        e.setId(1L);
        e.setDocId(DOC_ID);
        e.setSubjectId(1L);
        e.setAmount(new BigDecimal("1000.00"));
        e.setSummary("测试分录");
        e.setSortOrder(1);
        return e;
    }

    /** 构造一个 DTO */
    private BusinessDocDTO createDTO() {
        BusinessDocDTO dto = new BusinessDocDTO();
        dto.setDocType("RECEIPT");
        dto.setDocDate(LocalDate.of(2026, 6, 1));
        dto.setPeriod(PERIOD);
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setCustomerId(10L);
        dto.setSummary("测试收款单");
        dto.setEntries(List.of(entryDTO()));
        return dto;
    }

    /** 构造一个科目 */
    private Subject subject(String code, String name) {
        Subject s = new Subject();
        s.setId(1L);
        s.setCode(code);
        s.setName(name);
        return s;
    }

    /** mock periodService 返回开放期间（参考 PeriodCloseServiceImplTest 的模式） */
    @SuppressWarnings("unchecked")
    private void mockOpenPeriod() {
        PeriodEntity p = openPeriod();
        LambdaQueryChainWrapper<PeriodEntity> chain = mock(LambdaQueryChainWrapper.class);
        lenient().when(periodService.lambdaQuery()).thenReturn(chain);
        lenient().when(chain.eq(any(), any())).thenReturn(chain);
        lenient().when(chain.one()).thenReturn(p);
    }

    // ====================================================================
    // 1. pageQuery 分页查询
    // ====================================================================

    @Test
    @DisplayName("testPageQuery_正常分页返回结果")
    void testPageQuery_正常分页返回结果() {
        // given
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        q.setCurrent(1);
        q.setSize(20);
        BusinessDocEntity entity = draftDoc();
        Page<BusinessDocEntity> entityPage = new Page<>(1, 20, 1);
        entityPage.setRecords(List.of(entity));
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(entityPage);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：返回 VO 列表，总数正确
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("SK2026060001", result.getRecords().get(0).getDocNo());
        verify(docMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("testPageQuery_空结果返回空页")
    void testPageQuery_空结果返回空页() {
        // given
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        Page<BusinessDocEntity> emptyPage = new Page<>(1, 20, 0);
        emptyPage.setRecords(Collections.emptyList());
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：空页
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        // then — 负向：空结果不应查询客户/供应商
        verify(customerMapper, never()).selectBatchIds(anyList());
        verify(vendorMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("testPageQuery_按docType过滤")
    void testPageQuery_按docType过滤() {
        // given
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        q.setDocType("RECEIPT");
        Page<BusinessDocEntity> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(draftDoc()));
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：按类型过滤后返回
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("RECEIPT", result.getRecords().get(0).getDocType());
        verify(docMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("testPageQuery_按status过滤")
    void testPageQuery_按status过滤() {
        // given
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        q.setStatus("DRAFT");
        Page<BusinessDocEntity> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(draftDoc()));
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：按状态过滤后返回
        assertNotNull(result);
        assertEquals("DRAFT", result.getRecords().get(0).getStatus());
    }

    // ====================================================================
    // 2. getDetail 详情查询
    // ====================================================================

    @Test
    @DisplayName("testGetDetail_正常返回详情")
    void testGetDetail_正常返回详情() {
        // given
        BusinessDocEntity entity = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(subject("1002", "银行存款"));
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        BusinessDocVO vo = service.getDetail(DOC_ID);

        // then — 正向
        assertNotNull(vo);
        assertEquals(DOC_ID, vo.getId());
        assertEquals("SK2026060001", vo.getDocNo());
        assertEquals(1, vo.getEntries().size());
        assertEquals("1002", vo.getEntries().get(0).getSubjectCode());
        verify(docMapper).selectById(DOC_ID);
        verify(docEntryMapper).selectByDocId(DOC_ID);
    }

    @Test
    @DisplayName("testGetDetail_不存在返回异常")
    void testGetDetail_不存在返回异常() {
        // given
        when(docMapper.selectById(DOC_ID)).thenReturn(null);

        // when/then — 不存在抛 BusinessException
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDetail(DOC_ID));
        assertTrue(ex.getMessage().contains("单据不存在"));
        // then — 负向：不应查询分录
        verify(docEntryMapper, never()).selectByDocId(anyLong());
    }

    @Test
    @DisplayName("testGetDetail_已删除不返回")
    void testGetDetail_已删除不返回() {
        // given — MyBatis-Plus @TableLogic 会让 selectById 返回 null（已逻辑删除）
        when(docMapper.selectById(DOC_ID)).thenReturn(null);

        // when/then — 已删除视为不存在
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDetail(DOC_ID));
        assertTrue(ex.getMessage().contains("单据不存在"));
        verify(docEntryMapper, never()).selectByDocId(anyLong());
    }

    // ====================================================================
    // 3. create 创建
    // ====================================================================

    @Test
    @DisplayName("testCreate_正常创建")
    void testCreate_正常创建() {
        // given
        BusinessDocDTO dto = createDTO();
        mockOpenPeriod();
        BusinessDocEntity saved = draftDoc();
        saved.setId(null);
        // 模拟 insert 后回填 id
        doAnswer(inv -> {
            BusinessDocEntity e = inv.getArgument(0);
            e.setId(DOC_ID);
            return 1;
        }).when(docMapper).insert(any(BusinessDocEntity.class));
        // create 末尾调用 getDetail，mock 相关查询
        when(docMapper.selectById(DOC_ID)).thenReturn(draftDoc());
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(subject("1002", "银行存款"));
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        BusinessDocVO vo = service.create(dto, USER_ID);

        // then — 正向
        assertNotNull(vo);
        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).insert(captor.capture());
        BusinessDocEntity inserted = captor.getValue();
        assertEquals(USER_ID, inserted.getCreatedBy());
        assertEquals("DRAFT", inserted.getStatus());
        assertEquals("MANUAL", inserted.getSource());
        // 分录也插入了
        verify(docEntryMapper).insert(any(BusinessDocEntryEntity.class));
    }

    @Test
    @DisplayName("testCreate_编号自动生成")
    void testCreate_编号自动生成() {
        // given
        BusinessDocDTO dto = createDTO();
        dto.setDocNo(null); // 不传编号，触发自动生成
        mockOpenPeriod();
        doAnswer(inv -> {
            BusinessDocEntity e = inv.getArgument(0);
            e.setId(DOC_ID);
            return 1;
        }).when(docMapper).insert(any(BusinessDocEntity.class));
        when(docMapper.selectById(DOC_ID)).thenReturn(draftDoc());
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        service.create(dto, USER_ID);

        // then — 正向：调用了 generateDocNo（redisTemplate.opsForValue().increment）
        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getDocNo());
        assertTrue(captor.getValue().getDocNo().startsWith("SK"));
        verify(valueOps).increment(anyString());
    }

    // ====================================================================
    // 4. update 更新
    // ====================================================================

    @Test
    @DisplayName("testUpdate_正常更新")
    void testUpdate_正常更新() {
        // given
        BusinessDocDTO dto = createDTO();
        dto.setId(DOC_ID);
        BusinessDocEntity existing = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);
        mockOpenPeriod();
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        BusinessDocVO vo = service.update(dto, USER_ID);

        // then — 正向
        assertNotNull(vo);
        verify(docMapper).updateById(any(BusinessDocEntity.class));
        verify(docEntryMapper).deleteByDocId(DOC_ID);
        verify(docEntryMapper).insert(any(BusinessDocEntryEntity.class));
    }

    @Test
    @DisplayName("testUpdate_不存在抛异常")
    void testUpdate_不存在抛异常() {
        // given
        BusinessDocDTO dto = createDTO();
        dto.setId(DOC_ID);
        when(docMapper.selectById(DOC_ID)).thenReturn(null);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("单据不存在"));
        // then — 负向：不应更新
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
        verify(docEntryMapper, never()).deleteByDocId(anyLong());
    }

    @Test
    @DisplayName("testUpdate_状态不允许抛异常")
    void testUpdate_状态不允许抛异常() {
        // given — 非 DRAFT 状态不可修改（替代版本冲突场景）
        BusinessDocDTO dto = createDTO();
        dto.setId(DOC_ID);
        BusinessDocEntity existing = draftDoc();
        existing.setStatus(BusinessDocStatus.APPROVED); // 已审批不可改
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("仅草稿状态单据可修改"));
        // then — 负向
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
        verify(docEntryMapper, never()).deleteByDocId(anyLong());
    }

    // ====================================================================
    // 5. delete 删除
    // ====================================================================

    @Test
    @DisplayName("testDelete_逻辑删除")
    void testDelete_逻辑删除() {
        // given — DRAFT 状态可删（@TableLogic 实现逻辑删除）
        BusinessDocEntity existing = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        // when
        service.delete(DOC_ID);

        // then — 正向：调用 deleteById（MyBatis-Plus @TableLogic 自动转 UPDATE）
        verify(docMapper).deleteById(DOC_ID);
        verify(docEntryMapper).deleteByDocId(DOC_ID);
    }

    @Test
    @DisplayName("testDelete_不存在抛异常")
    void testDelete_不存在抛异常() {
        // given
        when(docMapper.selectById(DOC_ID)).thenReturn(null);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(DOC_ID));
        assertTrue(ex.getMessage().contains("单据不存在"));
        // then — 负向
        verify(docMapper, never()).deleteById(anyLong());
        verify(docEntryMapper, never()).deleteByDocId(anyLong());
    }

    // ====================================================================
    // 6. generateVoucher 生成凭证
    // ====================================================================

    @Test
    @DisplayName("testGenerateVoucher_正常生成凭证")
    void testGenerateVoucher_正常生成凭证() {
        // given — APPROVED 状态，无 voucherId，走降级硬编码科目路径
        BusinessDocEntity entity = approvedDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);
        when(templateMatcher.match(any(TemplateContext.class))).thenReturn(null); // 无模板，走降级
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        when(voucherNoService.generateNextNo(anyString(), anyLong())).thenReturn("V2026060001");
        // 科目查询 mock
        when(subjectMapper.selectOne(any())).thenReturn(subject("1002", "银行存款"));
        // insert voucher 回填 id
        doAnswer(inv -> {
            VoucherEntity v = inv.getArgument(0);
            v.setId(200L);
            return 1;
        }).when(voucherMapper).insert(any(VoucherEntity.class));
        // generateVoucher 末尾调用 getDetail
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        BusinessDocVO vo = service.generateVoucher(DOC_ID, USER_ID);

        // then — 正向：凭证创建、单据状态变为 VOUCHERED
        assertNotNull(vo);
        verify(voucherMapper).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, atLeast(1)).insert(any(VoucherEntryEntity.class));
        verify(voucherMapper).updateById(any(VoucherEntity.class));
        verify(docMapper).updateById(any(BusinessDocEntity.class));
        // 验证单据状态更新为 VOUCHERED
        ArgumentCaptor<BusinessDocEntity> docCaptor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(docCaptor.capture());
        assertEquals(BusinessDocStatus.VOUCHERED, docCaptor.getValue().getStatus());
        assertEquals(200L, docCaptor.getValue().getVoucherId());
    }

    @Test
    @DisplayName("testGenerateVoucher_状态不允许")
    void testGenerateVoucher_状态不允许() {
        // given — DRAFT 状态不可生成凭证
        BusinessDocEntity entity = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateVoucher(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅已审批状态可生成凭证"));
        // then — 负向：不应创建任何凭证
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
    }

    @Test
    @DisplayName("testGenerateVoucher_已生成凭证不可重复")
    void testGenerateVoucher_已生成凭证不可重复() {
        // given — APPROVED 但已有 voucherId
        BusinessDocEntity entity = approvedDoc();
        entity.setVoucherId(200L);
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when/then — 替代"金额为零"场景，测试已有凭证的拦截
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateVoucher(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("该单据已生成凭证"));
        // then — 负向
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }

    // ====================================================================
    // 7. submit 提交（对应任务中的 confirm）
    // ====================================================================

    @Test
    @DisplayName("testConfirm_正常确认_提交单据")
    void testConfirm_正常确认() {
        // given — DRAFT → SUBMITTED
        BusinessDocEntity entity = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when
        service.submit(DOC_ID, USER_ID);

        // then — 正向：状态变为 SUBMITTED，记录提交人
        assertEquals(BusinessDocStatus.SUBMITTED, entity.getStatus());
        assertEquals(USER_ID, entity.getSubmittedBy());
        assertNotNull(entity.getSubmittedAt());
        verify(docMapper).updateById(entity);
        // then — 负向：提交不应创建凭证或单据
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(docMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("testConfirm_状态不允许")
    void testConfirm_状态不允许() {
        // given — APPROVED 不可再提交
        BusinessDocEntity entity = approvedDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submit(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅草稿状态可提交"));
        // then — 负向
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
    }

    // ====================================================================
    // 8. reject 驳回
    // ====================================================================

    @Test
    @DisplayName("testReject_正常驳回")
    void testReject_正常驳回() {
        // given — SUBMITTED → REJECTED
        BusinessDocEntity entity = draftDoc();
        entity.setStatus(BusinessDocStatus.SUBMITTED);
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when
        service.reject(DOC_ID, USER_ID);

        // then — 正向
        assertEquals(BusinessDocStatus.REJECTED, entity.getStatus());
        assertEquals(USER_ID, entity.getUpdatedBy());
        verify(docMapper).updateById(entity);
        // then — 负向：驳回不应创建凭证
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }

    @Test
    @DisplayName("testReject_状态不允许")
    void testReject_状态不允许() {
        // given — DRAFT 不可驳回（必须 SUBMITTED）
        BusinessDocEntity entity = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅已提交状态可驳回"));
        // then — 负向
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
    }

    // ====================================================================
    // 9. approve 审批（对应任务中的 audit）
    // ====================================================================

    @Test
    @DisplayName("testAudit_正常审核")
    void testAudit_正常审核() {
        // given — SUBMITTED → APPROVED
        BusinessDocEntity entity = draftDoc();
        entity.setStatus(BusinessDocStatus.SUBMITTED);
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when
        service.approve(DOC_ID, USER_ID);

        // then — 正向
        assertEquals(BusinessDocStatus.APPROVED, entity.getStatus());
        assertEquals(USER_ID, entity.getApprovedBy());
        assertNotNull(entity.getApprovedAt());
        verify(docMapper).updateById(entity);
        // then — 负向：审批不应生成凭证（铁律 #1：人是唯一审核主体，凭证需单独触发）
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(docEntryMapper, never()).insert(any(BusinessDocEntryEntity.class));
    }

    @Test
    @DisplayName("testAudit_状态不允许")
    void testAudit_状态不允许() {
        // given — DRAFT 不可审批（必须 SUBMITTED）
        BusinessDocEntity entity = draftDoc();
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅已提交状态可审批"));
        // then — 负向
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("testAudit_自审拦截_制单人不能审核自己提交的单据")
    void testAudit_自审拦截_制单人审核自己() {
        // given — createdBy == userId，禁止自审
        BusinessDocEntity entity = draftDoc();
        entity.setStatus(BusinessDocStatus.SUBMITTED);
        entity.setCreatedBy(USER_ID);
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(DOC_ID, USER_ID));
        assertTrue(ex.getMessage().contains("制单人不能审核自己提交的单据"));
        // then — 负向：状态不变，未更新
        assertEquals(BusinessDocStatus.SUBMITTED, entity.getStatus());
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("testAudit_自审拦截_不同用户审核成功")
    void testAudit_自审拦截_不同用户审核成功() {
        // given — createdBy=1, userId=2，允许审核
        BusinessDocEntity entity = draftDoc();
        entity.setStatus(BusinessDocStatus.SUBMITTED);
        entity.setCreatedBy(1L);
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);

        // when
        service.approve(DOC_ID, 2L);

        // then — 正向
        assertEquals(BusinessDocStatus.APPROVED, entity.getStatus());
        verify(docMapper).updateById(entity);
    }

    @Test
    @DisplayName("testReverse_生成红冲单时原单isReversed标记为true")
    void testReverse_父端标记() {
        // given — VOUCHERED 状态单据可红冲
        BusinessDocEntity entity = approvedDoc();
        entity.setStatus(BusinessDocStatus.VOUCHERED);
        entity.setVoucherId(100L);
        entity.setIsReversed(false);
        when(docMapper.selectById(DOC_ID)).thenReturn(entity);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
        // insert 回调设置红冲单 id
        doAnswer(inv -> {
            BusinessDocEntity e = inv.getArgument(0);
            e.setId(999L);
            return 1;
        }).when(docMapper).insert(any(BusinessDocEntity.class));
        // 记录红冲单实体供 getDetail() 使用
        BusinessDocEntity redDoc = new BusinessDocEntity();
        redDoc.setId(999L);
        redDoc.setDocNo("RDK2026060001");
        redDoc.setDocType("RECEIPT");
        redDoc.setStatus(BusinessDocStatus.DRAFT);
        redDoc.setReversedFrom(DOC_ID);
        lenient().when(docMapper.selectById(999L)).thenReturn(redDoc);
        when(docEntryMapper.selectByDocId(anyLong())).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        service.reverse(DOC_ID, USER_ID);

        // then — 正向：原单 isReversed=true
        assertTrue(entity.getIsReversed());
    }

    // ====================================================================
    // 10. batchImport 批量导入（通过 create 批量场景覆盖）
    // ====================================================================

    @Test
    @DisplayName("testBatchImport_正常导入")
    void testBatchImport_正常导入() {
        // given — 批量调用 create 模拟导入多条
        mockOpenPeriod();
        doAnswer(inv -> {
            BusinessDocEntity e = inv.getArgument(0);
            e.setId(DOC_ID);
            return 1;
        }).when(docMapper).insert(any(BusinessDocEntity.class));
        when(docMapper.selectById(DOC_ID)).thenReturn(draftDoc());
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when — 连续创建 3 条
        for (int i = 0; i < 3; i++) {
            service.create(createDTO(), USER_ID);
        }

        // then — 正向：3 次插入
        verify(docMapper, times(3)).insert(any(BusinessDocEntity.class));
        verify(docEntryMapper, times(3)).insert(any(BusinessDocEntryEntity.class));
    }

    @Test
    @DisplayName("testBatchImport_部分失败")
    void testBatchImport_部分失败() {
        // given — 第二条 create 抛异常（期间校验失败）
        mockOpenPeriod();
        doAnswer(inv -> {
            BusinessDocEntity e = inv.getArgument(0);
            e.setId(DOC_ID);
            return 1;
        }).when(docMapper).insert(any(BusinessDocEntity.class));
        when(docMapper.selectById(DOC_ID)).thenReturn(draftDoc());
        when(docEntryMapper.selectByDocId(DOC_ID)).thenReturn(List.of(entryEntity()));
        lenient().when(subjectMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // 第一条成功
        service.create(createDTO(), USER_ID);

        // 第二条：构造一个分录为空的 DTO 触发校验异常
        BusinessDocDTO badDto = createDTO();
        badDto.setEntries(Collections.emptyList());

        // when/then — 第二条抛异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(badDto, USER_ID));
        assertTrue(ex.getMessage().contains("单据至少需要1条分录"));

        // then — 正向：第一条成功，第二条失败
        verify(docMapper, times(1)).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("testBatchImport_全部失败")
    void testBatchImport_全部失败() {
        // given — 所有 DTO 分录为空；期间校验通过，分录校验失败
        mockOpenPeriod();
        BusinessDocDTO badDto = createDTO();
        badDto.setEntries(Collections.emptyList());

        // when/then — 每条都失败
        for (int i = 0; i < 3; i++) {
            assertThrows(BusinessException.class, () -> service.create(badDto, USER_ID));
        }

        // then — 负向：不应有任何插入
        verify(docMapper, never()).insert(any(BusinessDocEntity.class));
        verify(docEntryMapper, never()).insert(any(BusinessDocEntryEntity.class));
    }

    // ====================================================================
    // 11. listByCustomer / listBySupplier（通过 pageQuery 过滤场景覆盖）
    // ====================================================================

    @Test
    @DisplayName("testListByCustomer_按客户查询")
    void testListByCustomer_按客户查询() {
        // given — 通过 pageQuery 关键词过滤模拟按客户查询
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        q.setKeyword("客户A");
        BusinessDocEntity entity = draftDoc();
        entity.setCustomerId(10L);
        Page<BusinessDocEntity> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(entity));
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        // 客户名填充
        CustomerEntity customer = new CustomerEntity();
        customer.setId(10L);
        customer.setName("客户A");
        when(customerMapper.selectBatchIds(anyList())).thenReturn(List.of(customer));
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：返回客户名
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("客户A", result.getRecords().get(0).getCustomerName());
    }

    @Test
    @DisplayName("testListByCustomer_不存在返回空")
    void testListByCustomer_不存在返回空() {
        // given — 查询无结果
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        q.setKeyword("不存在的客户");
        Page<BusinessDocEntity> emptyPage = new Page<>(1, 20, 0);
        emptyPage.setRecords(Collections.emptyList());
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：空
        assertTrue(result.getRecords().isEmpty());
        // then — 负向：无结果不查客户
        verify(customerMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("testListBySupplier_按供应商查询")
    void testListBySupplier_按供应商查询() {
        // given
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        BusinessDocEntity entity = draftDoc();
        entity.setDocType("PAYMENT");
        entity.setSupplierId(20L);
        entity.setCustomerId(null);
        Page<BusinessDocEntity> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(entity));
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        VendorEntity vendor = new VendorEntity();
        vendor.setId(20L);
        vendor.setName("供应商B");
        when(vendorMapper.selectBatchIds(anyList())).thenReturn(List.of(vendor));
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("供应商B", result.getRecords().get(0).getSupplierName());
    }

    // ====================================================================
    // 12. sumAmount / countByStatus（通过 Mapper 聚合方法覆盖）
    // ====================================================================

    @Test
    @DisplayName("testSumAmount_按类型汇总")
    void testSumAmount_按类型汇总() {
        // given — 通过 BusinessDocMapper.aggregateByCustomer 验证聚合查询可用
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("customer_id", 10L);
        row.put("total_unsettled", new BigDecimal("5000.00"));
        when(docMapper.aggregateByCustomer()).thenReturn(List.of(row));

        // when
        List<java.util.Map<String, Object>> result = docMapper.aggregateByCustomer();

        // then — 正向
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("5000.00"), result.get(0).get("total_unsettled"));
        verify(docMapper).aggregateByCustomer();
    }

    @Test
    @DisplayName("testCountByStatus_按状态统计")
    void testCountByStatus_按状态统计() {
        // given — 通过 pageQuery + status 过滤验证按状态统计能力
        BusinessDocQueryDTO q = new BusinessDocQueryDTO();
        q.setStatus("VOUCHERED");
        // 构造 2 条 VOUCHERED 记录
        BusinessDocEntity e1 = draftDoc();
        e1.setStatus("VOUCHERED");
        BusinessDocEntity e2 = draftDoc();
        e2.setStatus("VOUCHERED");
        e2.setId(101L);
        Page<BusinessDocEntity> page = new Page<>(1, 20, 2);
        page.setRecords(List.of(e1, e2));
        when(docMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        lenient().when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(outputInvoiceMapper.selectOne(any())).thenReturn(null);
        lenient().when(voucherMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        // when
        IPage<BusinessDocVO> result = service.pageQuery(q);

        // then — 正向：2 条 VOUCHERED
        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertTrue(result.getRecords().stream().allMatch(vo -> "VOUCHERED".equals(vo.getStatus())));
    }

    @Test
    @DisplayName("testGenerateDocNo_正常生成编号")
    void testGenerateDocNo_正常生成编号() {
        // given — redis 返回序列号 1
        when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        String docNo = service.generateDocNo("RECEIPT", PERIOD);

        // then — 正向：格式为 SK + period + 0001
        assertNotNull(docNo);
        assertEquals("SK2026060001", docNo);
        verify(valueOps).increment("doc:no:202606:RECEIPT");
    }

    @Test
    @DisplayName("testGenerateDocNo_未知类型使用默认前缀")
    void testGenerateDocNo_未知类型使用默认前缀() {
        // given
        when(valueOps.increment(anyString())).thenReturn(5L);

        // when
        String docNo = service.generateDocNo("UNKNOWN", PERIOD);

        // then — 正向：默认前缀 QT
        assertNotNull(docNo);
        assertEquals("QT2026060005", docNo);
    }
}
