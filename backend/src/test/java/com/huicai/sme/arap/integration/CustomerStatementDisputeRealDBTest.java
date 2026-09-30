package com.huicai.sme.arap.integration;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.arap.entity.CustomerStatementEntity;
import com.huicai.sme.arap.entity.DisputeEntity;
import com.huicai.sme.arap.mapper.CustomerStatementMapper;
import com.huicai.sme.arap.mapper.DisputeMapper;
import com.huicai.sme.arap.service.CustomerStatementService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D1 缺陷修复真实 DB 测试（REQ-2026-134 / P107）。
 *
 * 缺陷：CustomerStatementServiceImpl.dispute() 设 status="DISPUTED"，而
 * chk_customer_statement_status 允许集为 (DRAFT, GENERATED, SENT, CONFIRMED)
 * 无 DISPUTED => 对账「发起差异」端点 100% 抛
 * "chk_customer_statement_status" 约束违约。
 *
 * 前端 CustomerStatementList.vue:152,160 已有 DISPUTED 的中文标签与颜色，
 * 说明业务语义早已确定，缺的是 DB 侧 CHECK 值 —— 故修法是补约束（V160）
 * 而非改代码值。
 *
 * 本类必须走真实 DB：CHECK 约束违约只有真库才能暴露，Mock 测不到
 * （AGENTS §4.3 第 7 条 Mock 盲区）。
 */
@SlowTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerStatementDisputeRealDBTest extends AbstractMapperTest {

    @Autowired
    private CustomerStatementService customerStatementService;

    @Autowired
    private CustomerStatementMapper statementMapper;

    @Autowired
    private DisputeMapper disputeMapper;

    @Autowired
    private com.huicai.base.masterdata.mapper.CustomerMapper customerMapper;

    /**
     * 自建 find-or-insert 客户夹具，不依赖基类 ensureCustomer 的实例级缓存。
     * 原因：customerIdCache 是 private final 非 static 字段，NOT_SUPPORTED 下
     * 夹具行不随事务回滚，跨用例易撞 uq_customer_code_enterprise
     * （同 AGENTS §4.2-12：夹具助手与清理必须成对）。
     */
    private Long ensureTestCustomer() {
        String code = "D1-CUSTOMER-1";
        com.huicai.base.masterdata.entity.CustomerEntity existing =
                customerMapper.selectOne(new LambdaQueryWrapper<com.huicai.base.masterdata.entity.CustomerEntity>()
                        .eq(com.huicai.base.masterdata.entity.CustomerEntity::getCode, code)
                        .eq(com.huicai.base.masterdata.entity.CustomerEntity::getEnterpriseId, 1L)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        com.huicai.base.masterdata.entity.CustomerEntity c =
                new com.huicai.base.masterdata.entity.CustomerEntity();
        c.setCode(code);
        c.setName("D1争议测试客户");
        c.setIsActive(true);
        c.setEnterpriseId(1L);
        customerMapper.insert(c);
        return c.getId();
    }

    private CustomerStatementEntity seedSentStatement(String uniqueSuffix) {
        // 夹具必填列按 information_schema 实测：statement_no(NOT NULL+UNIQUE)、
        // period_start/period_end(NOT NULL)、customer_id 受
        // fk_customer_statement_customer 外键约束 —— 缺任一都会
        // DataIntegrityViolation（AGENTS §4.2-13：写测试前先查真实 schema）
        CustomerStatementEntity e = new CustomerStatementEntity();
        e.setStatementNo("9999.D1.STMT." + uniqueSuffix);
        e.setCustomerId(ensureTestCustomer());
        e.setCustomerName("争议测试客户" + uniqueSuffix);
        e.setPeriod("202609");
        e.setStatementDate(LocalDate.of(2026, 9, 30));
        e.setPeriodStart(LocalDate.of(2026, 9, 1));
        e.setPeriodEnd(LocalDate.of(2026, 9, 30));
        e.setTotalOriginal(new BigDecimal("1000.00"));
        e.setTotalSettled(new BigDecimal("600.00"));
        e.setTotalUnsettled(new BigDecimal("400.00"));
        e.setStatus("SENT");
        e.setDeleted(0);
        assertEquals(1, statementMapper.insert(e), "对账单夹具应插入成功");
        return e;
    }

    /** AT-107-1：发起差异不得抛 CHECK 违约，且状态应落为 DISPUTED */
    @Test
    void dispute_应落库DISPUTED且不违反CHECK约束() {
        CustomerStatementEntity stmt = seedSentStatement("D1-A");

        customerStatementService.dispute(stmt.getId(),
                new CustomerStatementService.DisputeRequest(
                        "DOC-D1-A", "AMOUNT_MISMATCH",
                        new BigDecimal("1000.00"), new BigDecimal("900.00"),
                        "金额不符"));

        // 正向：状态已落库
        CustomerStatementEntity reloaded = statementMapper.selectById(stmt.getId());
        assertNotNull(reloaded, "对账单应仍存在");
        assertEquals("DISPUTED", reloaded.getStatus(), "发起差异后状态应为 DISPUTED");

        // 正向：差异记录已落库
        DisputeEntity dispute = disputeMapper.selectOne(new LambdaQueryWrapper<DisputeEntity>()
                .eq(DisputeEntity::getStatementId, stmt.getId())
                .last("LIMIT 1"));
        assertNotNull(dispute, "应创建差异记录");
        assertEquals(0, new BigDecimal("100.00").compareTo(dispute.getDiffAmount()),
                "diff_amount 应为 expected - actual = 100.00");
    }

    /** 负向断言：非 SENT/CONFIRMED 状态不得发起差异（状态机守恒） */
    @Test
    void dispute_非SENT或CONFIRMED状态应被拒绝且不落差异记录() {
        CustomerStatementEntity stmt = seedSentStatement("D1-B");
        stmt.setStatus("DRAFT");
        statementMapper.updateById(stmt);

        assertThrows(RuntimeException.class, () -> customerStatementService.dispute(stmt.getId(),
                new CustomerStatementService.DisputeRequest(
                        "DOC-D1-B", "AMOUNT_MISMATCH",
                        new BigDecimal("1000.00"), new BigDecimal("900.00"),
                        "不应成功")));

        // 负向：状态未被改动，差异记录未创建
        assertEquals("DRAFT", statementMapper.selectById(stmt.getId()).getStatus(),
                "被拒绝后状态不得被改成 DISPUTED");
        assertNull(disputeMapper.selectOne(new LambdaQueryWrapper<DisputeEntity>()
                .eq(DisputeEntity::getStatementId, stmt.getId())
                .last("LIMIT 1")), "被拒绝时不得留下差异记录");
    }

    /**
     * 清理：本类 NOT_SUPPORTED 不回滚，需自清理（按唯一编号前缀收敛，不误删他人数据）。
     * 注意：只清本类造的对账单与差异记录，**不删 ensureCustomer 建的客户** ——
     * AbstractMapperTest 的 customerIdCache 会在 JVM 内缓存该 id，
     * 删掉行会导致下一个用例重建时撞 uq_customer_code_enterprise
     * （与 AGENTS §4.2-12 同源：助手缓存与数据清理必须成对）。
     */
    @AfterEach
    void cleanup() {
        disputeMapper.delete(new LambdaQueryWrapper<DisputeEntity>()
                .likeRight(DisputeEntity::getDocNo, "DOC-D1-"));
        statementMapper.delete(new LambdaQueryWrapper<CustomerStatementEntity>()
                .likeRight(CustomerStatementEntity::getStatementNo, "9999.D1.STMT."));
    }
}
