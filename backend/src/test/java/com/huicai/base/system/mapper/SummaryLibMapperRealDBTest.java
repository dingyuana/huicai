package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.SummaryLibEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SummaryLib Mapper 真实 DB 测试.
 */
class SummaryLibMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private SummaryLibMapper summaryLibMapper;

    @Test
    void insert_shouldReturnId() {
        SummaryLibEntity entity = new SummaryLibEntity();
        entity.setSummaryCode("SUM_" + System.currentTimeMillis());
        entity.setSummaryText("测试摘要内容");
        entity.setCategory("expense");
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setEnterpriseId(1L);
        entity.setDeleted(0);

        assertEquals(1, summaryLibMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}