package com.huicai.sme.cash.service.impl;

import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.base.business.util.ColumnMappingResolver;
import com.huicai.sme.cash.service.BankStatementService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * D5 金额精度（REQ-2026-134 / P107）：银行流水 Excel 导入不得引入浮点尾巴。
 *
 * <p>缺陷：previewExcel 两个重载均用 {@code new BigDecimal(val)}（val 是 double），
 * 把 Excel 数值单元格 0.1 转成 0.1000000000000000055511151231257827…（二进制展开）。
 * 该长尾字符串随后经 parseRow 变成 BigDecimal，①预览金额显示垃圾；②更重要的是
 * countDuplicate(…, amount) 用精确数值比对 NUMERIC(18,2) 列（0.10）——
 * 0.10000000000000000555… ≠ 0.10，**重复流水判定系统性失效**。
 *
 * <p>修复：{@code BigDecimal.valueOf(val).toPlainString()}（Double.toString 最短往返）。
 *
 * <p>本测试用真实 POI 建工作簿走完整解析链，不 mock 被测解析逻辑。
 */
class BankStatementExcelImportServiceTest {

    private BankStatementExcelImportService newService() {
        return new BankStatementExcelImportService(
                mock(BankStatementMapper.class),
                mock(BankStatementService.class),
                new ColumnMappingResolver());
    }

    /** 数值 0.1 的双精度形态（Excel 单元格存 double）→ 期望解析为 0.1 而非长尾垃圾 */
    private MockMultipartFile xlsxWithAmount(String dateHeader, String amountHeader, double amountVal) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("流水");
            // findHeaderRow 要求表头行 ≥5 个非空单元格（真实银行流水 5+ 列）
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue(dateHeader);
            header.createCell(1).setCellValue(amountHeader);
            header.createCell(2).setCellValue("摘要");
            header.createCell(3).setCellValue("对方账号");
            header.createCell(4).setCellValue("交易类型");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("2026-01-05");
            data.createCell(1).setCellValue(amountVal);
            data.createCell(2).setCellValue("货款");
            data.createCell(3).setCellValue("6222000000000001");
            data.createCell(4).setCellValue("来账");
            wb.write(out);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void previewExcel_显式列映射_金额不得引入浮点尾巴() throws Exception {
        MockMultipartFile file = xlsxWithAmount("交易日期", "金额", 0.1d);
        Map<String, Object> result = newService().previewExcel(1L, file,
                Map.of("TX_DATE", "交易日期", "AMOUNT", "金额"));

        List<?> previews = (List<?>) result.get("previews");
        assertEquals(1, previews.size(), "应解析出 1 条预览: " + result.get("errors"));
        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>) previews.get(0);
        assertEquals(Boolean.FALSE, preview.get("isError"),
                "解析不应报错，errorMessage=" + preview.get("errorMessage"));
        BigDecimal amount = (BigDecimal) preview.get("amount");
        assertNotNull(amount, "金额不得为空");
        assertEquals(0, amount.compareTo(new BigDecimal("0.1")),
                "0.1 不得产生浮点尾巴，实际: " + amount.toPlainString());
        assertTrue(amount.scale() <= 2, "金额 scale 应受控(<=2)，实际: " + amount.scale());
    }

    @Test
    void previewExcel_自动列映射_金额不得引入浮点尾巴() throws Exception {
        MockMultipartFile file = xlsxWithAmount("交易日期", "金额", 1234.56d);
        Map<String, Object> result = newService().previewExcel(1L, file);

        List<?> previews = (List<?>) result.get("previews");
        assertEquals(1, previews.size(), "应解析出 1 条预览: " + result.get("errors"));
        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>) previews.get(0);
        assertEquals(Boolean.FALSE, preview.get("isError"),
                "解析不应报错，errorMessage=" + preview.get("errorMessage"));
        BigDecimal amount = (BigDecimal) preview.get("amount");
        assertNotNull(amount, "金额不得为空");
        assertEquals(0, amount.compareTo(new BigDecimal("1234.56")),
                "1234.56 不得产生浮点尾巴，实际: " + amount.toPlainString());
    }
}
