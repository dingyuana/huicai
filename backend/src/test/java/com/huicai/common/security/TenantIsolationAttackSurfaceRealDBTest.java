package com.huicai.common.security;

import com.huicai.base.storage.entity.AttachmentEntity;
import com.huicai.base.storage.mapper.AttachmentMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.salary.entity.SalaryBillEntity;
import com.huicai.sme.salary.entity.SalaryItemEntity;
import com.huicai.sme.salary.mapper.SalaryBillMapper;
import com.huicai.sme.salary.mapper.SalaryItemMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-P154 切片一：多租户隔离攻击面负向探针（v4.0 §8 映射）。
 *
 * <p><b>隔离架构</b>：慧财数据层隔离 = RLS（enterprise_id = GUC，Postgres 行级安全兜底）
 * + GUC 逐 SQL 设置；功能层隔离 = PermissionEnforcementInterceptor（enforcing，307 端点）。
 * 本类把 v4.0 的攻击面矩阵落到真库 + 真 Controller 链（MockMvc 走权限拦截器 →
 * GUC 拦截器 → RLS 谓词全链），每例锁两断言：<b>操作被拒 + 受害行数据不变</b>。</p>
 *
 * <p><b>🔴 已登记差距（八类禁自动修复：审计日志策略，须人工裁定）</b>：权限拦截器 403
 * 在 preHandle 阶段直接写 response，@Auditable 切面不触发 —— <b>越权尝试不留 DB 审计，
 * 只有 log.warn</b>。v4.0 红线要求「审计日志已记录该尝试（主体/租户/资源/动作）」。
 * 修法建议（人工审核队列）：拦截器 403 分支落 module=AUTH/operation=ACCESS_DENIED 审计行，
 * 复用 P152 真列（request_params 记 method+uri，operator 取 SecurityContext）。</p>
 *
 * <p><b>探针口径（与生产同构）</b>：攻击者持<b>充足功能权限</b>（salary:save 等），
 * 只缺「数据归属」——功能层全放行、数据层 RLS 必须兜住。这比「无权限 403」更硬：
 * 它证明隔离不依赖功能权限配置的正确性。</p>
 */
@DisplayName("SPEC-P154：多租户攻击面 —— BOLA 读/写/删 + 附件签名 URL + 功能越权 + 列表不串户")
@AutoConfigureMockMvc
class TenantIsolationAttackSurfaceRealDBTest extends AbstractMapperTest {

    private static final long VICTIM_ENT = 1L;
    private static final long ATTACKER_ENT = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SalaryBillMapper billMapper;
    @Autowired private SalaryItemMapper salaryItemMapper;
    @Autowired private AttachmentMapper attachmentMapper;

    // ── 造数（受害方 = ent1）──

    private Long makeVictimBill() {
        SalaryItemEntity item = new SalaryItemEntity();
        item.setItemCode("ATK-" + System.nanoTime());
        item.setItemName("探针项目");
        item.setCategory("EARN");
        salaryItemMapper.insert(item);

        SalaryBillEntity bill = new SalaryBillEntity();
        bill.setPeriod("202612");
        bill.setStatus("DRAFT");
        billMapper.insert(bill);
        return bill.getId();
    }

    private Long makeVictimAttachment() {
        AttachmentEntity att = new AttachmentEntity();
        att.setBizType("salary_bill");
        att.setBizId(0L);
        att.setFileName("atk/" + UUID.randomUUID() + ".bin");
        att.setOriginalName("victim-secret.bin");
        att.setFilePath("huicai-files/atk/" + UUID.randomUUID() + ".bin");
        att.setBucketName("huicai-files");
        att.setFileSize(1L);
        att.setContentType("application/octet-stream");
        attachmentMapper.insert(att);
        return att.getId();
    }

    // ── 自证前提（防探针恒绿：RLS 表集若被撤策略，本类全部断言失去意义）──

    @Test
    @DisplayName("自证：t_salary_bill / t_attachment 确在 RLS 策略内（探针有效性前提）")
    void rlsPremiseHolds() {
        for (String table : new String[] {"t_salary_bill", "t_attachment"}) {
            Integer policies = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_policies WHERE tablename = ?", Integer.class, table);
            assertEquals(1, policies, table + " 必须挂 enterprise_policy —— 策略被撤则探针全部失效");
        }
    }

    // ── 攻击面 1：BOLA 读（嵌套资源）──

    @Test
    @DisplayName("攻击面1 BOLA读：ent2 会话读 ent1 工资单详情 → 拒 + 行不变")
    void bolaReadDenied() throws Exception {
        Long billId = makeVictimBill();
        EnterpriseContextHolder.set(ATTACKER_ENT);
        try {
            // R 信封口径：BusinessException = HTTP 200 + body code 400（V1.105 实机家法）
            mockMvc.perform(get("/api/sme/salary/v1/salary-bills/" + billId)
                            .with(SecurityMockMvcRequestPostProcessors.user("attacker")
                                    .authorities(() -> "salary:list", () -> "salary:save")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.greaterThanOrEqualTo(400)));
        } finally {
            EnterpriseContextHolder.clear();
        }
        assertBillStatus(billId, "DRAFT");
    }

    // ── 攻击面 2：BOLA 写（跨租户状态推进）──

    @Test
    @DisplayName("攻击面2 BOLA写：ent2 会话确认 ent1 工资单 → 拒 + 状态不变 + 不产生凭证")
    void bolaWriteDenied() throws Exception {
        Long billId = makeVictimBill();
        EnterpriseContextHolder.set(ATTACKER_ENT);
        try {
            mockMvc.perform(post("/api/sme/salary/v1/salary-bills/" + billId + "/confirm")
                            .with(SecurityMockMvcRequestPostProcessors.user("attacker")
                                    .authorities(() -> "salary:confirm")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.greaterThanOrEqualTo(400)));
        } finally {
            EnterpriseContextHolder.clear();
        }
        assertBillStatus(billId, "DRAFT");
        Integer salc = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher WHERE voucher_no LIKE 'SALC-%' AND enterprise_id = 1",
                Integer.class);
        assertEquals(0, salc, "被拒的确认不得产生任何计提凭证");
    }

    // ── 攻击面 3：文件存储 —— 跨租户换签名下载 URL ──

    @Test
    @DisplayName("攻击面3 附件：ent2 会话换 ent1 附件签名 URL → 拒（RLS 滤空）+ 行不变")
    void bolaPresignDenied() throws Exception {
        Long attId = makeVictimAttachment();
        EnterpriseContextHolder.set(ATTACKER_ENT);
        try {
            mockMvc.perform(get("/api/v1/attachments/" + attId + "/url")
                            .with(SecurityMockMvcRequestPostProcessors.user("attacker")
                                    .authorities(() -> "file:attachment:manage")))
                    .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.greaterThanOrEqualTo(400)));
        } finally {
            EnterpriseContextHolder.clear();
        }
        Integer alive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_attachment WHERE id = ? AND deleted = 0", Integer.class, attId);
        assertEquals(1, alive, "受害附件行必须原样存在");
    }

    // ── 攻击面 4：跨租户删除 ──

    @Test
    @DisplayName("攻击面4 BOLA删：ent2 会话删 ent1 附件 → 拒 + 行不变")
    void bolaDeleteDenied() throws Exception {
        Long attId = makeVictimAttachment();
        EnterpriseContextHolder.set(ATTACKER_ENT);
        try {
            mockMvc.perform(delete("/api/v1/attachments/" + attId)
                            .with(SecurityMockMvcRequestPostProcessors.user("attacker")
                                    .authorities(() -> "file:attachment:manage")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.greaterThanOrEqualTo(400)));
        } finally {
            EnterpriseContextHolder.clear();
        }
        Integer alive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_attachment WHERE id = ? AND deleted = 0", Integer.class, attId);
        assertEquals(1, alive, "被拒的删除不得软删受害行");
    }

    // ── 攻击面 5：功能越权（缺权限 403，enforcing 模式）──

    @Test
    @DisplayName("攻击面5 功能越权：无 salary:save 者新建工资单 → 403 + 数据不变")
    void missingPermissionDenied403() throws Exception {
        EnterpriseContextHolder.set(ATTACKER_ENT);
        try {
            // body 必须合法（items 非空）：@Valid 校验先于 @PreAuthorize 执行，
            // 空 items 会以 400「工资明细不能为空」抢在权限拒绝之前返回（信息面小泄露，另登记）
            mockMvc.perform(post("/api/sme/salary/v1/salary-bills")
                            .with(SecurityMockMvcRequestPostProcessors.user("cashier")
                                    .authorities(() -> "bank:journal:list"))
                            .contentType("application/json")
                            .content("{\"period\":\"202612\",\"items\":[{\"employeeId\":1,\"itemId\":1,\"amount\":1}]}"))
                    .andExpect(status().isForbidden());
        } finally {
            EnterpriseContextHolder.clear();
        }
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_salary_bill WHERE period = '202612'", Integer.class);
        assertEquals(0, cnt, "403 请求不得留下任何工资单行");
    }

    // ── 攻击面 6：列表不串户 ──

    @Test
    @DisplayName("攻击面6 列表不串户：ent2 会话的工资单列表 ID 集合 ⊆ 本租户")
    void listScopedToTenant() throws Exception {
        makeVictimBill(); // ent1 的行
        EnterpriseContextHolder.set(ATTACKER_ENT);
        try {
            mockMvc.perform(get("/api/sme/salary/v1/salary-bills")
                            .with(SecurityMockMvcRequestPostProcessors.user("attacker")
                                    .authorities(() -> "salary:list")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.enterpriseId != 2)]").isEmpty());
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    private void assertBillStatus(Long billId, String expected) {
        // jdbcTemplate 绕 MyBatis 一级缓存（@Transactional 同 SqlSession 假阴性，V1.104 教训）
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_salary_bill WHERE id = ?", String.class, billId);
        assertTrue(expected.equals(status), "受害工资单状态必须保持 " + expected + "，实际 " + status);
    }
}
