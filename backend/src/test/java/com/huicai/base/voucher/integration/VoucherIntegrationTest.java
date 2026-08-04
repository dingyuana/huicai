package com.huicai.base.voucher.integration;

import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 凭证核心业务链路集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>调用真实的 VoucherService 方法，验证：
 * <ul>
 *   <li>凭证创建：DRAFT 状态、借贷平衡、分录写入</li>
 *   <li>凭证状态流转：DRAFT → SUBMITTED → AUDITED → POSTED</li>
 *   <li>凭证驳回：SUBMITTED → DRAFT</li>
 *   <li>反向操作：POSTED → AUDITED (unpost)</li>
 *   <li>逻辑删除：deleted=1 标记</li>
 * </ul>
 *
 * <p>这是 {@code VoucherControllerTest} 的补充：Controller 测试使用 @MockBean 只验证 HTTP 参数绑定，
 * 本测试使用真实 DB 验证 Service 层的业务逻辑。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
@DisplayName("凭证 - 核心业务链路集成测试")
public class VoucherIntegrationTest extends AbstractMapperTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private PeriodMapper periodMapper;

    @Autowired
    private SubjectBalanceService subjectBalanceService;

    private static final Long USER_ID = 1L;
    private static final Long VOUCHER_TYPE_ID = 1L;
    private static final String TEST_PERIOD = "202608";
    private Long debitSubjectId;
    private Long creditSubjectId;

    @BeforeEach
    void setUp() {
        // 企业上下文：MyBatis-Plus MetaObjectHandler 与数据权限拦截器都依赖此
        EnterpriseContextHolder.set(1L);

        // 创建测试会计期间
        PeriodEntity period = new PeriodEntity();
        period.setYear(2026);
        period.setMonth(8);
        period.setPeriodCode(TEST_PERIOD);
        period.setStartDate(java.time.LocalDate.of(2026, 8, 1));
        period.setEndDate(java.time.LocalDate.of(2026, 8, 31));
        period.setStatus("open");
        period.setEnterpriseId(1L);
        period.setDeleted(0);
        periodMapper.insert(period);

        // 创建测试科目（auto-generated ID）
        Subject s1 = new Subject();
        s1.setCode("E2E-1001");
        s1.setName("测试借方科目");
        s1.setDirection("debit");
        s1.setLevel(1);
        subjectMapper.insert(s1);
        debitSubjectId = s1.getId();

        Subject s2 = new Subject();
        s2.setCode("E2E-1002");
        s2.setName("测试贷方科目");
        s2.setDirection("credit");
        s2.setLevel(1);
        subjectMapper.insert(s2);
        creditSubjectId = s2.getId();

        // 期初前置强制：202608 是企业最早期，过账前必须已建账。
        // 用空 map 标记 entered（"确认期初为零"语义），使后续 post() 通过 validateOpeningBeforePost 校验。
        subjectBalanceService.initOpeningBalances(TEST_PERIOD, new java.util.HashMap<>());
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    @Test
    @DisplayName("创建凭证: 初始状态为 DRAFT，借贷平衡")
    void create_shouldBeDraftAndBalanced() {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202608");
        dto.setVoucherTypeId(VOUCHER_TYPE_ID);
        dto.setSummary("测试凭证-集成测试");

        VoucherCreateDTO.EntryDTO entry1 = new VoucherCreateDTO.EntryDTO();
        entry1.setSubjectId(debitSubjectId);
        entry1.setDebit(new BigDecimal("1000.00"));
        entry1.setCredit(BigDecimal.ZERO);
        entry1.setSummary("借：测试科目");

        VoucherCreateDTO.EntryDTO entry2 = new VoucherCreateDTO.EntryDTO();
        entry2.setSubjectId(creditSubjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("1000.00"));
        entry2.setSummary("贷：测试科目");

        dto.setEntries(List.of(entry1, entry2));

        VoucherVO vo = voucherService.create(dto, USER_ID);

        assertNotNull(vo.getId(), "创建后应有 ID");
        assertEquals("DRAFT", vo.getStatus(), "新建凭证状态应为 DRAFT");
        assertEquals(0, new BigDecimal("1000.00").compareTo(vo.getTotalDebit()), "总借方应为 1000");
        assertEquals(0, new BigDecimal("1000.00").compareTo(vo.getTotalCredit()), "总贷方应为 1000");
        assertEquals("202608", vo.getPeriod());
    }

    @Test
    @DisplayName("凭证状态流转: DRAFT → SUBMITTED → AUDITED → POSTED")
    void fullStatusTransition_shouldSucceed() {
        // 创建
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202608");
        dto.setVoucherTypeId(VOUCHER_TYPE_ID);
        dto.setSummary("状态流转测试");
        VoucherCreateDTO.EntryDTO entry1 = new VoucherCreateDTO.EntryDTO();
        entry1.setSubjectId(debitSubjectId);
        entry1.setDebit(new BigDecimal("500.00"));
        entry1.setCredit(BigDecimal.ZERO);
        VoucherCreateDTO.EntryDTO entry2 = new VoucherCreateDTO.EntryDTO();
        entry2.setSubjectId(creditSubjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("500.00"));
        dto.setEntries(List.of(entry1, entry2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();

        // 提交: DRAFT → SUBMITTED
        voucherService.submit(id, USER_ID);
        VoucherVO submitted = voucherService.getDetail(id);
        assertEquals("SUBMITTED", submitted.getStatus(), "提交后状态应为 SUBMITTED");

        // 审核: SUBMITTED → AUDITED
        voucherService.audit(id, USER_ID);
        VoucherVO audited = voucherService.getDetail(id);
        assertEquals("AUDITED", audited.getStatus(), "审核后状态应为 AUDITED");

        // 过账: AUDITED → POSTED
        voucherService.post(id, USER_ID);
        VoucherVO posted = voucherService.getDetail(id);
        assertEquals("POSTED", posted.getStatus(), "过账后状态应为 POSTED");
    }

    @Test
    @DisplayName("凭证驳回: SUBMITTED → DRAFT")
    void reject_shouldReturnToDraft() {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202608");
        dto.setVoucherTypeId(VOUCHER_TYPE_ID);
        dto.setSummary("驳回测试");
        VoucherCreateDTO.EntryDTO entry1 = new VoucherCreateDTO.EntryDTO();
        entry1.setSubjectId(debitSubjectId);
        entry1.setDebit(new BigDecimal("200.00"));
        entry1.setCredit(BigDecimal.ZERO);
        VoucherCreateDTO.EntryDTO entry2 = new VoucherCreateDTO.EntryDTO();
        entry2.setSubjectId(creditSubjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("200.00"));
        dto.setEntries(List.of(entry1, entry2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();

        // 提交
        voucherService.submit(id, USER_ID);
        VoucherVO submitted = voucherService.getDetail(id);
        assertEquals("SUBMITTED", submitted.getStatus());

        // 驳回
        voucherService.reject(id, USER_ID, "摘要不完整，请补充");
        VoucherVO rejected = voucherService.getDetail(id);
        assertEquals("DRAFT", rejected.getStatus(), "驳回后状态应回到 DRAFT");
    }

    @Test
    @DisplayName("反过账: POSTED → AUDITED")
    void unpost_shouldReturnToAudited() {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202608");
        dto.setVoucherTypeId(VOUCHER_TYPE_ID);
        dto.setSummary("反过账测试");
        VoucherCreateDTO.EntryDTO entry1 = new VoucherCreateDTO.EntryDTO();
        entry1.setSubjectId(debitSubjectId);
        entry1.setDebit(new BigDecimal("300.00"));
        entry1.setCredit(BigDecimal.ZERO);
        VoucherCreateDTO.EntryDTO entry2 = new VoucherCreateDTO.EntryDTO();
        entry2.setSubjectId(creditSubjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("300.00"));
        dto.setEntries(List.of(entry1, entry2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();

        // 过账全流程
        voucherService.submit(id, USER_ID);
        voucherService.audit(id, USER_ID);
        voucherService.post(id, USER_ID);
        VoucherVO posted = voucherService.getDetail(id);
        assertEquals("POSTED", posted.getStatus());

        // 反过账
        voucherService.unpost(id, USER_ID);
        VoucherVO unposted = voucherService.getDetail(id);
        assertEquals("AUDITED", unposted.getStatus(), "反过账后状态应回到 AUDITED");
    }

    @Test
    @DisplayName("逻辑删除: 删除后查询应抛异常")
    void delete_shouldMarkDeleted() {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202608");
        dto.setVoucherTypeId(VOUCHER_TYPE_ID);
        dto.setSummary("删除测试");
        VoucherCreateDTO.EntryDTO entry1 = new VoucherCreateDTO.EntryDTO();
        entry1.setSubjectId(debitSubjectId);
        entry1.setDebit(new BigDecimal("100.00"));
        entry1.setCredit(BigDecimal.ZERO);
        VoucherCreateDTO.EntryDTO entry2 = new VoucherCreateDTO.EntryDTO();
        entry2.setSubjectId(creditSubjectId);
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("100.00"));
        dto.setEntries(List.of(entry1, entry2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();

        // 删除
        voucherService.delete(id);

        // 逻辑删除后，getDetail 应返回 null 或抛异常
        assertThrows(Exception.class, () -> voucherService.getDetail(id),
                "删除后查询详情应抛异常");
    }
}