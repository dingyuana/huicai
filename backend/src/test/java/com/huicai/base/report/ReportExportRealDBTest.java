package com.huicai.base.report;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * SPEC-P154 L5（后端侧收口）：报表<b>导出件解析断言</b>——v4.0「导出有则测」。
 *
 * <p>导出端点（{@code /subject-balance/export}）此前只被 {@code ReportExportTest}
 * （Mockito 桩）验过「写了字节流」，<b>从没验过写出来的内容</b>。本探针下载真 xlsx
 * 并用 hutool 读 API（与生产写出同库）解析，锁三件事：</p>
 * <ol>
 *   <li>文件结构：标题行 + 表头（科目编码…期末余额）+ 数据行齐全；</li>
 *   <li>数值勾稽：独立造数场景（切片二同款：期初 100,000 + 借 10,000 − 贷 2,000）
 *       的期末余额在导出件里逐字对上；</li>
 *   <li><b>导出 = 在线同源</b>：同一时刻导出件与 JSON API 的期末余额必须一致——
 *       两条渲染路径（ExcelWriter vs JSON）共享同一查询，出现分叉即是导出专属缺陷。</li>
 * </ol>
 *
 * <p><b>同 code 撞行口径</b>（同切片二）：测试容器超级用户绕 RLS，导出含 ent0 参考库
 * 同码行——断言用「code 且金额匹配的行存在」（存在性），不断言唯一行。</p>
 */
@DisplayName("SPEC-P154 L5：科目余额表导出件解析 —— 结构/数值/在线同源三断言")
@AutoConfigureMockMvc
class ReportExportRealDBTest extends AbstractMapperTest {

    private static final String PERIOD = "202612";
    private static final BigDecimal TOL = new BigDecimal("0.01");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherEntryMapper voucherEntryMapper;
    @Autowired private VoucherService voucherService;
    private final ObjectMapper om = new ObjectMapper();

    private Long subj1002;
    private Long subj4001;

    @BeforeEach
    void seed() {
        EnterpriseContextHolder.set(1L);
        jdbcTemplate.update("""
                INSERT INTO t_period(period_code, year, month, start_date, end_date, status, enterprise_id, opening_status)
                VALUES (?, 2026, 12, '2026-12-01', '2026-12-31', 'open', 1, 'entered')
                """, PERIOD);
        subj1002 = subjectId("1002", "银行存款", "debit");
        subj4001 = subjectId("4001", "实收资本", "credit");
        upsertOpening(subj1002, "100000");
        upsertOpening(subj4001, "100000");

        Map<String, Object> type = jdbcTemplate.queryForMap(
                "SELECT id FROM t_voucher_type WHERE enterprise_id = 1 ORDER BY id LIMIT 1");
        Long typeId = ((Number) type.get("id")).longValue();
        // 收现销售（简化场景，不含结转）：借 1002 10,000 / 贷 4001… 不，收现对应收入段会拉进 6xx；
        // 用权益侧对称分录即可：借 1002 10,000 / 贷 4001 10,000（增资收款），期末 1002=110,000
        postVoucher(typeId, "PROBE-X1", new Object[][] {
                {subj1002, true, "10000"}, {subj4001, false, "10000"}});
    }

    @AfterEach
    void clearCtx() {
        EnterpriseContextHolder.clear();
    }

    @Test
    @DisplayName("导出件三断言：结构齐 + 期末余额数值勾稽 + 与在线 JSON API 同源一致")
    void exportStructureValuesAndParity() throws Exception {
        var result = mockMvc.perform(get("/api/base/report/v1/reports/subject-balance/export")
                        .param("period", PERIOD)
                        .with(SecurityMockMvcRequestPostProcessors.user("probe")
                                .authorities(() -> "report:view")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();

        byte[] xlsx = result.getResponse().getContentAsByteArray();
        assertTrue(xlsx.length > 1000, "导出件必须非空 xlsx（PK 头 + 数据），实际 " + xlsx.length + " 字节");
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition, "必须带 Content-Disposition（文件名含期间）");
        assertTrue(java.net.URLDecoder.decode(disposition, StandardCharsets.UTF_8).contains(PERIOD),
                "文件名必须含期间：" + disposition);

        // hutool 读回（与生产写出同库）
        List<List<Object>> rows;
        try (var reader = ExcelUtil.getReader(new ByteArrayInputStream(xlsx))) {
            rows = reader.read();
        }
        // 结构：标题行 + 表头行（科目编码…期末余额）
        boolean headerOk = rows.stream().anyMatch(r -> r.size() >= 7
                && "科目编码".equals(String.valueOf(r.get(0)))
                && "期末余额".equals(String.valueOf(r.get(r.size() - 1))));
        assertTrue(headerOk, "表头行缺失（应为 科目编码/科目名称/余额方向/期初余额/本期借方/本期贷方/期末余额）");
        assertTrue(rows.stream().anyMatch(r -> !r.isEmpty() && "科目余额表".equals(String.valueOf(r.get(0)))),
                "P89-D 标题行缺失");

        // 数值勾稽：造数期末 1002 = 100,000 + 10,000 = 110,000；4001 = 110,000（贷）
        assertTrue(hasRow(rows, "1002", "110000"), "导出件必须含 1002 期末=110,000 行");
        assertTrue(hasRow(rows, "4001", "110000"), "导出件必须含 4001 期末=110,000 行");

        // 导出 = 在线同源：JSON API 同科目期末必须一致（ent1 行按 subject_id）
        JsonNode data = apiJson().path("data");
        BigDecimal jsonEnd = null;
        for (JsonNode row : data) {
            if (row.path("id").asLong() == subj1002) {
                jsonEnd = new BigDecimal(row.path("end_balance").asText());
            }
        }
        assertNotNull(jsonEnd, "在线 API 必须有 1002 行");
        BigDecimal excelEnd = rows.stream()
                .filter(r -> r.size() >= 7 && "1002".equals(String.valueOf(r.get(0))))
                .map(r -> new BigDecimal(String.valueOf(r.get(6))))
                .max(BigDecimal::compareTo).orElseThrow(); // 撞行口径：ent1 行是最大值
        assertTrue(excelEnd.subtract(jsonEnd).abs().compareTo(TOL) < 0,
                "导出件与在线 API 分叉：Excel 1002 期末=" + excelEnd + "，JSON=" + jsonEnd);
    }

    private boolean hasRow(List<List<Object>> rows, String code, String endBalance) {
        return rows.stream().anyMatch(r -> r.size() >= 7
                && code.equals(String.valueOf(r.get(0)))
                && new BigDecimal(String.valueOf(r.get(6))).subtract(new BigDecimal(endBalance)).abs()
                        .compareTo(TOL) < 0);
    }

    private JsonNode apiJson() throws Exception {
        var r = mockMvc.perform(get("/api/base/report/v1/reports/subject-balance")
                        .param("period", PERIOD)
                        .with(SecurityMockMvcRequestPostProcessors.user("probe")
                                .authorities(() -> "report:view")))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ── 造数（切片二同款家法）──

    private Long subjectId(String code, String name, String direction) {
        var ids = jdbcTemplate.queryForList(
                "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = 1 AND deleted = 0",
                Long.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbcTemplate.update("INSERT INTO t_subject(code, name, direction, is_leaf, enterprise_id, level) VALUES (?, ?, ?, TRUE, 1, 1)",
                code, name, direction);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = 1 AND deleted = 0 ORDER BY id DESC LIMIT 1",
                Long.class, code);
    }

    private void upsertOpening(Long subjectId, String balance) {
        jdbcTemplate.update("DELETE FROM t_subject_balance WHERE subject_id = ? AND period = ?", subjectId, PERIOD);
        jdbcTemplate.update(
                "INSERT INTO t_subject_balance(subject_id, year, period, begin_balance, end_balance) VALUES (?, '2026', ?, ?, ?)",
                subjectId, PERIOD, new BigDecimal(balance), new BigDecimal(balance));
    }

    private void postVoucher(Long typeId, String no, Object[][] lines) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(no);
        v.setPeriod(PERIOD);
        v.setVoucherTypeId(typeId);
        v.setStatus("AUDITED");
        voucherMapper.insert(v);
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] line : lines) {
            VoucherEntryEntity e = new VoucherEntryEntity();
            e.setVoucherId(v.getId());
            e.setSubjectId((Long) line[0]);
            BigDecimal amount = new BigDecimal((String) line[2]);
            if ((Boolean) line[1]) {
                e.setDebit(amount);
            } else {
                e.setCredit(amount);
            }
            voucherEntryMapper.insert(e);
            total = total.add(amount);
        }
        v.setTotalDebit(total);
        v.setTotalCredit(total);
        voucherMapper.updateById(v);
        voucherService.post(v.getId(), 1L);
    }
}
