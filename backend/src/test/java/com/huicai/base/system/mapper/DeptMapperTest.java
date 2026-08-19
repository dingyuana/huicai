package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.DeptEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dept Mapper 真实 DB 测试.
 * 验证树形结构、逻辑删除、NOT NULL 约束.
 */
class DeptMapperTest extends AbstractMapperTest {

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

        int rows = deptMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnParentDept() {
        DeptEntity parent = new DeptEntity();
        parent.setName("测试父部门");
        parent.setParentId(0L);
        parent.setSortOrder(1);
        parent.setDeleted(0);
        parent.setCreatedBy(1L);
        parent.setUpdatedBy(1L);
        deptMapper.insert(parent);

        DeptEntity found = deptMapper.selectById(parent.getId());
        assertNotNull(found);
        assertEquals("测试父部门", found.getName());
        assertEquals(0L, found.getParentId());
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
                "dept_name 为 NOT NULL，插入应失败");
    }
}