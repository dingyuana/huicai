package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherType Mapper 真实 DB 测试.
 * VoucherTypeEntity 继承 BaseEntity，enterpriseId 由 MyBatis-Plus 自动填充。
 */
class VoucherTypeMapperTest extends AbstractMapperTest {

    @Autowired
    private VoucherTypeMapper voucherTypeMapper;

    @Test
    void insert_shouldReturnId() {
        VoucherTypeEntity entity = new VoucherTypeEntity();
        entity.setCode("TYPE_TEST_" + System.currentTimeMillis());
        entity.setName("测试凭证类型");
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setEnterpriseId(1L);
        entity.setDeleted(0);

        int rows = voucherTypeMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }
}