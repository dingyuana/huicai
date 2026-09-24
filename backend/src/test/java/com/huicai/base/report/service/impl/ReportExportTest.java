package com.huicai.base.report.service.impl;

import com.huicai.base.report.mapper.ReportDataMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P89-D 导出测试。
 *
 * 覆盖 writeExcel 的抬头结构与 4 张报表导出的可执行性：
 *   第 0 行 = 报表标题
 *   第 1 行 = 期间 + 制表人 + 制表日期
 *   第 2 行 = 表头
 *   第 3 行起 = 数据
 *
 * 为什么必须测：writeExcel 是 4 张报表导出共用的唯一出口，抬头行一旦错位，
 * 4 张表同时错且无告警，且 Excel 里"看起来正常"——人眼很难发现列整体下移两行。
 *
 * 两个已踩的坑（记录以防回退）：
 * 1. 不能用 MockHttpServletResponse + when(response.getOutputStream())：
 *    该方法是 final，Mockito 报 MissingMethodInvocationException。
 *    这里改为 mock HttpServletResponse 接口本身。
 * 2. 严格 stubbing：when() 必须与方法真实调用完全匹配（含参数），
 *    多 stub 会报 UnnecessaryStubbingException。
 */
@ExtendWith(MockitoExtension.class)
class ReportExportTest {

    private static final String PERIOD = "202601";

    @Mock private ReportDataMapper reportDataMapper;

    private ReportServiceImpl service;
    private HttpServletResponse response;
    private ByteArrayOutputStream capture;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportDataMapper);
        response = mock(HttpServletResponse.class);
    }

    /** 捕获 export 写出的字节到内存 */
    private ByteArrayOutputStream captureStream() throws IOException {
        capture = new ByteArrayOutputStream();
        ServletOutputStream sos = new ServletOutputStream() {
            private boolean ready = true;

            @Override
            public boolean isReady() {
                return ready;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // 单测无异步 IO；置 false 使 writeReady 永不被调用
                ready = false;
            }

            @Override
            public void write(int b) {
                capture.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                capture.write(b, off, len);
            }
        };
        when(response.getOutputStream()).thenReturn(sos);
        return capture;
    }

    @Test
    void exportBalanceSheet_writes_xlsx_bytes() throws IOException {
        ByteArrayOutputStream out = captureStream();
        when(reportDataMapper.subjectBalance(PERIOD)).thenReturn(new ArrayList<>());

        service.exportBalanceSheet(PERIOD, response);

        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 0, "导出应产生非空内容");
        // xlsx 是 zip 容器，以 PK 魔数开头
        assertEquals((byte) 'P', bytes[0]);
        assertEquals((byte) 'K', bytes[1]);
    }

    @Test
    void exportBalanceSheet_sets_content_type_and_period_in_filename() throws IOException {
        captureStream();
        when(reportDataMapper.subjectBalance(PERIOD)).thenReturn(new ArrayList<>());

        service.exportBalanceSheet(PERIOD, response);

        verify(response).setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verify(response).setHeader(eq("Content-Disposition"), argThat(v ->
                v != null && v.startsWith("attachment;filename=") && v.contains(PERIOD)));
    }

    @Test
    void exportAllFour_write_non_empty_content() throws IOException {
        when(reportDataMapper.subjectBalance(any())).thenReturn(new ArrayList<>());
        when(reportDataMapper.incomeStatementData(any())).thenReturn(Map.of());
        when(reportDataMapper.cumulativeData(any(), any())).thenReturn(Map.of());
        when(reportDataMapper.cashFlowData(any())).thenReturn(new ArrayList<>());
        when(reportDataMapper.cashSubjectBalance(any())).thenReturn(Map.of());

        ByteArrayOutputStream out = captureStream();
        service.exportSubjectBalance(PERIOD, response);
        assertTrue(out.size() > 0, "科目余额表导出应为非空");

        ByteArrayOutputStream out2 = captureStream();
        service.exportBalanceSheet(PERIOD, response);
        assertTrue(out2.size() > 0, "资产负债表导出应为非空");

        ByteArrayOutputStream out3 = captureStream();
        service.exportIncomeStatement(PERIOD, response);
        assertTrue(out3.size() > 0, "利润表导出应为非空");

        ByteArrayOutputStream out4 = captureStream();
        service.exportCashFlow(PERIOD, response);
        assertTrue(out4.size() > 0, "现金流量表导出应为非空");
    }

    @Test
    void writeExcel_survives_no_login_context() throws IOException {
        // writeExcel 取制表人时调 SecurityUtils.getCurrentUsername()，
        // 无 SecurityContext（单测环境）会抛 BusinessException。
        // 方法内 catch 必须吞掉、不阻断导出——否则无登录上下文时所有导出全挂。
        captureStream();
        when(reportDataMapper.subjectBalance(PERIOD)).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> service.exportBalanceSheet(PERIOD, response));
    }

    @Test
    void exportExcel_header_rows_match_spec(@TempDir Path tmp) throws IOException {
        // 核心断言：逐格校验抬头四段结构。
        // 单测无 SecurityContext，制表人应为兜底值"未知"。
        ByteArrayOutputStream out = captureStream();
        when(reportDataMapper.subjectBalance(PERIOD)).thenReturn(new ArrayList<>());

        service.exportBalanceSheet(PERIOD, response);

        File xlsx = tmp.resolve("资产负债表_" + PERIOD + ".xlsx").toFile();
        try (FileOutputStream fos = new FileOutputStream(xlsx)) {
            fos.write(out.toByteArray());
        }

        try (InputStream in = Files.newInputStream(xlsx.toPath());
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);

            // 第 0 行：报表标题
            assertEquals("资产负债表", cellText(sheet, 0, 0));
            // 第 1 行：期间（左） + 制表人/制表日期（右）
            assertEquals("期间：202601", cellText(sheet, 1, 0));
            String meta = cellText(sheet, 1, 2);
            assertTrue(meta.contains("制表人："), "制表信息应含制表人，实际: " + meta);
            assertTrue(meta.contains("制表日期："), "制表信息应含制表日期，实际: " + meta);
            // 制表人兜底值
            assertTrue(meta.contains("未知"), "无登录上下文时制表人应为兜底值，实际: " + meta);
            // 第 2 行：表头
            assertEquals("项目", cellText(sheet, 2, 0));
            assertEquals("行次", cellText(sheet, 2, 1));
            assertEquals("期末余额", cellText(sheet, 2, 2));
            assertEquals("年初余额", cellText(sheet, 2, 3));
            // 第 3 行起：数据。无科目数据时，只有两行小计（行次列为空）
            assertEquals("资产总计", cellText(sheet, 3, 0));
            assertEquals("负债+所有者权益合计", cellText(sheet, 4, 0));
            assertEquals(5, sheet.getLastRowNum() + 1, "无科目数据时总行数应为 5");
        }
    }

    private static String cellText(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null || row.getCell(colIdx) == null) {
            return "";
        }
        return row.getCell(colIdx).toString();
    }
}
