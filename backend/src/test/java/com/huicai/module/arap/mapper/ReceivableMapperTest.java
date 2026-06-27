package com.huicai.module.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.ReceivableEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReceivableMapper 真实 DB 测试
 * 验证应收 Mapper 的 CRUD 操作与 SQL 正确性
 */
public class ReceivableMapperTest extends AbstractMapperTest {

    @Autowired
    private ReceivableMapper receivableMapper;

    @Test
    void insert_shouldReturnId() {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCustomerId(1L);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.now());
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("1000.00"));
        entity.setDueDate(LocalDate.now().plusDays(30));
        entity.setStatus("UNSETTLED");
        entity.setDeleted(0);
        entity.setVersion(0);

        int rows = receivableMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnCorrectData() {
        // 先插入
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCustomerId(2L);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.now());
        entity.setAmount(new BigDecimal("2000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("2000.00"));
        entity.setDueDate(LocalDate.now().plusDays(30));
        entity.setStatus("UNSETTLED");
        entity.setDeleted(0);
        entity.setVersion(0);
        receivableMapper.insert(entity);

        // 再查询
        ReceivableEntity found = receivableMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals(2L, found.getCustomerId());
        assertEquals("202606", found.getPeriod());
        assertEquals("UNSETTLED", found.getStatus());
        assertEquals(0, new BigDecimal("2000.00").compareTo(found.getAmount()));
    }

    @Test
    void updateById_shouldUpdateCorrectly() {
        // 先插入
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCustomerId(3L);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.now());
        entity.setAmount(new BigDecimal("3000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("3000.00"));
        entity.setDueDate(LocalDate.now().plusDays(30));
        entity.setStatus("UNSETTLED");
        entity.setDeleted(0);
        entity.setVersion(0);
        receivableMapper.insert(entity);

        // 更新状态和已结算金额
        entity.setStatus("SETTLED");
        entity.setSettledAmount(new BigDecimal("3000.00"));
        entity.setUnsettledAmount(BigDecimal.ZERO);
        int rows = receivableMapper.updateById(entity);

        assertEquals(1, rows);
        ReceivableEntity updated = receivableMapper.selectById(entity.getId());
        assertEquals("SETTLED", updated.getStatus());
        assertEquals(0, new BigDecimal("3000.00").compareTo(updated.getSettledAmount()));
    }

    @Test
    void deleteById_shouldDeleteCorrectly() {
        // 先插入
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCustomerId(4L);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.now());
        entity.setAmount(new BigDecimal("4000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("4000.00"));
        entity.setDueDate(LocalDate.now().plusDays(30));
        entity.setStatus("UNSETTLED");
        entity.setDeleted(0);
        entity.setVersion(0);
        receivableMapper.insert(entity);

        // 删除
        int rows = receivableMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        assertNull(receivableMapper.selectById(entity.getId()));
    }
}
