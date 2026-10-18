package com.huicai.base.voucher;

import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P128 §7.3 遗留修复的真实 DB 测试：凭证类型的科目限制须<b>按 code 生效</b>。
 *
 * <p>🔴 <b>这个缺陷在企业 1 上永不发作</b>：V183 补种子前，JZ/SK/FK/ZZ 的主键恰好是 1/2/3/4，
 * 与 {@code VoucherType} 常量逐一相等；V183 给企业 2/3/4 补种子后它们的实际主键变成 5~8，
 * 于是
 * <pre>
 * if (voucherTypeId == VoucherType.SK &amp;&amp; !hasCashBankInDebit) throw ...
 * </pre>
 * 对企业 2/3/4 <b>一次都不会进入</b> ⇒ 收款凭证不挂现金科目、转账凭证塞现金科目，<b>都不拦</b>。
 *
 * <p>🔴 <b>为什么结构守卫不够</b>：{@code VoucherTypeHardcodeStructureTest} 能证明
 * 「代码里不再引用全局 id 常量」，但<b>不能证明改完之后校验真的会触发</b> ——
 * 例如把 code 归一化写错（DB 存小写而代码比大写），守卫照样全绿，校验却依然恒不触发。
 * 故本测试走<b>真实业务读路径</b>（{@code VoucherService#create}）验行为。
 *
 * <p>🔴 <b>自建企业的原因</b>：L2 的 Testcontainers 库里 {@code t_enterprise} 只有默认企业 1。
 * 若依赖库里现成的多企业，本用例会退化为「只验企业 1」—— 而那恰恰是旧代码本来就正常的那一家，
 * <b>鉴别力为零</b>。
 */
@DisplayName("SPEC-P128 §7.3：凭证类型科目校验按 code 生效（非仅企业 1）（真实 DB）")
class VoucherTypeLimitByCodeRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private PeriodMapper periodMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long otherEnterpriseId;
    private Long skTypeId;
    private Long fkTypeId;
    private Long zzTypeId;
    private Long jzTypeId;
    private Long debitSubjectId;
    private Long creditSubjectId;
    private Long cashSubjectId;
    private String testPeriod;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status, "
                        + "seed_data_done, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, 'SME', 'ACTIVE', true, NOW(), NOW(), 0)",
                "P128LIM" + System.nanoTime() % 100000, "P128 限额校验测试企业");
        otherEnterpriseId = jdbcTemplate.queryForObject(
                "SELECT max(id) FROM t_enterprise WHERE enterprise_code LIKE 'P128LIM%'",
                Long.class);

        // 🔴 §4.5 第 23 条：造数前先切上下文，让 MetaObjectHandler 落正确的 enterprise_id
        useEnterprise(otherEnterpriseId);

        for (String code : List.of("JZ", "SK", "FK", "ZZ")) {
            jdbcTemplate.update(
                    "INSERT INTO t_voucher_type (enterprise_id, code, name, sort_order, "
                            + "numbering_rule, is_active, created_at, updated_at, deleted) "
                            + "VALUES (?, ?, ?, 1, '{type}-{serial}', true, NOW(), NOW(), 0)",
                    otherEnterpriseId, code, code + "凭证");
        }
        jzTypeId = typeIdOf("JZ");
        skTypeId = typeIdOf("SK");
        fkTypeId = typeIdOf("FK");
        zzTypeId = typeIdOf("ZZ");

        testPeriod = "202609";
        jdbcTemplate.update(
                "INSERT INTO t_period (enterprise_id, year, month, period_code, start_date, end_date, "
                        + "status, opening_status, deleted, created_at, updated_at) "
                        + "VALUES (?, 2026, 9, ?, DATE '2026-09-01', DATE '2026-09-30', "
                        + "'open', 'entered', 0, NOW(), NOW())",
                otherEnterpriseId, testPeriod);

        // 🔴 夹具一律走 jdbcTemplate 显式带 enterprise_id，<b>不用</b> subjectMapper.insert()：
        // 后者依赖 MyMetaObjectHandler.insertFill 填企业，而 fill 取的是线程上下文 ——
        // 首版用它建「1001」时实际填成了企业 1，撞上企业 1 已有的 1001（uq_subject_code_ent）
        // 报 DuplicateKey。显式 SQL 让归属一眼可查，也符合「造数要显式」的纪律。
        debitSubjectId = insertSubject("LIM-6001", "限额测试借方科目", "debit");
        creditSubjectId = insertSubject("LIM-6002", "限额测试贷方科目", "credit");

        // 🔴 自建一个现金科目：本方法自建的企业是新账套，库中<b>没有</b>现金/银行科目。
        // 而 SubjectMapper.getCashBankCodes() 的裸 SQL 会被 EnterpriseDataPermissionInterceptor
        // 自动注入当前企业过滤 ⇒ 新企业查不到任何 1001/1002 ⇒ 返回空集；
        // validateVoucherTypeLimit 在该集合为空时<b>直接 return</b>，本类全部「应被拒」用例
        // 都会恒绿 —— 即假绿（首版正是这样被前提用例抓出来的）。
        // 这同时暴露一个真实缺口：**企业未建现金/银行科目 ⇒ 凭证类型限制完全不生效**（fail-open）。
        cashSubjectId = insertSubject("1001", "库存现金", "debit");
    }

    /** 显式建科目并回读主键（列名已对 V1__baseline.sql 的 t_subject 定义查证） */
    private Long insertSubject(String code, String name, String direction) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_subject (enterprise_id, code, name, level, direction, is_leaf, "
                        + "is_active, deleted, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, ?, true, true, 0, NOW(), NOW()) RETURNING id",
                Long.class, otherEnterpriseId, code, name, direction);
    }

    private Long typeIdOf(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_type WHERE enterprise_id = ? AND code = ? AND deleted = 0",
                Long.class, otherEnterpriseId, code);
    }

    // ── 自证前提：缺了这些，本类全部断言都无鉴别力 ──

    @Test
    @DisplayName("前提：本企业四种凭证类型的主键都 ≠ 企业 1 的 1/2/3/4（否则等于没测非默认企业）")
    void premisePrimaryKeysDifferFromEnterpriseOne() {
        assertNotEquals(1L, jzTypeId, "JZ 主键撞 1，本用例退化为「只验企业 1」，鉴别力为零");
        assertNotEquals(2L, skTypeId, "SK 主键撞 2 ⇒ 旧代码恒成立，无法反证");
        assertNotEquals(3L, fkTypeId, "FK 主键撞 3 ⇒ 旧代码恒成立，无法反证");
        assertNotEquals(4L, zzTypeId, "ZZ 主键撞 4 ⇒ 旧代码恒成立，无法反证");
    }

    @Test
    @DisplayName("前提：现金/银行科目集合非空（否则 validateVoucherTypeLimit 会提前 return）")
    void premiseCashBankCodesNotEmpty() {
        assertFalse(subjectMapper.getCashBankCodes().isEmpty(),
                "SubjectMapper.getCashBankCodes() 为空 ⇒ validateVoucherTypeLimit 在"
                        + "「没有现金银行科目就跳过」分支直接返回，本类全部用例恒绿 = 假绿");
    }

    @Test
    @DisplayName("前提：本用例的借方科目确实不属于现金/银行类")
    void premiseDebitSubjectIsNotCashBank() {
        List<String> cashBank = subjectMapper.getCashBankCodes();
        assertTrue(cashBank.stream().noneMatch(c -> debitSubjectId.toString().equals(c)),
                "借方科目 id 意外落入现金银行集合，会让 hasCashBankInDebit 恒 true");
    }

    // ── 真正的鉴别性用例 ──

    @Test
    @DisplayName("收款凭证借方不挂现金/银行科目 ⇒ 必须被拒（修复前对企业 ≠ 1 恒放行）")
    void skWithoutCashBankInDebitIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherService.create(skVoucher("借方不挂现金科目"), USER_ID));
        assertTrue(ex.getMessage().contains("收款凭证"),
                "须报收款凭证的规则；实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("付款凭证贷方不挂现金/银行科目 ⇒ 必须被拒")
    void fkWithoutCashBankInCreditIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherService.create(fkVoucher("贷方不挂现金科目"), USER_ID));
        assertTrue(ex.getMessage().contains("付款凭证"),
                "须报付款凭证的规则；实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("转账凭证挂现金/银行科目 ⇒ 必须被拒")
    void zzWithCashBankIsRejected() {
        // 借方挂现金科目（本类自建，见 setUp）
        VoucherCreateDTO dto = voucherWith(zzTypeId, "转账凭证带现金科目");
        dto.getEntries().get(0).setSubjectId(cashSubjectId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherService.create(dto, USER_ID));
        assertTrue(ex.getMessage().contains("转账凭证"),
                "须报转账凭证的规则；实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("记账凭证不做科目限制（正向对照：修复不能把 JZ 也拦掉）")
    void jzIsExemptFromLimit() {
        VoucherVO vo = voucherService.create(voucherWith(jzTypeId, "记账凭证借方不挂现金科目"), USER_ID);
        assertEquals("DRAFT", vo.getStatus(), "记账凭证应放行并建成 DRAFT");
    }

    @Test
    @DisplayName("收款凭证借方挂了现金/银行科目 ⇒ 放行（负向对照：不能一律拒绝）")
    void skWithCashBankInDebitIsAllowed() {
        VoucherCreateDTO dto = voucherWith(skTypeId, "收款凭证借方挂现金科目");
        dto.getEntries().get(0).setSubjectId(cashSubjectId);

        VoucherVO vo = voucherService.create(dto, USER_ID);
        assertEquals("DRAFT", vo.getStatus(), "合规的收款凭证应放行");
    }

    @Test
    @DisplayName("他企业的凭证类型 id 在本企业上下文下取不到（隔离前提，防止测试用错 id）")
    void foreignTypeIdIsNotUsable() {
        Long defaultJz = jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_type WHERE enterprise_id = 1 AND code = 'JZ' AND deleted = 0",
                Long.class);
        if (defaultJz != null && !defaultJz.equals(jzTypeId)) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.create(voucherWith(defaultJz, "用他企业凭证类型"), USER_ID));
            assertTrue(ex.getMessage().contains("不属于当前企业"),
                    "应报「不属于当前企业」；实际: " + ex.getMessage());
        }
    }

    // ── 夹具 ──

    private VoucherCreateDTO skVoucher(String summary) {
        return voucherWith(skTypeId, summary);
    }

    private VoucherCreateDTO fkVoucher(String summary) {
        return voucherWith(fkTypeId, summary);
    }

    /** 构造一张借贷平衡、分录合法、但两个科目都不是现金/银行的凭证 */
    private VoucherCreateDTO voucherWith(Long voucherTypeId, String summary) {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(testPeriod);
        dto.setVoucherTypeId(voucherTypeId);
        dto.setSummary(summary);

        VoucherCreateDTO.EntryDTO debit = new VoucherCreateDTO.EntryDTO();
        debit.setSubjectId(debitSubjectId);
        debit.setDebit(new BigDecimal("100.00"));
        debit.setCredit(BigDecimal.ZERO);
        debit.setSortOrder(1);

        VoucherCreateDTO.EntryDTO credit = new VoucherCreateDTO.EntryDTO();
        credit.setSubjectId(creditSubjectId);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(new BigDecimal("100.00"));
        credit.setSortOrder(2);

        dto.setEntries(List.of(debit, credit));
        return dto;
    }
}