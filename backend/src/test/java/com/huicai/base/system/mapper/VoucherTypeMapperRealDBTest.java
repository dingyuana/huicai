package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherType Mapper 真实 DB 测试.
 * VoucherTypeEntity 继承 BaseEntity，enterpriseId 由 MyBatis-Plus 自动填充.
 *
 * <h3>历史说明（REQ-2026-121）</h3>
 * {@code t_voucher_type.code} 是 {@code varchar(20)}，原测试用
 * {@code "TYPE_TEST_" + System.currentTimeMillis()}（10+13=23 字符）必然超长。
 * 改用 base36 短码，控制在 20 字符内。
 */
class VoucherTypeMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherTypeMapper voucherTypeMapper;

    @Test
    void insert_shouldReturnId() {
        VoucherTypeEntity entity = new VoucherTypeEntity();
        entity.setCode("TT" + Long.toString(System.nanoTime() & 0xFFFFFFL, 36));
        entity.setName("测试凭证类型");
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setEnterpriseId(1L);
        entity.setDeleted(0);

        assertEquals(1, voucherTypeMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}