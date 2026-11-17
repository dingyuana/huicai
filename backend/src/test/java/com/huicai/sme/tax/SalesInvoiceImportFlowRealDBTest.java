package com.huicai.sme.tax;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.service.impl.SalesInvoiceImportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SPEC-P153 发票导入 500：🔴 <b>导入流程集成锁</b> —— 非默认企业（2）走完
 * 「Excel 上传 → preview 查重 → confirm 落库」全链路（真实 Spring bean + 真 PG）。
 *
 * <p>与两层守卫的分工：</p>
 * <ul>
 *   <li>{@code TenantInsertEnterpriseFillRealDBTest}：实体 fill 标记（单表 INSERT 面）；</li>
 *   <li>本用例：<b>流程面</b> —— 预览查重要能真读到已存在发票（无事务时 SELECT
 *       被应用层租户注入/RLS 谓词滤空 ⇒ existing 恒 0 的静默失效），confirm 的
 *       行级 REQUIRES_NEW（rowTx）在真代理下提交、租户列落上下文企业；</li>
 *   <li>RLS「WITH CHECK 拒绝」这一面 L2 <b>测不到</b>：Testcontainers 的 owner(test)
 *       是超级用户、原生豁免 RLS（V106 的 FORCE 也不压过 rolsuper）。该面由生产
 *       实机验证（huicai_app 非超级用户角色重建后实测导入 200）。</li>
 * </ul>
 *
 * <p>为什么断言在企业 2：企业 1 = 列 DEFAULT，漏 fill 也「碰巧对」，无鉴别力。</p>
 */
@DisplayName("SPEC-P153：销售发票导入流程（企业 2 · 真 bean · 真 PG · 行级 REQUIRES_NEW 提交）")
class SalesInvoiceImportFlowRealDBTest extends AbstractMapperTest {

    private static final Long ENT = 2L;
    private static final String NO_A = "P153E2E001";
    private static final String NO_B = "P153E2E002";

    @Autowired
    private SalesInvoiceImportService importService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("preview 查重读得到 + confirm 全行落库且 enterprise_id=上下文企业")
    void importFlowForNonDefaultEnterprise() throws Exception {
        useEnterprise(ENT);
        // 模拟 HTTP 语义：每个请求 = 独立事务（Controller 端点的 @Transactional 代理）。
        // 🔴 两个必须独立事务的理由（首跑被第二条绊倒）：
        // ① GUC 依赖活动事务（TenantGucSetter 无事务直接 return）；
        // ② MyBatis 一级缓存按 SqlSession 分桶，测试 @Transactional 让全流程共享
        //    一个 SqlSession ⇒ 二次 preview 的同参数 findExisting 直接命中首查
        //    的空缓存 ⇒ 假阴性。生产中每个 HTTP 请求都是新 SqlSession，无此问题。
        org.springframework.transaction.support.TransactionTemplate txOps =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txOps.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            MockMultipartFile file = new MockMultipartFile("file", "inv.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    buildInvoiceXlsx());

            Map<String, Object> preview = txOps.execute(st -> importService.previewInvoices(file));
            assertNotNull(preview);
            String batchId = String.valueOf(preview.get("batchId"));
            assertNotNull(batchId, "preview 未返回 batchId");
            assertEquals(2, ((Number) preview.get("total")).intValue());

            Map<String, Object> result = txOps.execute(st -> importService.confirmImport(batchId));
            assertNotNull(result);
            assertEquals(2, ((Number) result.get("success")).intValue(),
                    "两行应全部成功；errors=" + result.get("errors"));

            Integer owned = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_output_invoice WHERE enterprise_id = ? AND deleted = 0"
                            + " AND invoice_no IN (?, ?)", Integer.class, ENT, NO_A, NO_B);
            assertEquals(2, owned,
                    "发票未落企业 2 账套 ⇒ 租户列漏填被 DEFAULT 兜底，或行级事务未提交。落库真值："
                            + jdbcTemplate.queryForList(
                                    "SELECT invoice_no || '@ent' || enterprise_id || '/del' || deleted"
                                            + " FROM t_output_invoice WHERE invoice_no IN (?, ?)",
                                    String.class, NO_A, NO_B));

            // 二遍导入（同批发票号）：预览必须把它们标为已存在（查重读面守卫）。
            // 修复前 findExisting 的 SELECT 在无事务/租户谓词失配时恒空 ⇒ 永不判重。
            @SuppressWarnings("unchecked")
            List<String> existingList = (List<String>) txOps
                    .execute(st -> importService.previewInvoices(file)).get("existingList");
            assertNotNull(existingList);
            org.junit.jupiter.api.Assertions.assertTrue(
                    existingList.contains(NO_A) && existingList.contains(NO_B),
                    "查重读不到刚落库的发票 ⇒ existing 恒 0 的静默失效未修复：" + existingList);
        } finally {
            // rowTx 是 REQUIRES_NEW ⇒ 行数据已提交，不随测试事务回滚，必须手清
            jdbcTemplate.update("DELETE FROM t_output_invoice WHERE invoice_no IN (?, ?)", NO_A, NO_B);
            jdbcTemplate.update("DELETE FROM t_customer WHERE name LIKE 'P153端到端客户%' AND enterprise_id = ?", ENT);
        }
    }

    private byte[] buildInvoiceXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("inv");
            Row header = sheet.createRow(0);
            String[] cols = {"开票日期", "发票号码", "购买方名称", "购方识别号",
                    "货物名称", "金额", "税额", "价税合计"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            writeRow(sheet, 1, "2024-07-10", NO_A, "P153端到端客户A", "91370200P153E2EA",
                    "软件服务费", "1000", "60", "1060");
            writeRow(sheet, 2, "2024-07-11", NO_B, "P153端到端客户B", "91370200P153E2EB",
                    "技术服务费", "2000", "120", "2120");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeRow(Sheet sheet, int rowIdx, String... vals) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < vals.length; i++) {
            row.createCell(i).setCellValue(vals[i]);
        }
    }
}
