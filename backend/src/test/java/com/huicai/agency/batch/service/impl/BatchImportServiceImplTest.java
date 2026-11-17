package com.huicai.agency.batch.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.service.impl.InputInvoiceImportService;
import com.huicai.sme.tax.service.impl.SalesInvoiceImportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchImportServiceImplTest {

    private final InputInvoiceImportService inputImporter = mock(InputInvoiceImportService.class);
    private final SalesInvoiceImportService outputImporter = mock(SalesInvoiceImportService.class);
    private final AgencyBatchTransactionRunner transactionRunner =
            mock(AgencyBatchTransactionRunner.class);
    private final BatchImportServiceImpl service =
            new BatchImportServiceImpl(inputImporter, outputImporter, transactionRunner);

    @Test
    void dispatchesByInvoiceTypeAndReportsImportedFiles() {
        var file = new MockMultipartFile("files", "invoices.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        when(inputImporter.previewInvoices(file)).thenReturn(Map.of(
                "batchId", "input-batch", "total", 1, "errors", List.of()));
        when(inputImporter.confirmImport("input-batch")).thenReturn(new HashMap<>(Map.of(
                "success", 1, "duplicateSkipped", 0, "errors", List.of())));
        runInline();

        var result = service.importInvoices(List.of(file), 8L, "input");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(0, result.getFailed());
        verify(inputImporter).confirmImport("input-batch");
        verify(outputImporter, never()).previewInvoices(any());
    }

    @Test
    void parseErrorsMakeTheFilePartialFailureRatherThanFullSuccess() {
        var file = new MockMultipartFile("files", "invoices.xlsx", "application/octet-stream", new byte[]{1});
        when(outputImporter.previewInvoices(file)).thenReturn(Map.of(
                "batchId", "output-batch",
                "total", 1,
                "errors", List.of(Map.of("row", 3, "message", "金额错误"))));
        when(outputImporter.confirmImport("output-batch")).thenReturn(new HashMap<>(Map.of(
                "success", 1, "duplicateSkipped", 0, "errors", List.of())));
        runInline();

        var result = service.importInvoices(List.of(file), 8L, "output");

        assertEquals(0, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertFalse(result.getDetails().get(0).isSuccess());
        assertTrue(result.getDetails().get(0).getMessage().contains("金额错误"));
    }

    @Test
    void parseFailuresAreReportedWhenNoRowsCanBeImported() {
        var file = new MockMultipartFile("files", "invoices.xlsx", "application/octet-stream", new byte[]{1});
        when(outputImporter.previewInvoices(file)).thenReturn(Map.of(
                "batchId", "output-batch",
                "total", 0,
                "errors", List.of(Map.of("row", 2, "message", "发票号缺失"))));
        when(outputImporter.confirmImport("output-batch")).thenReturn(new HashMap<>(Map.of(
                "success", 0, "duplicateSkipped", 0, "errors", List.of())));
        runInline();

        var result = service.importInvoices(List.of(file), 8L, "output");

        assertEquals(0, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertTrue(result.getDetails().get(0).getMessage().contains("发票号缺失"));
    }

    @Test
    void unsupportedFilesAreReportedWithoutEnteringImporter() {
        var file = new MockMultipartFile("files", "invoice.pdf", "application/pdf", new byte[]{1});
        runInline();

        var result = service.importInvoices(List.of(file), 8L, "output");

        assertEquals(1, result.getFailed());
        assertFalse(result.getDetails().get(0).isSuccess());
        verify(outputImporter, never()).previewInvoices(any());
    }

    @Test
    void oneUnsupportedFileDoesNotPreventFollowingFilesFromImporting() {
        var unsupported = new MockMultipartFile("files", "invoice.pdf", "application/pdf", new byte[]{1});
        var supported = new MockMultipartFile("files", "invoice.xlsx", "application/octet-stream", new byte[]{1});
        when(outputImporter.previewInvoices(supported)).thenReturn(Map.of(
                "batchId", "output-batch", "total", 1, "errors", List.of()));
        when(outputImporter.confirmImport("output-batch")).thenReturn(new HashMap<>(Map.of(
                "success", 1, "duplicateSkipped", 0, "errors", List.of())));
        runInline();

        var result = service.importInvoices(List.of(unsupported, supported), 8L, "output");

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(1, result.getFailed());
        verify(outputImporter).previewInvoices(supported);
    }

    @Test
    void duplicateRowsAreReportedAsHandledWithoutMarkingTheFileFailed() {
        var file = new MockMultipartFile("files", "invoices.xlsx", "application/octet-stream", new byte[]{1});
        when(outputImporter.previewInvoices(file)).thenReturn(Map.of(
                "batchId", "output-batch", "total", 1, "errors", List.of()));
        when(outputImporter.confirmImport("output-batch")).thenReturn(new HashMap<>(Map.of(
                "success", 0, "duplicateSkipped", 1, "errors", List.of())));
        runInline();

        var result = service.importInvoices(List.of(file), 8L, "output");

        assertEquals(1, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertTrue(result.getDetails().get(0).getMessage().contains("重复跳过 1 行"));
    }

    @Test
    void invalidTypeOrEmptyFileListIsRejected() {
        assertThrows(BusinessException.class, () -> service.importInvoices(List.of(), 8L, "output"));
        assertThrows(BusinessException.class,
                () -> service.importInvoices(List.of(), 8L, "unknown"));
    }

    private void runInline() {
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(transactionRunner).run(anyLong(), any(Supplier.class));
    }
}
