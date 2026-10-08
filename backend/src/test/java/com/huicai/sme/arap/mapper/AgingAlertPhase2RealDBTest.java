package com.huicai.sme.arap.mapper;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.AgingAlertEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P110 Phase 2：AgingAlertEntity 在真实 DB 上的 insert 顺通回路。
 *
 * <p>修复前的问题：Entity 缺 doc_type/party_type 声明（全 NOT NULL），INSERT 必挂。
 * 本测试先由 DB 中的真实功能链接插入：补齐后对全部必填列进行插入。
 */
@DisplayName("P110 Phase 2 逾期预警: insert NPE 保护插入回路")
class AgingAlertPhase2RealDBTest extends AbstractMapperTest {

    @Autowired
    private AgingAlertMapper alertMapper;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    private Long newRequiredBusinessDoc() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("INV-20261001-001");
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(LocalDate.now().minusDays(120));
        doc.setPeriod("202610");
        doc.setCustomerId(1L);
        doc.setAmount(new BigDecimal("10000"));
        doc.setUnsettledAmount(new BigDecimal("5000"));
        businessDocMapper.insert(doc);
        return doc.getId();
    }

    @Test
    @DisplayName("补齐 docType/partyType 后 insert 成功，round-trip 列一致")
    void insert_roundTrip() {
        Long docId = newRequiredBusinessDoc();

        AgingAlertEntity e = new AgingAlertEntity();
        e.setDocId(docId);
        e.setDocType("INVOICE_OUT");
        e.setCustomerId(1L);
        e.setPartyType("CUSTOMER");
        e.setDueDate(LocalDate.now().minusDays(30));
        e.setOverdueDays(30);
        e.setUnsettledAmount(new BigDecimal("5000.00"));
        e.setAlertLevel("WARNING");
        e.setStatus("ACTIVE");
        e.setEnterpriseId(DEFAULT_ENTERPRISE_ID);

        int n = alertMapper.insert(e);
        assertEquals(1, n);

        AgingAlertEntity back = alertMapper.selectById(e.getId());
        assertNotNull(back);
        assertEquals("INVOICE_OUT", back.getDocType(), "doc_type 回读一致");
        assertEquals("CUSTOMER", back.getPartyType(), "party_type 回读一致");
        assertEquals("WARNING", back.getAlertLevel(), "alert_level 回读一致且通过 CHECK");
        assertEquals(new BigDecimal("5000.00"), back.getUnsettledAmount());
    }

    @Test
    @DisplayName("负向：alertLevel 违背 DB CHECK（MILD 不在允许集）⇒ insert 必败")
    void insert_invalidAlertLevel_rejected() {
        Long docId = newRequiredBusinessDoc();

        AgingAlertEntity e = new AgingAlertEntity();
        e.setDocId(docId);
        e.setDocType("INVOICE_OUT");
        e.setCustomerId(1L);
        e.setPartyType("CUSTOMER");
        e.setDueDate(LocalDate.now().minusDays(10));
        e.setOverdueDays(10);
        e.setUnsettledAmount(new BigDecimal("1000.00"));
        e.setAlertLevel("MILD");          // 旧 service 产出的非法字面量
        e.setStatus("ACTIVE");
        e.setEnterpriseId(DEFAULT_ENTERPRISE_ID);

        assertThrows(Exception.class, () -> alertMapper.insert(e),
                "alert_level=MILD 不在 DB CHECK 允许集，必须被拒绝");
    }
}
