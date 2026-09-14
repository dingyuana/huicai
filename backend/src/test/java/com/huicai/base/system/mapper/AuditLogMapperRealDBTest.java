package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditLog Mapper 真实 DB 测试.
 */
class AuditLogMapperRealDBTest extends AbstractMapperTest {

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

        assertEquals(1, auditLogMapper.insert(entity));
        assertNotNull(entity.getId());
    }

    @Test
    void insert_operatorFields_shouldPersistToDb() {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUserId(42L);
        entity.setUsername("audit_tester");
        entity.setOperation("TEST_PERSIST");
        entity.setModule("UNIT_TEST");
        entity.setIpAddress("127.0.0.1");

        auditLogMapper.insert(entity);
        assertNotNull(entity.getId());

        AuditLogEntity fetched = auditLogMapper.selectById(entity.getId());
        assertNotNull(fetched);
        assertEquals(42L, fetched.getUserId());
        assertEquals("audit_tester", fetched.getUsername());
        assertEquals("TEST_PERSIST", fetched.getOperation());
    }
}