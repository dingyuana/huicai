package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditLog Mapper 真实 DB 测试.
 */
class AuditLogMapperTest extends AbstractMapperTest {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Test
    void insert_shouldReturnId() {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUserId(1L);
        entity.setUsername("test_operator");
        entity.setOperation("CREATE");
        entity.setModule("system");
        entity.setIpAddress("127.0.0.1");

        int rows = auditLogMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnLog() {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUserId(2L);
        entity.setUsername("test_operator_2");
        entity.setOperation("UPDATE");
        entity.setModule("voucher");
        entity.setIpAddress("192.168.1.1");
        auditLogMapper.insert(entity);

        AuditLogEntity found = auditLogMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("test_operator_2", found.getUsername());
        assertEquals("UPDATE", found.getOperation());
    }
}