package com.huicai.sme.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessDoc Mapper 真实 DB 测试.
 * ✅ 正向插入 + 5 项约束校验
 */
class BusinessDocMapperTest extends AbstractMapperTest {

    @Autowired
    private BusinessDocMapper mapper;

    private BusinessDocEntity createValidDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setDocNo("TEST-" + System.currentTimeMillis());
        e.setDocType("RECEIPT");
        e.setDocDate(LocalDate.now());
        e.setPeriod("202607");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus("DRAFT");
        e.setSource("MANUAL");
        e.setSummary("测试业务单据");
        return e;
    }

    @Test
    void insert_shouldSucceedWithAllRequiredFields() {
        BusinessDocEntity e = createValidDoc();
        mapper.insert(e);
        assertNotNull(e.getId());

        BusinessDocEntity found = mapper.selectById(e.getId());
        assertEquals("DRAFT", found.getStatus());
        assertEquals(0, found.getAmount().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void insert_shouldEnforceNotNullDocNo() {
        BusinessDocEntity e = createValidDoc();
        e.setDocNo(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "doc_no 为 NOT NULL，插入应失败");
    }

    @Test
    void insert_shouldEnforceNotNullDocType() {
        BusinessDocEntity e = createValidDoc();
        e.setDocType(null);
        assertThrows(Exception.class, () -> mapper.insert(e));
    }

    @Test
    void insert_shouldEnforceChkDocType() {
        BusinessDocEntity e = createValidDoc();
        e.setDocType("INVALID_TYPE");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "doc_type 有 CHECK 约束，INVALID_TYPE 应失败");
    }

    @Test
    void insert_shouldEnforceChkDocStatus() {
        BusinessDocEntity e = createValidDoc();
        e.setStatus("INVALID_STATUS");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "status 有 CHECK 约束，INVALID_STATUS 应失败");
    }

    @Test
    void insert_shouldEnforceUniqueDocNoType() {
        BusinessDocEntity e1 = createValidDoc();
        mapper.insert(e1);

        BusinessDocEntity e2 = createValidDoc();
        e2.setDocNo(e1.getDocNo()); // 相同 doc_no + doc_type
        e2.setDocType(e1.getDocType());
        assertThrows(Exception.class, () -> mapper.insert(e2),
                "(doc_type, doc_no) 有 UNIQUE 约束，重复应失败");
    }

    @Test
    void update_shouldEnforceVersionOptimisticLock() {
        BusinessDocEntity e = createValidDoc();
        mapper.insert(e);

        BusinessDocEntity e2 = mapper.selectById(e.getId());
        e2.setSummary("修改");
        mapper.updateById(e2);

        e.setSummary("旧版本修改");
        assertThrows(Exception.class, () -> mapper.updateById(e),
                "乐观锁：旧版本更新应抛出 OptimisticLockingFailureException");
    }
}