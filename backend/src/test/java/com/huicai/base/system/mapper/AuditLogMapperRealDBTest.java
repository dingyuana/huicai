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
}