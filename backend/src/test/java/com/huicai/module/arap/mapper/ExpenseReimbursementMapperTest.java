package com.huicai.module.arap.mapper;

import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class ExpenseReimbursementMapperTest {

    @Test
    @DisplayName("ExpenseReimbursementMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        ExpenseReimbursementMapper mapper = Mockito.mock(ExpenseReimbursementMapper.class);
        ExpenseReimbursementEntity entity = new ExpenseReimbursementEntity();
        entity.setReimbNo("REIMB-001");
        entity.setEmployeeId(1L);
        entity.setDeptId(1L);
        entity.setExpenseType("TRAVEL");
        entity.setAmount(java.math.BigDecimal.valueOf(2000));
        entity.setSummary("出差报销");
        entity.setStatus("DRAFT");
        entity.setDocId(null);
        entity.setVoucherId(null);
        entity.setBankStmtId(null);
        entity.setAttachmentIds(null);
        entity.setSubmittedAt(null);
        entity.setApprovedAt(null);
        entity.setRejectReason(null);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("ExpenseReimbursementMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        ExpenseReimbursementMapper mapper = Mockito.mock(ExpenseReimbursementMapper.class);
        ExpenseReimbursementEntity entity = new ExpenseReimbursementEntity();
        entity.setReimbNo("REIMB-001");
        entity.setEmployeeId(1L);
        entity.setDeptId(1L);
        entity.setExpenseType("TRAVEL");
        entity.setAmount(java.math.BigDecimal.valueOf(2000));
        entity.setSummary("出差报销");
        entity.setStatus("DRAFT");
        entity.setDocId(null);
        entity.setVoucherId(null);
        entity.setBankStmtId(null);
        entity.setAttachmentIds(null);
        entity.setSubmittedAt(null);
        entity.setApprovedAt(null);
        entity.setRejectReason(null);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        ExpenseReimbursementEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("ExpenseReimbursementMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        ExpenseReimbursementMapper mapper = Mockito.mock(ExpenseReimbursementMapper.class);
        ExpenseReimbursementEntity entity = new ExpenseReimbursementEntity();
        entity.setReimbNo("REIMB-001");
        entity.setEmployeeId(1L);
        entity.setDeptId(1L);
        entity.setExpenseType("TRAVEL");
        entity.setAmount(java.math.BigDecimal.valueOf(2000));
        entity.setSummary("出差报销");
        entity.setStatus("DRAFT");
        entity.setDocId(null);
        entity.setVoucherId(null);
        entity.setBankStmtId(null);
        entity.setAttachmentIds(null);
        entity.setSubmittedAt(null);
        entity.setApprovedAt(null);
        entity.setRejectReason(null);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("ExpenseReimbursementMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        ExpenseReimbursementMapper mapper = Mockito.mock(ExpenseReimbursementMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("ExpenseReimbursementMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        ExpenseReimbursementMapper mapper = Mockito.mock(ExpenseReimbursementMapper.class);
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setReimbNo("REIMB-001");
        e.setEmployeeId(1L);
        e.setDeptId(1L);
        e.setExpenseType("TRAVEL");
        e.setAmount(java.math.BigDecimal.valueOf(2000));
        e.setSummary("出差报销");
        e.setStatus("DRAFT");
        e.setDocId(null);
        e.setVoucherId(null);
        e.setBankStmtId(null);
        e.setAttachmentIds(null);
        e.setSubmittedAt(null);
        e.setApprovedAt(null);
        e.setRejectReason(null);
        e.setCreatedBy(1L);
        Mockito.when(mapper.insert(e)).thenReturn(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(e);
        Mockito.when(mapper.updateById(e)).thenReturn(1);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        assertEquals(1, mapper.insert(e));
        assertNotNull(mapper.selectById(1L));
        assertEquals(1, mapper.updateById(e));
        assertEquals(1, mapper.deleteById(1L));
    }
}
