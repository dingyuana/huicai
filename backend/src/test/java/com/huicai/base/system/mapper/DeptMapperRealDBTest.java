package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.DeptEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dept Mapper 真实 DB 测试.
 */
class DeptMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private DeptMapper deptMapper;

    @Test
    void insert_shouldReturnId() {
        DeptEntity entity = new DeptEntity();
        entity.setName("测试部门");
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
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        assertThrows(Exception.class, () -> deptMapper.insert(entity),
                "dept_name 为 NOT NULL");
    }
}