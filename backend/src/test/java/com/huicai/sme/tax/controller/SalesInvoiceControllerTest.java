package com.huicai.sme.tax.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.arap.dto.BusinessDocQueryDTO;
import com.huicai.sme.arap.dto.BusinessDocVO;
import com.huicai.sme.arap.service.BusinessDocService;
import com.huicai.sme.tax.service.impl.SalesInvoiceImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 销售发票导入 Controller 层测试 (P0).
 *
 * <p>测试覆盖：
 * <ol>
 *   <li>MultipartFile 文件上传</li>
 *   <li>批量操作接口</li>
 *   <li>查询参数默认值</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SalesInvoiceControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private SalesInvoiceImportService importService;

    @MockBean
    private BusinessDocService businessDocService;

    // ==================== 文件上传测试 ====================

    @Test
    @DisplayName("发票预览_文件参数正确绑定_Service被调用")
    void previewInvoices_multipartFile_boundCorrectly() throws Exception {
        // given
        when(importService.previewInvoices(any())).thenReturn(Map.of(
                "total", 10,
                "success", 10,
                "items", Map.of()
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoices.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "mock excel content".getBytes()
        );

        // when & then
        mvc.perform(multipart("/api/sme/tax/v1/sales-invoices/preview")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.success").value(10));

        verify(importService).previewInvoices(argThat(f ->
                f.getOriginalFilename() != null &&
                f.getOriginalFilename().endsWith(".xlsx")
        ));
    }

        // 参数正确时 Service 被调用，框架层面的验证省略

    @Test
    @DisplayName("一步式导入_文件参数正确绑定")
    void importInvoices_multipartFile_boundCorrectly() throws Exception {
        // given
        when(importService.importInvoices(any())).thenReturn(Map.of(
                "batchId", "BATCH-001",
                "imported", 5
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoices.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "mock excel content".getBytes()
        );

        // when & then
        mvc.perform(multipart("/api/sme/tax/v1/sales-invoices/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value("BATCH-001"))
                .andExpect(jsonPath("$.data.imported").value(5));

        verify(importService).importInvoices(any());
    }

    // ==================== 确认导入测试 ====================

    @Test
    @DisplayName("确认导入_batchId参数正确传递")
    void confirmImport_batchId_passedToService() throws Exception {
        // given
        when(importService.confirmImport(eq("BATCH-12345"))).thenReturn(Map.of(
                "batchId", "BATCH-12345",
                "created", 8,
                "updated", 2
        ));

        // when & then
        mvc.perform(post("/api/sme/tax/v1/sales-invoices/confirm-import")
                        .param("batchId", "BATCH-12345")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value("BATCH-12345"))
                .andExpect(jsonPath("$.data.created").value(8));

        verify(importService).confirmImport(eq("BATCH-12345"));
    }

        // 参数正确时 Service 被调用，框架层面的验证省略

    // ==================== 批量操作测试 ====================

    @Test
    @DisplayName("批量红冲关联_无参数接口正确调用")
    void batchLinkRedFlush_noParams_serviceCalled() throws Exception {
        // given
        when(importService.batchLinkRedFlushInvoices()).thenReturn(Map.of(
                "scanned", 100,
                "matched", 15,
                "updated", 15
        ));

        // when & then
        mvc.perform(post("/api/sme/tax/v1/sales-invoices/batch-link-red-flush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(100))
                .andExpect(jsonPath("$.data.matched").value(15));

        verify(importService).batchLinkRedFlushInvoices();
    }

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("分页查询_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        // given
        IPage<BusinessDocVO> page = new Page<>(1, 20);
        when(businessDocService.pageQuery(any(BusinessDocQueryDTO.class))).thenReturn(page);

        // when & then - 不传参数，使用默认值 current=1, size=20
        mvc.perform(get("/api/sme/tax/v1/sales-invoices/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(businessDocService).pageQuery(argThat(query ->
                query.getCurrent().equals(1) &&
                query.getSize().equals(20) &&
                "INVOICE_OUT".equals(query.getDocType())
        ));
    }

    @Test
    @DisplayName("分页查询_自定义参数正确传递")
    void page_customParams_passedCorrectly() throws Exception {
        // given
        IPage<BusinessDocVO> page = new Page<>(3, 100);
        when(businessDocService.pageQuery(any(BusinessDocQueryDTO.class))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/sme/tax/v1/sales-invoices/page")
                        .param("current", "3")
                        .param("size", "100"))
                .andExpect(status().isOk());

        verify(businessDocService).pageQuery(argThat(query ->
                query.getCurrent().equals(3) &&
                query.getSize().equals(100) &&
                "INVOICE_OUT".equals(query.getDocType())
        ));
    }
}
