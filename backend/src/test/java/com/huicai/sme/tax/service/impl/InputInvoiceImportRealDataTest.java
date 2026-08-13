package com.huicai.sme.tax.service.impl;

import com.huicai.base.business.mapper.BusinessDocEntryMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.util.ColumnMappingResolver;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 进项发票 Excel 导入 — 真实 Excel 文件解析测试.
 *
 * <p>⚠️ 注意：本测试属于 L1 纯逻辑测试（MockitoExtension）。
 * 虽然生成真实的 .xlsx 文件流，但所有 9 个外部依赖（Mapper/Service）均为 @Mock，
 * 因此不验证 DB 写入、不触发业务逻辑、不验证 Aspect 审计日志。
 *
 * <p>测试范围仅限于 Excel 解析逻辑（表头识别、日期格式解析、行数据提取）。
 * 真实的导入流程（含 DB 写入）见 InputInvoiceImportServiceTest（Testcontainers 版本）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("进项发票 - 真实 Excel 解析测试")
class InputInvoiceImportRealDataTest {

    @Mock private BusinessDocMapper docMapper;
    @Mock private BusinessDocEntryMapper docEntryMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private VendorMapper vendorMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private InputInvoiceMapper inputInvoiceMapper;
    @Mock private InvoiceDedupUtil invoiceDedupUtil;

    private InputInvoiceImportService service;
    private final ColumnMappingResolver realResolver = new ColumnMappingResolver();

    @TestFactory
    @DisplayName("真实 Excel 解析")
    Stream<DynamicTest> realExcelParsing() {
        service = new InputInvoiceImportService(
                docMapper, docEntryMapper, voucherMapper, voucherEntryMapper,
                voucherNoService, vendorMapper, subjectMapper, inputInvoiceMapper,
                realResolver, invoiceDedupUtil);

        lenient().when(invoiceDedupUtil.findExisting(any())).thenReturn(Collections.emptySet());
        lenient().when(vendorMapper.selectList(any())).thenReturn(Collections.emptyList());
        injectBatchCache();

        byte[] excelBytes = createExcelBytes();

        return Stream.of(
                DynamicTest.dynamicTest("解析 3 行数据", () -> {
                    MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);
                    Map<String, Object> result = service.previewInvoices(file);
                    assertEquals(3, ((Number) result.get("total")).intValue());
                }),

                DynamicTest.dynamicTest("包含 batchId", () -> {
                    MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);
                    Map<String, Object> result = service.previewInvoices(file);
                    assertNotNull(result.get("batchId"));
                    assertTrue(((String) result.get("batchId")).startsWith("PRE_IN_"));
                }),

                DynamicTest.dynamicTest("无错误行", () -> {
                    MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);
                    Map<String, Object> result = service.previewInvoices(file);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
                    assertTrue(errors == null || errors.isEmpty());
                })
        );
    }

    @TestFactory
    @DisplayName("日期格式兼容性")
    Stream<DynamicTest> dateFormatCompatibility() {
        service = new InputInvoiceImportService(
                docMapper, docEntryMapper, voucherMapper, voucherEntryMapper,
                voucherNoService, vendorMapper, subjectMapper, inputInvoiceMapper,
                realResolver, invoiceDedupUtil);

        lenient().when(invoiceDedupUtil.findExisting(any())).thenReturn(Collections.emptySet());
        lenient().when(vendorMapper.selectList(any())).thenReturn(Collections.emptyList());
        injectBatchCache();

        return Stream.of(
                DynamicTest.dynamicTest("yyyy-MM-dd", () -> {
                    var result = previewSingleDate("2026-06-01");
                    assertEquals(1, ((Number) result.get("total")).intValue());
                }),
                DynamicTest.dynamicTest("yyyy/MM/dd", () -> {
                    var result = previewSingleDate("2026/06/05");
                    assertEquals(1, ((Number) result.get("total")).intValue());
                }),
                DynamicTest.dynamicTest("yyyyMMdd", () -> {
                    var result = previewSingleDate("20260610");
                    assertEquals(1, ((Number) result.get("total")).intValue());
                })
        );
    }

    @TestFactory
    @DisplayName("异常处理")
    Stream<DynamicTest> errorHandling() {
        service = new InputInvoiceImportService(
                docMapper, docEntryMapper, voucherMapper, voucherEntryMapper,
                voucherNoService, vendorMapper, subjectMapper, inputInvoiceMapper,
                realResolver, invoiceDedupUtil);

        return Stream.of(
                DynamicTest.dynamicTest("空文件抛异常", () -> {
                    MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
                    assertThrows(Exception.class, () -> service.previewInvoices(empty));
                })
        );
    }

    private Map<String, Object> previewSingleDate(String dateStr) {
        byte[] excel = createExcelWithDate(dateStr);
        MockMultipartFile file = new MockMultipartFile("file", "d.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        return service.previewInvoices(file);
    }

    private byte[] createExcelBytes() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("发票导入");
            String[] headers = {"发票号码", "发票代码", "开票日期", "销方名称", "销方税号",
                    "金额", "税额", "价税合计", "发票类型", "货物名称", "税率"};
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) hr.createCell(i).setCellValue(headers[i]);

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("INV-R-001");
            r1.createCell(1).setCellValue("3100212345");
            r1.createCell(2).setCellValue("2026-06-01");
            r1.createCell(3).setCellValue("供应商A");
            r1.createCell(4).setCellValue("91310115MA1H12345X");
            r1.createCell(5).setCellValue(10000.00);
            r1.createCell(6).setCellValue(1300.00);
            r1.createCell(7).setCellValue(11300.00);
            r1.createCell(8).setCellValue("增值税专用发票");
            r1.createCell(9).setCellValue("办公用品");
            r1.createCell(10).setCellValue(13);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("INV-R-002");
            r2.createCell(1).setCellValue("3100212346");
            r2.createCell(2).setCellValue("2026/06/05");
            r2.createCell(3).setCellValue("供应商B");
            r2.createCell(4).setCellValue("91310115MA1H12346X");
            r2.createCell(5).setCellValue(50000.00);
            r2.createCell(6).setCellValue(6500.00);
            r2.createCell(7).setCellValue(56500.00);
            r2.createCell(8).setCellValue("增值税专用发票");
            r2.createCell(9).setCellValue("设备");
            r2.createCell(10).setCellValue(13);

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("INV-R-003");
            r3.createCell(1).setCellValue("3100212347");
            r3.createCell(2).setCellValue("20260610");
            r3.createCell(3).setCellValue("供应商C");
            r3.createCell(4).setCellValue("91310115MA1H12347X");
            r3.createCell(5).setCellValue(120000.00);
            r3.createCell(6).setCellValue(15600.00);
            r3.createCell(7).setCellValue(135600.00);
            r3.createCell(8).setCellValue("增值税专用发票");
            r3.createCell(9).setCellValue("原材料");
            r3.createCell(10).setCellValue(13);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createExcelWithDate(String dateStr) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("发票");
            Row hr = sheet.createRow(0);
            hr.createCell(0).setCellValue("发票号码");
            hr.createCell(1).setCellValue("开票日期");
            hr.createCell(2).setCellValue("金额");
            hr.createCell(3).setCellValue("税额");
            hr.createCell(4).setCellValue("货物名称");
            hr.createCell(5).setCellValue("税率");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("INV-DATE-001");
            r.createCell(1).setCellValue(dateStr);
            r.createCell(2).setCellValue(1000.00);
            r.createCell(3).setCellValue(130.00);
            r.createCell(4).setCellValue("商品");
            r.createCell(5).setCellValue(13);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void injectBatchCache() {
        try {
            Field cacheField = InputInvoiceImportService.class.getDeclaredField("batchCache");
            cacheField.setAccessible(true);
            cacheField.set(service, new ConcurrentHashMap<>());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}