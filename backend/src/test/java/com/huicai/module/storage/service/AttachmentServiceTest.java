package com.huicai.module.storage.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.storage.entity.AttachmentEntity;
import com.huicai.module.storage.mapper.AttachmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentMapper mapper;
    @Mock private MinioService minioService;
    @InjectMocks private AttachmentService service;

    private AttachmentEntity stub(Long id, String bizType) {
        AttachmentEntity e = new AttachmentEntity();
        e.setId(id);
        e.setBizType(bizType);
        e.setFileName("test.pdf");
        e.setBucketName("huicai");
        return e;
    }

    @Test
    void runOcr_attachment_not_found_throws() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.runOcr(99L, Map.of("bizType", "bank_statement")));
        assertTrue(ex.getMessage().contains("附件不存在"));
    }

    @Test
    void runOcr_bank_statement_returns_4_field_json() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "bank_statement"));
        Map<String, String> hint = new HashMap<>();
        hint.put("bizType", "bank_statement");
        hint.put("txDate", "2026-06-15");
        hint.put("amount", "1000.00");
        hint.put("summary", "客户回款");
        hint.put("counterAccount", "客户A");

        String json = service.runOcr(1L, hint);
        // 验证 4 个字段都在
        assertTrue(json.contains("txDate"));
        assertTrue(json.contains("2026-06-15"));
        assertTrue(json.contains("1000.00"));
        assertTrue(json.contains("客户A"));
        // 持久化
        verify(mapper).updateById(any(AttachmentEntity.class));
    }

    @Test
    void runOcr_persists_ocrData() {
        AttachmentEntity e = stub(1L, "sales_invoice");
        when(mapper.selectById(1L)).thenReturn(e);
        when(mapper.updateById(any(AttachmentEntity.class))).thenReturn(1);

        Map<String, String> hint = new HashMap<>();
        hint.put("bizType", "sales_invoice");
        hint.put("invoiceNo", "INV-001");
        hint.put("customerName", "客户A");

        service.runOcr(1L, hint);
        // 验证 ocrData 被设置
        assertNotNull(e.getOcrData());
        assertTrue(e.getOcrData().contains("INV-001"));
    }
}
