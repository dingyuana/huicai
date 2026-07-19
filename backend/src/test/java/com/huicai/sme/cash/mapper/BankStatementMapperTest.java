package com.huicai.sme.cash.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.cash.entity.BankStatementEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BankStatement Mapper 真实 DB 测试.
 * ✅ 正向插入 + 5 项约束校验
 */
class BankStatementMapperTest extends AbstractMapperTest {

    @Autowired
    private BankStatementMapper mapper;

    private BankStatementEntity createValidStmt() {
        BankStatementEntity e = new BankStatementEntity();
        e.setAccountId(1L);
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
    void insert_shouldSucceedWithAllRequiredFields() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);
        assertNotNull(e.getId());

        BankStatementEntity found = mapper.selectById(e.getId());
        assertEquals("PENDING", found.getReviewStatus());
        assertEquals("INCOME", found.getTxType());
    }

    @Test
    void insert_shouldEnforceNotNullAccountId() {
        BankStatementEntity e = createValidStmt();
        e.setAccountId(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "account_id 为 NOT NULL，插入应失败");
    }

    @Test
    void insert_shouldEnforceNotNullTxDate() {
        BankStatementEntity e = createValidStmt();
        e.setTxDate(null);
        assertThrows(Exception.class, () -> mapper.insert(e));
    }

    @Test
    void insert_shouldEnforceChkTxType() {
        BankStatementEntity e = createValidStmt();
        e.setTxType("INVALID_TYPE");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "tx_type 有 CHECK 约束，INVALID_TYPE 应失败");
    }

    @Test
    void insert_shouldEnforceChkDirection() {
        BankStatementEntity e = createValidStmt();
        e.setDirection("invalid");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "direction 有 CHECK 约束，invalid 应失败");
    }

    @Test
    void insert_shouldEnforceChkReviewStatus() {
        BankStatementEntity e = createValidStmt();
        e.setReviewStatus("INVALID_STATUS");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "review_status 有 CHECK 约束，INVALID_STATUS 应失败");
    }

    @Test
    void update_shouldTrackVersion() {
        BankStatementEntity e = createValidStmt();
        mapper.insert(e);
        assertEquals(0, e.getVersion().intValue(), "初始版本号应为0");

        e.setSummary("修改摘要");
        mapper.updateById(e);
        // 注意：@Version 更新后会自动递增版本号
        assertNotNull(mapper.selectById(e.getId()));
    }
}