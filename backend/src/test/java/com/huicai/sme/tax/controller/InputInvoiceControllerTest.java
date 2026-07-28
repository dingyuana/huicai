package com.huicai.sme.tax.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.tax.service.impl.InputInvoiceImportService;
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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class InputInvoiceControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private InputInvoiceImportService importService;

    @Test
    @DisplayName("导入预览_MultipartFile正确解析")
    void previewInvoices_multipartFile_parsedCorrectly() throws Exception {
        when(importService.previewInvoices(any())).thenReturn(Map.of("total", 5, "rows", "data"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "invoices.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "test data".getBytes());

        mvc.perform(multipart("/api/sme/tax/v1/input-invoices/preview")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    @DisplayName("确认导入_RequestParam正确绑定")
    void confirmImport_requestParam_boundCorrectly() throws Exception {
        when(importService.confirmImport(eq("batch-202601-001"))).thenReturn(Map.of("status", "success"));

        mvc.perform(post("/api/sme/tax/v1/input-invoices/confirm-import")
                        .param("batchId", "batch-202601-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(importService).confirmImport(eq("batch-202601-001"));
    }

    @Test
    @DisplayName("一步式导入_MultipartFile正确解析")
    void importInvoices_multipartFile_parsedCorrectly() throws Exception {
        when(importService.importInvoices(any())).thenReturn(Map.of("imported", 10));

        MockMultipartFile file = new MockMultipartFile(
                "file", "invoices.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "test data".getBytes());

        mvc.perform(multipart("/api/sme/tax/v1/input-invoices/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.imported").value(10));
    }
}