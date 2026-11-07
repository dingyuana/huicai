package com.huicai.security;

import com.huicai.base.report.entity.VoucherCashFlowEntity;
import com.huicai.base.report.mapper.VoucherCashFlowMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.cash.entity.BankReconciliationLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P153 发票导入 500：🔴 真库写入守卫 —— <b>RLS 租户实体的 INSERT 必须携带
 * 上下文 enterprise_id，而不是靠列默认值 / 落 NULL</b>。
 *
 * <h2>生产事故（2026-11-05 王会计导入发票，t_audit_log id=1238 SERVER_ERROR 在案）</h2>
 * <pre>
 * PSQLException: new row violates row-level security policy for table "t_subject"
 *   ### SQL: INSERT INTO t_subject (code, name, level, ...) VALUES (...)
 *                                              ↑ 列清单里没有 enterprise_id
 * </pre>
 * 链条：{@code Subject.enterpriseId} 是<b>裸字段</b>（无 {@code @TableField(fill=INSERT)}）
 * ⇒ MyBatis-Plus 的 INSERT 列清单按 fill 标记生成，该列整个缺席
 * ⇒ {@code MyMetaObjectHandler} 往实体 set 了值也没用（列根本不在语句里）
 * ⇒ DB 用 {@code DEFAULT 1} 兜底 —— <b>企业 1 账套恰好「碰巧正确」，所以该缺陷
 * 潜伏至今只在企业 2/3/4/5 爆发</b>：默认值 1 ≠ 上下文 4 ⇒ RLS WITH CHECK 拒 ⇒ 500。
 * 王会计（AGENCY，切在客户账套）导入发票时 {@code ensureStandardSubjects()}
 * 自动建科目 ⇒ 必炸。<b>同一次点击里「新增科目」按钮也是同一死法</b>
 * （2026-11-05 实机：shandong_c（ent4）POST /v1/subjects ⇒ 500，日志
 * {@code violates row-level security policy for table "t_subject"} ×5）。
 *
 * <h2>为什么真写而不是静态扫描</h2>
 * 源码上有<b>两种</b>成因都会掉进同一个洞，静态扫描分不开（AGENTS「扫描器三次误配」）：
 * ①裸字段无 fill；②BaseEntity 子类<b>重复声明</b>同名 enterpriseId 覆盖父类 fill
 * （Lombok 字段遮蔽，MP 按子类裸字段建模）。唯一可靠判据：<b>真做一次 INSERT
 * 回读 enterprise_id</b>。
 *
 * <h2>为什么上下文必须选企业 2 而不是基类默认 1</h2>
 * {@code DEFAULT_ENTERPRISE_ID=1} 与列默认值 1 <b>同值</b> —— 漏填也能「通过」，
 * 毫无鉴别力。本守卫全部切到企业 2：漏 fill ⇒ 落库 1 ⇒ 断言转红。
 */
@DisplayName("SPEC-P153：RLS 实体 INSERT 必须落上下文 enterprise_id（发票导入 500 复发锁）")
class TenantInsertEnterpriseFillRealDBTest extends AbstractMapperTest {

    /** 鉴别用企业：≠ 列默认值 1（见类注释）。 */
    private static final Long CTX = 2L;

    @Autowired
    private com.huicai.base.system.mapper.SubjectMapper probeSubjectMapper;
    @Autowired
    private VoucherCashFlowMapper voucherCashFlowMapper;
    @Autowired
    private com.huicai.base.voucher.mapper.VoucherMapper voucherMapper;
    @Autowired
    private com.huicai.sme.cash.mapper.BankReconciliationLogMapper bankReconciliationLogMapper;
    @Autowired
    private com.huicai.base.system.mapper.VoucherTypeMapper voucherTypeMapper;

    @Test
    @DisplayName("Subject（发票导入 ensureSubject 自动建科目）：INSERT 落上下文企业而非 DEFAULT 1")
    void subjectInsertCarriesContextEnterprise() {
        useEnterprise(CTX);
        Subject s = new Subject();
        s.setCode("P153-PROBE");
        s.setName("P153探针科目");
        s.setLevel(1);
        s.setDirection("debit");
        s.setIsLeaf(true);
        s.setIsActive(true);

        probeSubjectMapper.insert(s);
        assertNotNull(s.getId(), "探针科目未写入");

        Long stored = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_subject WHERE id = ?", Long.class, s.getId());
        assertEquals(CTX, stored,
                "t_subject.enterprise_id 落库为 " + stored + "（上下文=" + CTX + "）"
                        + " ⇒ INSERT 语句不带 enterprise_id 列（实体缺 fill 标记），"
                        + "值来自 DB DEFAULT 1 —— 生产语义：企业 1 静默串数据、"
                        + "其余账套被 RLS WITH CHECK 拒 500（本次事故形态）");
        // RLS 可见性回读：走 mapper（经 GUC + 应用层租户注入）——「写进去且查得回」
        assertNotNull(probeSubjectMapper.selectById(s.getId()),
                "enterprise_id 正确但按上下文企业查不回 ⇒ 读面谓词与写面口径漂移");
    }

    @Test
    @DisplayName("VoucherCashFlow（凭证现金流量分配）：同型缺陷同锁（DEFAULT 1 静默串账套）")
    void voucherCashFlowInsertCarriesContextEnterprise() {
        useEnterprise(CTX);
        // fk_cf_voucher 外键真实存在 ⇒ 先造一张真凭证。凭证类型<b>自造</b>：
        // 全新测试库里企业 2 是运行时注册租户，V183 只回补建库时已存在的企业
        // ⇒ 预取可能为空（V192 同族冷启动差异），依赖种子不如自己造。
        com.huicai.base.system.entity.VoucherTypeEntity vt =
                new com.huicai.base.system.entity.VoucherTypeEntity();
        vt.setCode("P153T");
        vt.setName("P153探针类型");
        vt.setIsActive(true);
        voucherTypeMapper.insert(vt);
        Long typeId = vt.getId();
        assertNotNull(typeId, "探针凭证类型未写入");
        com.huicai.base.voucher.entity.VoucherEntity v = new com.huicai.base.voucher.entity.VoucherEntity();
        v.setVoucherNo("P153-PROBE-V");
        v.setPeriod("202407");
        v.setVoucherTypeId(typeId);
        v.setStatus("DRAFT");
        voucherMapper.insert(v);

        VoucherCashFlowEntity cf = new VoucherCashFlowEntity();
        cf.setVoucherId(v.getId());
        cf.setFlowType("OPERATING_IN");
        cf.setAmount(BigDecimal.ONE);
        voucherCashFlowMapper.insert(cf);

        Long stored = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_voucher_cash_flow WHERE id = ?", Long.class, cf.getId());
        assertEquals(CTX, stored,
                "t_voucher_cash_flow.enterprise_id 落库为 " + stored
                        + " ⇒ 漏 fill 被 DEFAULT 1 接住：企业 2 的凭证现金流挂到企业 1"
                        + "名下（RLS 下企业 2 永远查不到自己的现金流 = 静默数据丢失）");
    }

    @Test
    @DisplayName("BankReconciliationLog（影子声明覆盖父类 fill）：enterprise_id 不得落 NULL")
    void bankReconciliationLogInsertCarriesContextEnterprise() {
        useEnterprise(CTX);
        BankReconciliationLogEntity log = new BankReconciliationLogEntity();
        log.setStatementId(-1L);   // 本表无外键（V156 建表时即无 FK），-1 探针值可用
        log.setAction("CONFIRM");
        log.setStatusAfter("MATCHED");   // NOT NULL
        log.setOperator("p153-probe");
        bankReconciliationLogMapper.insert(log);

        Long stored = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_bank_reconciliation_log WHERE id = ?", Long.class, log.getId());
        assertEquals(CTX, stored,
                "t_bank_reconciliation_log.enterprise_id 落库为 " + stored
                        + " ⇒ 子类重复声明遮蔽了 BaseEntity 的 fill：INSERT 漏列 ⇒ NULL，"
                        + "而 RLS 谓词 enterprise_id=NULL 恒假 ⇒ 日志写了但永远查不到"
                        + "（本表生产 0 行与此一致）");
    }

    /**
     * 结构面（辅判据）：BaseEntity 子类重复声明 enterpriseId = Lombok 字段遮蔽，
     * 静态看父类一切正常、运行时列清单漏列。<b>白名单只允许缩小</b>（§4.5 第 43 条），
     * 且每条必须写明「为什么是有意的」。
     */
    @Test
    @DisplayName("结构面：BaseEntity 子类不得遮蔽 enterpriseId（白名单外的遮蔽一律转红）")
    void noShadowEnterpriseIdDeclarationOverBaseEntity() throws IOException {
        List<String> whitelist = List.of(
                // t_contract 已由 V177 撤 RLS（代理端跨客户表），创建时【必须】由
                // ContractServiceImpl 显式指定目标客户企业（dto.enterpriseId），
                // 父类 fill 的「无条件覆盖为上下文企业」反而会把合同挂错账套。
                // ⇒ 这里的遮蔽是刻意绕过 fill，不是缺陷。
                "ContractEntity");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                if (!src.contains("extends BaseEntity") || !src.contains("@TableName")) {
                    continue;
                }
                // 🔴 必须先剥注释再匹配：BankReconciliationLogEntity 的修复说明注释里
                // 引用了被删掉的 `private Long enterpriseId;` 原文，不剥注释守卫会
                // 把「记录删除事实的注释」当成缺陷本身（首跑即被此绊倒）。
                String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
                if (Pattern.compile("private\\s+Long\\s+enterpriseId\\s*;").matcher(code).find()) {
                    String cls = p.getFileName().toString().replace(".java", "");
                    if (!whitelist.contains(cls)) {
                        violations.add(cls + "（遮蔽父类 fill ⇒ INSERT 漏 enterprise_id 列）");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "BaseEntity 子实体遮蔽 enterpriseId 导致 INSERT 漏列（修法：删掉子类的重复声明，"
                        + "确实需要绕过 fill 的加入白名单并写明业务理由）：\n  "
                        + String.join("\n  ", violations));
    }
}
