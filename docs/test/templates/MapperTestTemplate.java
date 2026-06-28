package com.huicai.module.xxx.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.xxx.entity.XxxEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XxxMapper 真实 DB 测试模板
 * 
 * 说明：
 * 1. 继承 AbstractMapperTest 自动获得 Testcontainers + Flyway + 事务回滚
 * 2. 每个测试方法独立事务，执行后自动回滚，互不影响
 * 3. 可以发现 Mock 测试永远发现不了的问题：
 *    - 数据库约束（非空、外键、check constraint）
 *     - varchar(6) 长度限制（如 period 字段）
 *     - DECIMAL 精度丢失
 *     - MyBatis SQL 语法错误
 *     - Flyway migration 脚本错误
 *     - 外键约束
 *     - 唯一键约束
 * 
 * 使用方法：
 * 1. 替换 Xxx 为实际实体名
 * 2. 替换字段为实体实际字段
 * 3. 根据业务需求补充复杂 SQL 测试
 * 4. 如果表有外键依赖，在 @BeforeEach 中准备前置数据
 * 
 * ⚠️ 重要注意事项：
 * 1. 编码冲突：V60 migration 已预置 1001/1002/1122/2202 等常用科目编码
 *    测试数据请使用 9999 开头的编码（如 9999.0001）避免冲突
 * 2. 字段大小写：direction 字段数据库约束为小写 "debit" / "credit"
 *    不是大写 "DEBIT" / "CREDIT"，Mock 测试无法发现此类问题
 * 3. period 字段长度：period 是 varchar(6)，格式为 "202606"，不能用 "2026-06"
 * 4. 外键依赖：有外键的表必须在 @BeforeEach 中先插入关联数据
 *    参考顺序：t_subject → t_customer/t_vendor → 业务表
 */
public class XxxMapperTest extends AbstractMapperTest {

    @Autowired
    private XxxMapper xxxMapper;

    /**
     * 场景 1：插入测试
     * 验证：所有必填字段可正确插入，主键自动生成
     */
    @Test
    void insert_shouldReturnId() {
        XxxEntity entity = new XxxEntity();
        // TODO: 根据实际实体设置字段
        entity.setCode("TEST-001");
        entity.setName("测试数据");
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);

        int rows = xxxMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    /**
     * 场景 2：根据 ID 查询测试
     * 验证：插入的数据可正确查询，字段值一致
     */
    @Test
    void selectById_shouldReturnCorrectData() {
        // 先插入
        XxxEntity entity = new XxxEntity();
        entity.setCode("TEST-002");
        entity.setName("测试数据2");
        entity.setAmount(new BigDecimal("2000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        // 再查询
        XxxEntity found = xxxMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals("TEST-002", found.getCode());
        assertEquals("测试数据2", found.getName());
        assertEquals(0, new BigDecimal("2000.00").compareTo(found.getAmount()));
    }

    /**
     * 场景 3：更新测试
     * 验证：状态和字段可正确更新
     */
    @Test
    void updateById_shouldUpdateCorrectly() {
        // 先插入
        XxxEntity entity = new XxxEntity();
        entity.setCode("TEST-003");
        entity.setName("测试数据3");
        entity.setAmount(new BigDecimal("3000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        // 更新
        entity.setStatus("CONFIRMED");
        entity.setName("测试数据3-已更新");
        int rows = xxxMapper.updateById(entity);

        assertEquals(1, rows);
        XxxEntity updated = xxxMapper.selectById(entity.getId());
        assertEquals("CONFIRMED", updated.getStatus());
        assertEquals("测试数据3-已更新", updated.getName());
    }

    /**
     * 场景 4：删除测试
     * 验证：记录可正确删除
     */
    @Test
    void deleteById_shouldDeleteCorrectly() {
        // 先插入
        XxxEntity entity = new XxxEntity();
        entity.setCode("TEST-004");
        entity.setName("测试数据4");
        entity.setAmount(new BigDecimal("4000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        // 删除
        int rows = xxxMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        assertNull(xxxMapper.selectById(entity.getId()));
    }

    /**
     * 场景 5：复杂自定义 SQL 测试（根据实际业务补充）
     * 验证：连表查询、分组聚合、条件查询等复杂 SQL 正确性
     */
    @Test
    void customQuery_shouldReturnCorrectResult() {
        // TODO: 根据实际 Mapper 中的自定义方法补充测试
        // 示例：
        // List<XxxVO> list = xxxMapper.selectByCondition("CONFIRMED", LocalDate.now());
        // assertNotNull(list);
    }
}
