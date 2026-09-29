package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.DeptEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dept Mapper 真实 DB 测试.
 *
 * <h3>历史说明（REQ-2026-121）</h3>
 * {@code t_dept.dept_code} 是 NOT NULL 且无默认值，而 {@code DeptEntity} 原先
 * <b>没有 deptCode 字段</b>，导致任何经 MyBatis-Plus 插入部门的路径都必然失败
 * （含生产 {@code DeptServiceImpl.create}）。已在 REQ-2026-121 补齐 Entity 字段。
 */
class DeptMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private DeptMapper deptMapper;

    @Test
    void insert_shouldReturnId() {
        DeptEntity entity = new DeptEntity();
        entity.setName("测试部门");
        entity.setDeptCode("DEPT_TEST_" + System.nanoTime());
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        assertEquals(1, deptMapper.insert(entity));
        assertNotNull(entity.getId());
    }

    @Test
    void insert_shouldFailWithoutName() {
        DeptEntity entity = new DeptEntity();
        entity.setDeptCode("DEPT_TEST_" + System.nanoTime());
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        assertThrows(Exception.class, () -> deptMapper.insert(entity),
                "dept_name 为 NOT NULL");
    }
}