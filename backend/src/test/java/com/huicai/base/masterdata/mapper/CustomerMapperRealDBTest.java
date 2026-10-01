package com.huicai.base.masterdata.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CustomerMapper **真实 DB** 测试（REQ-2026-131 / P104 M5：Mock 同义反复归零）
 *
 * <p><b>取代对象</b>：原 {@code CustomerMapperTest} 用 {@code Mockito.mock(CustomerMapper.class)}
 * mock <b>被测对象本身</b>，再 stub 成期望值后断言拿到该值 ——
 * 五个用例（145 个 @Test 中的一部分）全部如此，等于「断言自己写的桩」，
 * 零信号。其中 {@code deleteById_shouldAcceptId} 尤其危险：它「验证」了删除，
 * 而 mock 根本不会执行软删除，真实行为（铁律 #12 逻辑删除）从未被覆盖。
 *
 * <p>本类能断言 mock 版做不到的事：
 * <ul>
 *   <li>插入后 id 由数据库生成、{@code created_at} 有值；</li>
 *   <li>{@code uq_customer_code_enterprise} 唯一约束**真实生效**（重复 code 必失败）；</li>
 *   <li>{@code deleteById} 是<b>软删除</b> —— 行仍在，但 {@code deleted=1} 且默认查询查不到；</li>
 *   <li>租户过滤真实生效 —— 企业 B 的上下文看不到企业 A 的客户。</li>
 * </ul>
 */
@DisplayName("P104 CustomerMapper 真实 DB")
class CustomerMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private CustomerMapper customerMapper;

    private static final String CODE_PREFIX = "P104.CUST.";

    private CustomerEntity newCustomer(String suffix) {
        CustomerEntity c = new CustomerEntity();
        c.setCode(CODE_PREFIX + suffix);
        c.setName("P104测试客户-" + suffix);
        c.setContactPerson("张三");
        c.setPhone("13800000001");
        c.setCreditLimit(new BigDecimal("100000.00"));
        c.setCreditDays(30);
        c.setIsActive(true);
        return c;
    }

    @Test
    @DisplayName("insert 真实落库，id 由数据库生成")
    void insertPersistsAndGeneratesId() {
        CustomerEntity c = newCustomer("A");
        assertEquals(1, customerMapper.insert(c), "insert 应影响 1 行");

        assertNotNull(c.getId(), "id 未由数据库回填");
        CustomerEntity loaded = customerMapper.selectById(c.getId());
        assertNotNull(loaded, "插入后 selectById 查不到");
        assertEquals(c.getName(), loaded.getName());
        assertNotNull(loaded.getCreatedAt(), "created_at 未落库");
        assertEquals(0, loaded.getDeleted(), "初始 deleted 应为 0");
    }

    @Test
    @DisplayName("唯一约束 uq_customer_code_enterprise 真实生效（Mock 版完全测不到）")
    void uniqueConstraintOnCodeAndEnterpriseIsEnforced() {
        customerMapper.insert(newCustomer("DUP"));

        CustomerEntity dup = newCustomer("DUP");
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> customerMapper.insert(dup),
                "(code, enterprise_id) 唯一约束未生效 ⇒ 可能写入重复客商");
    }

    @Test
    @DisplayName("updateById 真实改变数据库中的字段")
    void updateByIdActuallyChangesRow() {
        CustomerEntity c = newCustomer("UPD");
        customerMapper.insert(c);

        c.setName("改过的名称");
        c.setCreditLimit(new BigDecimal("88888.00"));
        assertEquals(1, customerMapper.updateById(c));

        CustomerEntity loaded = customerMapper.selectById(c.getId());
        assertEquals("改过的名称", loaded.getName(), "updateById 未真实改库");
        assertEquals(0, new BigDecimal("88888.00").compareTo(loaded.getCreditLimit()),
                "creditLimit 未真实改库");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除：行仍在但 deleted=1，默认查询查不到")
    void deleteByIdIsSoftDelete() {
        CustomerEntity c = newCustomer("DEL");
        customerMapper.insert(c);
        Long id = c.getId();

        assertEquals(1, customerMapper.deleteById(id));

        // 逻辑删除（铁律 #12）：行未被物理删除
        Integer physicalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_customer WHERE id = ?", Integer.class, id);
        assertEquals(1, physicalRows, "行被物理删除了 —— 违反逻辑删除铁律 #12");

        // 但 MP 默认查询会过滤掉
        assertNull(customerMapper.selectById(id), "软删除后 selectById 仍能查到");
        Integer visibleByCode = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_customer WHERE code = ? AND deleted = 0",
                Integer.class, c.getCode());
        assertEquals(0, visibleByCode);
    }

    @Test
    @DisplayName("查询真实过滤 deleted=1 的行")
    void selectListExcludesSoftDeleted() {
        CustomerEntity c = newCustomer("LIST");
        customerMapper.insert(c);
        customerMapper.deleteById(c.getId());

        Long visible = customerMapper.selectCount(
                Wrappers.<CustomerEntity>lambdaQuery().eq(CustomerEntity::getCode, c.getCode()));
        assertEquals(0L, visible, "selectCount 未过滤软删除行");
    }

    @Test
    @DisplayName("租户隔离真实生效：企业 B 上下文看不到企业 A 的客户")
    void tenantIsolationIsReal() {
        CustomerEntity c = newCustomer("TENANT");
        customerMapper.insert(c);
        Long id = c.getId();

        useEnterprise(987654L);
        assertNull(customerMapper.selectById(id),
                "切换到其他企业后仍能查到该客户 ⇒ 租户隔离未生效");

        useEnterprise(DEFAULT_ENTERPRISE_ID);
        assertNotNull(customerMapper.selectById(id), "本企业应能查到");
    }

    @Test
    @DisplayName("反证：CRUD 真的走了数据库（同一 id 二次查询仍存在，说明不是内存态）")
    void crudActuallyHitsDatabase() {
        CustomerEntity c = newCustomer("REAL");
        customerMapper.insert(c);

        Integer rawCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_customer WHERE id = ?", Integer.class, c.getId());
        assertEquals(1, rawCount, "数据库里没有该行 ⇒ insert 未真实落库");
        assertTrue(rawCount != null);
    }
}