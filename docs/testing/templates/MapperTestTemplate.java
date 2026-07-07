package com.huicai.module.xxx.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.xxx.entity.XxxEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class XxxMapperTest extends AbstractMapperTest {

    @Autowired
    private XxxMapper xxxMapper;

    @Test
    void insert_shouldReturnId() {
        XxxEntity entity = new XxxEntity();
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

    @Test
    void selectById_shouldReturnCorrectData() {
        XxxEntity entity = new XxxEntity();
        entity.setCode("TEST-002");
        entity.setName("测试数据2");
        entity.setAmount(new BigDecimal("2000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        XxxEntity found = xxxMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals("TEST-002", found.getCode());
        assertEquals("测试数据2", found.getName());
        assertEquals(0, new BigDecimal("2000.00").compareTo(found.getAmount()));
    }

    @Test
    void updateById_shouldUpdateCorrectly() {
        XxxEntity entity = new XxxEntity();
        entity.setCode("TEST-003");
        entity.setName("测试数据3");
        entity.setAmount(new BigDecimal("3000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        entity.setStatus("CONFIRMED");
        entity.setName("测试数据3-已更新");
        int rows = xxxMapper.updateById(entity);

        assertEquals(1, rows);
        XxxEntity updated = xxxMapper.selectById(entity.getId());
        assertEquals("CONFIRMED", updated.getStatus());
        assertEquals("测试数据3-已更新", updated.getName());
    }

    @Test
    void deleteById_shouldDeleteCorrectly() {
        XxxEntity entity = new XxxEntity();
        entity.setCode("TEST-004");
        entity.setName("测试数据4");
        entity.setAmount(new BigDecimal("4000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        int rows = xxxMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        assertNull(xxxMapper.selectById(entity.getId()));
    }

    @Test
    void customQuery_shouldReturnCorrectResult() {
    }
}