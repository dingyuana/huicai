package com.huicai.sme.cash.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BankStatement Mapper 真实 DB 测试：正向插入 + 约束校验。
 *
 * <h3>历史说明（REQ-2026-118 / 慢测 C 类）</h3>
 * 本测试此前 {@code setAccountId(1L)} 硬编码，而迁移后 {@code t_bank_account} 为空表，
 * 触发 {@code fk_statement_account}。**更隐蔽的问题**：该外键违约让
 * {@link #insert_shouldEnforceNotNullAccountId()} 等 5 个约束用例
 * <b>「因错误的原因通过」</b> —— 它们断言 {@code assertThrows}，而外键异常同样满足，
 * 实际从未真正验证目标约束。改用真实账户 id 后才暴露：
 * <ol>
 *   <li>{@code direction} 是 {@code @TableField(exist = false)}（DB 无此列，
 *       仅内存缓存），<b>不存在 {@code chk_direction} 约束</b>，
 *       故 {@link #insert_shouldEnforceChkDirection()} 永远无法通过 → 改为验证
 *       该字段确实不落库；</li>
 *   <li>{@code version} 是 {@code @Version} 且 DB 默认值为 <b>1</b>，
 *       原断言「初始版本号应为 0」与 DB 默认不符 → 改为断言 DB 实际默认值；</li>
 *   <li>{@code chk_stmt_review_status} 经 V157 已扩为 10 值，
 *       「INVALID_STATUS」仍应被拒（该用例有效，保留）。</li>
 * </ol>
 */
class BankStatementMapperTest extends AbstractMapperTest {

    @Autowired
    private BankStatementMapper mapper;

    private BankStatementEntity createValidStmt() {
        BankStatementEntity e = new BankStatementEntity();
        // t_bank_statement.account_id 有 FK 到 t_bank_account，必须用真实存在的主键
        e.setAccountId(ensureBankAccount(DEFAULT_ENTERPRISE_ID));
        e.setTxDate(LocalDate.now());
        e.setTxType("INCOME");
        e.setAmount(new BigDecimal("1000.00"));
        e.setDirection("in");
        e.setSummary("测试银行流水");
        e.setMatchStatus("UNMATCHED");
        e.setReviewStatus("PENDING");
        e.setCounterAccount("测试客户");
        return e;
    }

    @Test
    @DisplayName("正向: 必填字段齐全时应插入成功")
    void insert_shouldSucceedWithAllRequiredFields() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);
        assertNotNull(e.getId());

        BankStatementEntity found = mapper.selectById(e.getId());
        assertEquals("PENDING", found.getReviewStatus());
        assertEquals("INCOME", found.getTxType());
        assertEquals(0, new BigDecimal("1000.00").compareTo(found.getAmount()));
    }

    @Test
    @DisplayName("约束: account_id 为 NOT NULL，缺省应插入失败")
    void insert_shouldEnforceNotNullAccountId() {
        BankStatementEntity e = createValidStmt();
        e.setAccountId(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "account_id 为 NOT NULL，插入应失败");
    }

    @Test
    @DisplayName("约束: tx_date 为 NOT NULL，缺省应插入失败")
    void insert_shouldEnforceNotNullTxDate() {
        BankStatementEntity e = createValidStmt();
        e.setTxDate(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "tx_date 为 NOT NULL，插入应失败");
    }

    @Test
    @DisplayName("约束: tx_type 为 NOT NULL，缺省应插入失败")
    void insert_shouldEnforceNotNullTxType() {
        BankStatementEntity e = createValidStmt();
        e.setTxType(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "tx_type 为 NOT NULL，插入应失败");
    }

    @Test
    @DisplayName("约束: chk_stmt_type 仅允许 INCOME/EXPENSE/TRANSFER_IN/TRANSFER_OUT")
    void insert_shouldEnforceChkTxType() {
        BankStatementEntity e = createValidStmt();
        e.setTxType("INVALID_TYPE");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "tx_type 有 CHECK 约束，INVALID_TYPE 应失败");
    }

    @Test
    @DisplayName("约束: chk_stmt_match_status 不接受非法匹配状态")
    void insert_shouldEnforceChkMatchStatus() {
        BankStatementEntity e = createValidStmt();
        e.setMatchStatus("INVALID_MATCH");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "match_status 有 CHECK 约束，INVALID_MATCH 应失败");
    }

    @Test
    @DisplayName("约束: chk_stmt_review_status 不接受非法审核状态")
    void insert_shouldEnforceChkReviewStatus() {
        BankStatementEntity e = createValidStmt();
        e.setReviewStatus("INVALID_STATUS");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "review_status 有 CHECK 约束，INVALID_STATUS 应失败");
    }

    @Test
    @DisplayName("字段: direction 为 exist=false，不落库（DB 无 direction 列/约束）")
    void direction_isNotPersisted() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);

        BankStatementEntity found = mapper.selectById(e.getId());
        // DB 无 direction 列，故从 DB 读回必为 null —— 这正是该字段的既有设计
        assertNull(found.getDirection(),
                "direction 是 @TableField(exist = false)，DB 无此列，读回应为 null");
    }

    @Test
    @DisplayName("字段: version 由 @Version 管理，取 DB 默认值 1")
    void version_takesDatabaseDefault() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);

        BankStatementEntity found = mapper.selectById(e.getId());
        assertNotNull(found.getVersion());
        assertEquals(1, found.getVersion().intValue(), "t_bank_statement.version 的 DB 默认值为 1");
    }

    @Test
    @DisplayName("更新: 可修改摘要且版本号递增")
    void update_shouldModifyAndBumpVersion() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);

        // 乐观锁用法：必须用「查库得到的实体」做更新。
        // MyBatis-Plus 不会把 version 的 DB 默认值回填到插入时的内存对象，
        // 该对象 version 仍为 null，OptimisticLockerInnerInterceptor 遇 null 会
        // 跳过版本条件与递增（既不会 bump，也不会校验并发）。
        BankStatementEntity loaded = mapper.selectById(e.getId());
        Integer before = loaded.getVersion();
        assertNotNull(before, "version 应由 DB 默认值 1 填充");
        assertEquals(1, before.intValue());

        loaded.setSummary("修改摘要");
        assertEquals(1, mapper.updateById(loaded));

        BankStatementEntity found = mapper.selectById(e.getId());
        assertEquals("修改摘要", found.getSummary());
        assertEquals(before + 1, found.getVersion().intValue(), "@Version 更新后应自动递增");
    }

    @Test
    @DisplayName("乐观锁: 用过期 version 更新应命中 0 行")
    void update_withStaleVersion_shouldAffectZeroRows() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);

        BankStatementEntity fresh = mapper.selectById(e.getId());
        fresh.setSummary("先更新一次");
        assertEquals(1, mapper.updateById(fresh), "首次更新应成功，version 1 -> 2");

        // 显式构造一个 version 已过期的实体。
        // 注意：不能用两次 selectById 拿「两个实体」—— 同一 SqlSession 内 MyBatis
        // 一级缓存会返回同一个对象实例，且更新成功后 version 会回写进该对象，
        // 于是「旧 version」其实是最新的，乐观锁自然拦不住。
        BankStatementEntity stale = new BankStatementEntity();
        stale.setId(e.getId());
        stale.setVersion(1);
        stale.setSummary("用过期版本再更新");
        assertEquals(0, mapper.updateById(stale), "version 已过期，乐观锁应使更新命中 0 行");

        BankStatementEntity found = mapper.selectById(e.getId());
        assertEquals("先更新一次", found.getSummary(), "过期版本的更新不应生效");
    }
}
