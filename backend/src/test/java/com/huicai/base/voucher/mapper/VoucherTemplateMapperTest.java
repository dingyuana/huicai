package com.huicai.base.voucher.mapper;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherTemplateMapper 真实 DB 测试（替代原 Mockito 同义反复自测）
 *
 * <p>覆盖 insert / selectById / updateById / deleteById 四个核心操作的真实落库往返。
 * 基于 {@link AbstractMapperTest}，事务自动回滚，不污染数据库。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
@DisplayName("VoucherTemplateMapper 真实 DB 测试")
public class VoucherTemplateMapperTest extends AbstractMapperTest {

    @Autowired
    private VoucherTemplateMapper mapper;

    private VoucherTemplateEntity buildEntity() {
        VoucherTemplateEntity e = new VoucherTemplateEntity();
        e.setName("测试模板");
        e.setBusinessType("PAYMENT");
        e.setTemplateCode("TPL_TEST_001");
        e.setVoucherTypeCode("FK");
        e.setSummary("测试模板摘要");
        e.setEntries("[]");
        e.setIsActive(true);
        e.setRemark("测试模板描述");
        return e;
    }

    @Test
    @DisplayName("insert 真实落库并返回主键")
    void insert_shouldPersistAndReturnId() {
        VoucherTemplateEntity entity = buildEntity();
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId(), "insert 后应回填主键 id");
    }

    @Test
    @DisplayName("selectById 能查出 insert 的数据")
    void selectById_shouldReturnInsertedEntity() {
        VoucherTemplateEntity entity = buildEntity();
        mapper.insert(entity);

        VoucherTemplateEntity result = mapper.selectById(entity.getId());

        assertNotNull(result);
        assertEquals(entity.getName(), result.getName());
        assertEquals(entity.getTemplateCode(), result.getTemplateCode());
        assertEquals(entity.getBusinessType(), result.getBusinessType());
    }

    @Test
    @DisplayName("updateById 真实更新并影响行")
    void updateById_shouldUpdatePersistedRow() {
        VoucherTemplateEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setName("更新后的模板名");
        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        VoucherTemplateEntity updated = mapper.selectById(entity.getId());
        assertEquals("更新后的模板名", updated.getName());
    }

    @Test
    @DisplayName("deleteById 真实删除（逻辑删除）")
    void deleteById_shouldMarkDeleted() {
        VoucherTemplateEntity entity = buildEntity();
        mapper.insert(entity);

        int rows = mapper.deleteById(entity.getId());

        assertEquals(1, rows);
        // MyBatis-Plus 逻辑删除后 selectById 应返回 null
        assertNull(mapper.selectById(entity.getId()));
    }
}
