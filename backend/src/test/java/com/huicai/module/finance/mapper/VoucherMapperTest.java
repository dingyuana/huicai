package com.huicai.base.voucher.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherMapper 真实 DB 测试
 * 验证凭证 Mapper 的 CRUD 操作与 SQL 正确性
 */
public class VoucherMapperTest extends AbstractMapperTest {

    @Autowired
    private VoucherMapper voucherMapper;

    @Test
    void insert_shouldReturnId() {
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("TEST-001");
        entity.setPeriod("202606");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setTotalDebit(BigDecimal.ZERO);
        entity.setTotalCredit(BigDecimal.ZERO);
        entity.setCreatedBy(1L);

        int rows = voucherMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnCorrectData() {
        // 先插入
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("TEST-002");
        entity.setPeriod("202606");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setTotalDebit(BigDecimal.ZERO);
        entity.setTotalCredit(BigDecimal.ZERO);
        entity.setCreatedBy(1L);
        voucherMapper.insert(entity);

        // 再查询
        VoucherEntity found = voucherMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals("TEST-002", found.getVoucherNo());
        assertEquals("DRAFT", found.getStatus());
        assertEquals("202606", found.getPeriod());
    }

    @Test
    void updateById_shouldUpdateCorrectly() {
        // 先插入
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("TEST-003");
        entity.setPeriod("202606");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setTotalDebit(BigDecimal.ZERO);
        entity.setTotalCredit(BigDecimal.ZERO);
        entity.setCreatedBy(1L);
        voucherMapper.insert(entity);

        // 更新状态
        entity.setStatus("AUDITED");
        int rows = voucherMapper.updateById(entity);

        assertEquals(1, rows);
        VoucherEntity updated = voucherMapper.selectById(entity.getId());
        assertEquals("AUDITED", updated.getStatus());
    }

    @Test
    void deleteById_shouldDeleteCorrectly() {
        // 先插入
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("TEST-004");
        entity.setPeriod("202606");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setTotalDebit(BigDecimal.ZERO);
        entity.setTotalCredit(BigDecimal.ZERO);
        entity.setCreatedBy(1L);
        voucherMapper.insert(entity);

        // 删除
        int rows = voucherMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        assertNull(voucherMapper.selectById(entity.getId()));
    }
}
