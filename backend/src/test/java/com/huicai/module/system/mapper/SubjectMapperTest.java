package com.huicai.base.system.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.system.entity.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubjectMapper 真实 DB 测试
 * 验证科目 Mapper 的 CRUD 操作与 SQL 正确性
 */
public class SubjectMapperTest extends AbstractMapperTest {

    @Autowired
    private SubjectMapper subjectMapper;

    @Test
    void insert_shouldReturnId() {
        Subject entity = new Subject();
        entity.setCode("9999.0001");
        entity.setName("测试科目");
        entity.setDirection("debit");
        entity.setLevel(1);
        entity.setIsActive(true);
        entity.setIsLeaf(true);
        entity.setDeleted(0);

        int rows = subjectMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnCorrectData() {
        // 先插入
        Subject entity = new Subject();
        entity.setCode("9999.0002");
        entity.setName("测试科目2");
        entity.setDirection("debit");
        entity.setLevel(1);
        entity.setIsActive(true);
        entity.setIsLeaf(true);
        entity.setDeleted(0);
        subjectMapper.insert(entity);

        // 再查询
        Subject found = subjectMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals("9999.0002", found.getCode());
        assertEquals("测试科目2", found.getName());
        assertEquals("debit", found.getDirection());
    }

    @Test
    void updateById_shouldUpdateCorrectly() {
        // 先插入
        Subject entity = new Subject();
        entity.setCode("9999.0003");
        entity.setName("测试科目3");
        entity.setDirection("debit");
        entity.setLevel(1);
        entity.setIsActive(true);
        entity.setIsLeaf(true);
        entity.setDeleted(0);
        subjectMapper.insert(entity);

        // 更新
        entity.setName("测试科目3-更新");
        entity.setIsActive(false);
        int rows = subjectMapper.updateById(entity);

        assertEquals(1, rows);
        Subject updated = subjectMapper.selectById(entity.getId());
        assertEquals("测试科目3-更新", updated.getName());
        assertFalse(updated.getIsActive());
    }

    @Test
    void deleteById_shouldDeleteCorrectly() {
        // 先插入
        Subject entity = new Subject();
        entity.setCode("9999.0004");
        entity.setName("测试科目4");
        entity.setDirection("debit");
        entity.setLevel(1);
        entity.setIsActive(true);
        entity.setIsLeaf(true);
        entity.setDeleted(0);
        subjectMapper.insert(entity);

        // 删除
        int rows = subjectMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        assertNull(subjectMapper.selectById(entity.getId()));
    }
}
