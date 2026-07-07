package com.huicai.module.xxx.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.xxx.entity.XxxEntity;
import com.huicai.module.xxx.mapper.XxxMapper;
import com.huicai.module.xxx.service.XxxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
public class XxxFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private XxxService xxxService;

    @Autowired
    private XxxMapper xxxMapper;

    @BeforeEach
    void setupBaseData() {
    }

    @Test
    void fullFlow_shouldCompleteSuccessfully() {
        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-001");
        entity.setName("E2E流程测试-完整流程");
        entity.setAmount(new BigDecimal("5000.00"));
        entity.setTaxAmount(new BigDecimal("650.00"));
        entity.setTotalAmount(new BigDecimal("5650.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        assertNotNull(entity.getId());
        assertEquals("PENDING_CONFIRM", entity.getStatus());

        Long xxxId = entity.getId();

        boolean confirmed = xxxService.confirm(xxxId, 2L);
        assertTrue(confirmed);

        XxxEntity confirmedEntity = xxxMapper.selectById(xxxId);
        assertEquals("CONFIRMED", confirmedEntity.getStatus());
        assertEquals(2L, confirmedEntity.getAuditedBy());
        assertNotNull(confirmedEntity.getAuditedAt());
    }

    @Test
    void confirmTwice_shouldFail() {
        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-002");
        entity.setName("E2E流程测试-重复审核");
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        xxxService.confirm(entity.getId(), 2L);

        boolean secondConfirm = xxxService.confirm(entity.getId(), 2L);

        assertFalse(secondConfirm);
    }

    @Test
    void cancelConfirmed_shouldRollbackStatus() {
        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-003");
        entity.setName("E2E流程测试-取消审核");
        entity.setAmount(new BigDecimal("1000.00"));
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        xxxService.confirm(entity.getId(), 2L);

        boolean cancelled = xxxService.cancel(entity.getId(), 3L);
        assertTrue(cancelled);

        XxxEntity cancelledEntity = xxxMapper.selectById(entity.getId());
        assertEquals("CANCELLED", cancelledEntity.getStatus());
    }

    @Test
    void amountPrecision_shouldNotLosePrecision() {
        BigDecimal preciseAmount = new BigDecimal("1234567.89");

        XxxEntity entity = new XxxEntity();
        entity.setCode("FLOW-TEST-004");
        entity.setName("E2E流程测试-精度验证");
        entity.setAmount(preciseAmount);
        entity.setStatus("PENDING_CONFIRM");
        entity.setBusinessDate(LocalDate.now());
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        xxxMapper.insert(entity);

        XxxEntity found = xxxMapper.selectById(entity.getId());
        assertEquals(0, preciseAmount.compareTo(found.getAmount()));
    }
}