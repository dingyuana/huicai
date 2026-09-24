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
import java.util.HashMap;
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
    void exportSubjectBalance_direction_column_is_chinese(@TempDir Path tmp) throws IOException {
        // 方向列曾导出原始英文 "debit/credit"，与前端显示的"借/贷"不一致。
        // 统一为中文后需断言：列名、借方科目、贷方科目、direction 缺失时的兜底值。
        ByteArrayOutputStream out = captureStream();
        // 用 HashMap 而非 Map.of：Map.of 不允许 null 值，而 direction 缺失正是要测的场景
        List<Map<String, Object>> balances = new ArrayList<>();
        balances.add(balanceRow("1001", "库存现金", "debit", 100, 50, 0, 150));
        balances.add(balanceRow("6001", "主营业务收入", "credit", 0, 0, 800, 800));
        balances.add(balanceRow("1901", "无方向科目", null, 0, 0, 0, 0));
        when(reportDataMapper.subjectBalance(PERIOD)).thenReturn(balances);

        service.exportSubjectBalance(PERIOD, response);

        File xlsx = tmp.resolve("科目余额表_" + PERIOD + ".xlsx").toFile();
        try (FileOutputStream fos = new FileOutputStream(xlsx)) {
            fos.write(out.toByteArray());
        }

        try (InputStream in = Files.newInputStream(xlsx.toPath());
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);

            // 表头：方向列改名"余额方向"（与前端列名一致）
            assertEquals("科目编码", cellText(sheet, 2, 0));
            assertEquals("科目名称", cellText(sheet, 2, 1));
            assertEquals("余额方向", cellText(sheet, 2, 2));

            // 数据行从第 3 行起
            assertEquals("1001", cellText(sheet, 3, 0));
            assertEquals("借", cellText(sheet, 3, 2), "debit 应导出为中文'借'");
            assertEquals("6001", cellText(sheet, 4, 0));
            assertEquals("贷", cellText(sheet, 4, 2), "credit 应导出为中文'贷'");
            assertEquals("—", cellText(sheet, 5, 2), "direction 缺失应兜底为 '—'");

            // 抬头含审核人留白
            assertTrue(cellText(sheet, 1, 2).contains("审核人：待审核"));
        }
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
            // 第 1 行：期间（左） + 制表人/制表日期/审核人（右）
            assertEquals("期间：202601", cellText(sheet, 1, 0));
            String meta = cellText(sheet, 1, 2);
            assertTrue(meta.contains("制表人："), "制表信息应含制表人，实际: " + meta);
            assertTrue(meta.contains("制表日期："), "制表信息应含制表日期，实际: " + meta);
            assertTrue(meta.contains("审核人：待审核"), "PRD §4.2 指定审核人留白占位，实际: " + meta);
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

    @Test
    void exportCashFlow_3列时制表人行不合并且内容正确(@TempDir Path tmp) throws IOException {
        // 现金流量表是 3 列表（项目/行次/本期金额），唯一走 cols<4 降级分支的报表。
        // 该分支曾因 merge(2,2) 单格合并在线上抛
        // "Merged region must contain 2 or more cells" 导致导出 500，故必须逐格断言而非只看"非空"。
        // 注：rows 由 exportCashFlow 硬编码生成 13 行，与 mapper 返回内容无关，
        // 所以本用例无需依赖 stub 的数据即可覆盖该分支。
        ByteArrayOutputStream out = captureStream();
        when(reportDataMapper.cashFlowData(PERIOD)).thenReturn(new ArrayList<>());
        when(reportDataMapper.cashSubjectBalance(PERIOD)).thenReturn(Map.of());

        service.exportCashFlow(PERIOD, response);

        File xlsx = tmp.resolve("现金流量表_" + PERIOD + ".xlsx").toFile();
        try (FileOutputStream fos = new FileOutputStream(xlsx)) {
            fos.write(out.toByteArray());
        }

        try (InputStream in = Files.newInputStream(xlsx.toPath());
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);

            assertEquals("现金流量表", cellText(sheet, 0, 0), "第0行标题");
            assertEquals("期间：202601", cellText(sheet, 1, 0), "第1行期间");
            // 3 列时不合并：制表人信息落在第 2 列（索引 2），且内容完整
            String meta = cellText(sheet, 1, 2);
            assertTrue(meta.contains("制表人："), "制表人信息应落在第2列，实际: " + meta);
            assertTrue(meta.contains("制表日期："), "制表日期应存在，实际: " + meta);
            assertTrue(meta.contains("审核人：待审核"), "审核人留白占位应存在，实际: " + meta);

            assertEquals("项目", cellText(sheet, 2, 0));
            assertEquals("行次", cellText(sheet, 2, 1));
            assertEquals("本期金额", cellText(sheet, 2, 2));
            // 13 行硬编码数据 → 总行数 2(抬头) + 1(表头) + 13
            assertEquals(16, sheet.getLastRowNum() + 1, "现金流量表总行数应为 16");
            assertEquals("经营活动现金流入", cellText(sheet, 3, 0), "首个数据行");
            assertEquals("勾稽校验", cellText(sheet, 15, 0), "末行为勾稽校验");
        }
    }

    /** 构造科目余额行。direction 可为 null（测方向列兜底值） */
    private static Map<String, Object> balanceRow(String code, String name, String direction,
                                                   long begin, long debit, long credit, long end) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("direction", direction);
        m.put("begin_balance", begin);
        m.put("debit_total", debit);
        m.put("credit_total", credit);
        m.put("end_balance", end);
        return m;
    }

    private static String cellText(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null || row.getCell(colIdx) == null) {
            return "";
        }
        return row.getCell(colIdx).toString();
    }
}
