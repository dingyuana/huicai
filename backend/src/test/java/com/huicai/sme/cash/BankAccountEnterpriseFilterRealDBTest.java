package com.huicai.sme.cash;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.service.BankAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P129 §1③ / AT-129-7 / AT-129-9：银行账号<b>按企业筛选</b>的真实 DB 测试。
 *
 * <p>🔴 <b>本类专门防「静默空列表」</b>：{@code t_bank_account} 开了 FORCE RLS，
 * 只加 {@code eq(enterpriseId, X)} 而不建立上下文时，X ≠ 当前企业的查询会被
 * 滤成<b>空列表且返回 200</b> —— 端到端实测过（企业 2 明明有账号，接口返 0）。
 * 那比抛错危险得多：用户只会以为「这家没账号」，而真相是「权限层把它藏了」。
 *
 * <p>⚠️ 因此断言必须是「<b>能查到</b>」而不是「不报错」：
 * 只断言 HTTP 200 的用例在这个缺陷下会全绿。
 */
@DisplayName("SPEC-P129：银行账号按企业筛选（真实 DB，FORCE RLS 下）")
class BankAccountEnterpriseFilterRealDBTest extends AbstractMapperTest {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 直接 JDBC 建企业：绕开状态机与角色守卫，只为给筛选测试准备数据。
     *
     * <p>⚠️ <b>不能显式给 id</b>：{@code t_enterprise.id} 是
     * {@code GENERATED ALWAYS AS IDENTITY}，显式插值报
     * {@code cannot insert a non-DEFAULT value into column ...}。
     */
    private Long newEnterprise(String code) {
        jdbcTemplate.update("INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, "
                        + "status, seed_data_done, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, 'SME', 'ACTIVE', true, NOW(), NOW(), 0) "
                        + "ON CONFLICT (enterprise_code) DO NOTHING",
                code, "P129筛选测试企业-" + code);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_enterprise WHERE enterprise_code = ?", Long.class, code);
    }

    /** 在指定企业下造一个账号（切上下文而非硬塞实体，§4.5 第 23 条） */
    private Long newAccount(Long enterpriseId, String tag) {
        useEnterprise(enterpriseId);
        BankAccountEntity e = new BankAccountEntity();
        e.setAccountNo("9999.P129." + tag);
        e.setAccountName("P129测试账户-" + tag);
        e.setBankName("P129测试银行");
        e.setIsActive(true);
        bankAccountMapper.insert(e);
        return e.getId();
    }

    private Set<Long> idsOf(IPage<BankAccountEntity> p) {
        Set<Long> s = new HashSet<>();
        p.getRecords().forEach(r -> s.add(r.getId()));
        return s;
    }

    @Test
    @DisplayName("🔴 跨企业必须真查到：企业 B 的账号在企业 B 的筛选下可见（不是静默空列表）")
    void crossEnterpriseQueryReturnsRows() {
        Long entA = newEnterprise("P129-FLT-A");
        Long entB = newEnterprise("P129-FLT-B");
        Long accB = newAccount(entB, "FLT-B");
        Long accA = newAccount(entA, "FLT-A");

        // 上下文停在企业 A —— 这正是修复前会返空列表的场景
        useEnterprise(entA);
        assertEquals(entA, EnterpriseContextHolder.get(), "前提：当前上下文应为企业 A");

        IPage<BankAccountEntity> pageB =
                bankAccountService.pageQueryAcrossEnterprise(null, 1, 20, entB);

        assertTrue(idsOf(pageB).contains(accB),
                "在企业 B 的筛选下必须查到企业 B 的账号 " + accB + "，实际查到=" + idsOf(pageB)
                        + "。若为空且不报错，说明又退回「只加 eq 条件」的旧实现"
                        + "（FORCE RLS 会把它滤成空）");
        assertTrue(!idsOf(pageB).contains(accA),
                "企业 B 的筛选结果不得包含企业 A 的账号 " + accA);
    }

    @Test
    @DisplayName("AT-129-9 回归锁：不传 enterpriseId 时行为与改动前完全一致")
    void noEnterpriseIdKeepsOldBehavior() {
        Long entA = newEnterprise("P129-REG-A");
        Long accA = newAccount(entA, "REG-A");

        useEnterprise(entA);
        IPage<BankAccountEntity> legacy = bankAccountService.pageQuery(null, 1, 20);
        IPage<BankAccountEntity> same =
                bankAccountService.pageQueryAcrossEnterprise(null, 1, 20, null);

        assertEquals(legacy.getTotal(), same.getTotal(),
                "不传 enterpriseId 时，新旧方法结果必须一致");
        assertTrue(idsOf(same).contains(accA), "不传时应能查到当前企业自己的账号");
    }

    @Test
    @DisplayName("🔴 查询后上下文必须复原：不得污染同一请求内的后续读")
    void contextIsRestoredAfterQuery() {
        Long entA = newEnterprise("P129-RST-A");
        Long entB = newEnterprise("P129-RST-B");
        newAccount(entB, "RST-B");

        useEnterprise(entA);
        bankAccountService.pageQueryAcrossEnterprise(null, 1, 20, entB);

        assertEquals(entA, EnterpriseContextHolder.get(),
                "跨企业查询结束后上下文必须复原到原值，否则后续读会串租户");
    }

    @Test
    @DisplayName("筛当前企业时照常返回（不触发无谓的 GUC 写入）")
    void sameEnterpriseStillReturnsRows() {
        Long entA = newEnterprise("P129-SAME-A");
        Long accA = newAccount(entA, "SAME-A");

        useEnterprise(entA);
        IPage<BankAccountEntity> page =
                bankAccountService.pageQueryAcrossEnterprise(null, 1, 20, entA);

        assertTrue(idsOf(page).contains(accA), "筛当前企业应正常返回");
        assertEquals(entA, EnterpriseContextHolder.get(), "筛当前企业后上下文不应变化");
    }
}
