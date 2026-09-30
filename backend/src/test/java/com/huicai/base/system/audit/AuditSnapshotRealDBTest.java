package com.huicai.base.system.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.mapper.AuditLogMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P103 审计追踪真实落地 —— 快照必须真的落库（REQ-2026-130）
 *
 * <p>缺陷背景（实测）：开发库 {@code t_audit_log} 有 <b>189 行</b>，而
 * {@code before_data} / {@code after_data} / {@code entity_type} / {@code operator_id}
 * 的<b>非空计数全为 0</b> —— 审计表「有行无快照」，铁律 #5 名存实亡。
 * 根因：{@code AuditLogEntity} 的快照字段被标 {@code @TableField(exist = false)}
 * （幽灵字段，完全不参与 SQL），而真实列是 {@code before_data}/{@code after_data}；
 * 且切面<b>只拦 insert 与 deleteById，{@code updateById} 完全无审计</b>，
 * 所以「谁把什么改成什么」根本查不到。
 */
@DisplayName("P103 审计快照真实落库")
class AuditSnapshotRealDBTest extends AbstractMapperTest {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private VoucherEntity newVoucher(String no) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(no);
        v.setPeriod("202609");
        // t_voucher.voucher_type_id NOT NULL；种子里 JZ=id 1 在 enterprise 1，
        // 与基类默认上下文一致，直接复用，不另造主数据（AGENTS §4.3 第 11 条：
        // 夹具凡插主数据一律 find-or-insert，否则每补一次种子就要炸一批测试）
        v.setVoucherTypeId(resolveVoucherTypeId());
        v.setStatus("DRAFT");
        v.setSource("MANUAL");
        v.setSummary("审计快照测试");
        v.setTotalDebit(new java.math.BigDecimal("100.00"));
        v.setTotalCredit(new java.math.BigDecimal("100.00"));
        return v;
    }

    private Long resolveVoucherTypeId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_type WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
    }

    private List<AuditLogEntity> logsFor(Long entityId) {
        return auditLogMapper.selectList(Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(AuditLogEntity::getEntityId, entityId)
                .orderByAsc(AuditLogEntity::getId));
    }

    /**
     * 取指定动作的审计行。
     *
     * <p>审计写入现为<b>随业务事务同步</b>（{@code AuditLogServiceImpl#save}
     * 已去掉 {@code @Async} + {@code REQUIRES_NEW}），故这里直接查即可。
     * 刻意<b>不加轮询</b>：一旦有人重新引入异步写入，同步断言会立刻变红，
     * 从而暴露语义回退，而不是被轮询悄悄掩盖。
     */
    private AuditLogEntity awaitAudit(Long entityId, String operation) {
        List<AuditLogEntity> logs = logsFor(entityId);
        return logs.stream().filter(l -> operation.equals(l.getOperation()))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("AT-103-1 插入必须留下 after_data 快照且是合法 JSON")
    void insertWritesAfterData() throws Exception {
        VoucherEntity v = newVoucher("P103.AUD.INS");
        voucherMapper.insert(v);

        AuditLogEntity create = awaitAudit(v.getId(), "CREATE");
        assertNotNull(create, "未记录 CREATE 审计（entity_id=" + v.getId() + "）");
        assertNotNull(create.getAfterData(), "after_data 为空 ⇒ 审计表有行无快照");
        assertNotNull(objectMapper.readTree(create.getAfterData()),
                "after_data 不是合法 JSON：" + create.getAfterData());
    }

    @Test
    @DisplayName("AT-103-2 审计行必须带 entity_type / entity_id（否则 idx_audit_log_entity 索引永久失效）")
    void auditRowCarriesEntityCoordinates() {
        VoucherEntity v = newVoucher("P103.AUD.COORD");
        voucherMapper.insert(v);

        AuditLogEntity create = awaitAudit(v.getId(), "CREATE");
        assertNotNull(create, "未记录 CREATE 审计");
        assertEquals(v.getId(), create.getEntityId(), "entity_id 未写入");
        assertNotNull(create.getEntityType(), "entity_type 未写入 ⇒ idx_audit_log_entity 索引形同虚设");
    }

    @Test
    @DisplayName("AT-103-3 🔴 updateById 必须留痕，且 before/after 快照均非空且不同")
    void updateByIdIsAuditedWithBothSnapshots() throws Exception {
        VoucherEntity v = newVoucher("P103.AUD.UPD");
        voucherMapper.insert(v);

        v.setSummary("改过的摘要-审计快照测试");
        voucherMapper.updateById(v);

        AuditLogEntity update = awaitAudit(v.getId(), "UPDATE");
        assertNotNull(update, "updateById 完全无审计 —— 切面原只拦 insert/deleteById，"
                + "「谁把什么改成什么」查不到");

        assertNotNull(update.getBeforeData(), "UPDATE 缺 before_data");
        assertNotNull(update.getAfterData(), "UPDATE 缺 after_data");
        assertNotEquals(update.getBeforeData(), update.getAfterData(),
                "before/after 快照相同 ⇒ 变更内容根本没被捕捉");
        assertNotNull(objectMapper.readTree(update.getBeforeData()));
        assertNotNull(objectMapper.readTree(update.getAfterData()));
    }

    @Test
    @DisplayName("AT-103-4 反证：before/after 快照确含变更字段（防止只是两份相同 JSON）")
    void snapshotsContainChangedField() throws Exception {
        VoucherEntity v = newVoucher("P103.AUD.CMP");
        voucherMapper.insert(v);
        v.setSummary("唯一新摘要-XYZ");
        voucherMapper.updateById(v);

        AuditLogEntity update = awaitAudit(v.getId(), "UPDATE");
        assertNotNull(update, "未记录 UPDATE 审计");

        String after = update.getAfterData();
        assertTrue(after.contains("唯一新摘要-XYZ"),
                "after_data 未包含变更后的值：" + after);
        String before = update.getBeforeData();
        assertTrue(!before.contains("唯一新摘要-XYZ"),
                "before_data 不应包含变更后的值（说明取的不是旧数据）：" + before);
    }

    @Test
    @DisplayName("AT-103-5 快照必须是 JSON 对象而非手工拼串（P103 缺陷 4）")
    void snapshotIsJsonObjectNotConcatenatedString() throws Exception {
        VoucherEntity v = newVoucher("P103.AUD.JSON");
        voucherMapper.insert(v);

        AuditLogEntity create = awaitAudit(v.getId(), "CREATE");
        assertNotNull(create, "未记录 CREATE 审计");
        var node = objectMapper.readTree(create.getAfterData());
        assertTrue(node.isObject(),
                "快照应为 JSON 对象，实得：" + create.getAfterData());
        Map<String, Object> asMap = objectMapper.convertValue(node, Map.class);
        assertTrue(asMap.containsKey("voucherNo") || !asMap.isEmpty(),
                "快照 JSON 为空对象，等于没记内容");
    }
}
