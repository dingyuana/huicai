package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P132 真实 DB 测试：凭证编辑的<b>归属校验</b>（只有制单人能改自己的草稿）。
 *
 * <p>🔴 <b>为什么这条必须存在</b>：{@code VoucherServiceImpl#update} 此前只校验
 * {@code status=DRAFT} 与期间开启，<b>不校验操作人是否为制单人</b> ——
 * 任何有访问权的人都能改<b>别人的</b>草稿凭证，且全程无报错。
 * P130 修的是「进不去编辑页」（路由 403），本条修的是「进去之后能改谁的」；
 * 缺任一环，内控都形同虚设。
 */
@DisplayName("SPEC-P132：凭证编辑归属校验（只有制单人能改自己的草稿）")
class VoucherEditOwnershipRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherServiceImpl voucherService;

    @Autowired
    private VoucherMapper voucherMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private VoucherEntity newDraft(Long createdBy, String tag) {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        // ⚠️ 必须先建会计期间：update 内有 assertPeriodOpen，
        // 期间不存在会先抛「会计期间不存在」而根本走不到归属校验（报错指错方向）。
        jdbcTemplate.update(
                "INSERT INTO t_period (year, month, period_code, start_date, end_date, status, "
                        + "enterprise_id, opening_status, created_at, updated_at, deleted) "
                        + "VALUES (2098, 1, '209801', '2098-01-01', '2098-01-31', 'open', ?, 'none', NOW(), NOW(), 0) "
                        + "ON CONFLICT (period_code, enterprise_id) DO NOTHING",
                DEFAULT_ENTERPRISE_ID);
        VoucherEntity e = new VoucherEntity();
        e.setPeriod("209801");
        e.setVoucherNo("TEST-P132-" + tag);
        e.setVoucherTypeId(1L);
        e.setStatus("DRAFT");
        e.setSource("MANUAL");
        e.setSummary("P132 测试凭证-" + tag);
        e.setTotalDebit(new java.math.BigDecimal("100.00"));
        e.setTotalCredit(new java.math.BigDecimal("100.00"));
        e.setCreatedBy(createdBy);
        voucherMapper.insert(e);
        return e;
    }

    private VoucherCreateDTO dto(Long id) {
        VoucherCreateDTO d = new VoucherCreateDTO();
        d.setId(id);
        d.setPeriod("209801");
        d.setSummary("P132 被改过的摘要");
        VoucherCreateDTO.EntryDTO e1 = new VoucherCreateDTO.EntryDTO();
        e1.setSubjectId(1L);
        e1.setDebit(new java.math.BigDecimal("100.00"));
        e1.setCredit(java.math.BigDecimal.ZERO);
        e1.setSummary("借");
        VoucherCreateDTO.EntryDTO e2 = new VoucherCreateDTO.EntryDTO();
        e2.setSubjectId(1L);
        e2.setDebit(java.math.BigDecimal.ZERO);
        e2.setCredit(new java.math.BigDecimal("100.00"));
        e2.setSummary("贷");
        d.setEntries(java.util.List.of(e1, e2));
        return d;
    }

    @Test
    @DisplayName("AT-132-1 制单人可以改自己的草稿凭证（正向）")
    void makerCanEditOwnDraft() {
        VoucherEntity v = newDraft(9001L, "OWN");
        assertDoesNotThrow(() -> voucherService.update(dto(v.getId()), 9001L),
                "制单人改自己的草稿必须被允许 —— 否则会计无法完成制单职责");

        String summary = voucherMapper.selectById(v.getId()).getSummary();
        assertEquals("P132 被改过的摘要", summary, "摘要未落库");
    }

    @Test
    @DisplayName("🔴 AT-132-2 别人不能改我的草稿凭证（这是修复前缺失的那条）")
    void nonMakerCannotEdit() {
        VoucherEntity v = newDraft(9001L, "OTHER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherService.update(dto(v.getId()), 9002L),
                "非制单人竟能修改他人草稿凭证 —— P132 要修的正是这个");
        assertTrue(ex.getMessage().contains("只能修改本人录入的凭证"),
                "异常文案须说明是归属问题；实际: " + ex.getMessage());

        // 🔴 负向断言：被拒后**数据不得变化**（只断言抛异常不够，
        // 「先 update 再校验」的实现同样会抛异常但数据已改坏，§4.5 第 43 条）
        assertEquals("P132 测试凭证-OTHER", voucherMapper.selectById(v.getId()).getSummary(),
                "越权修改虽抛异常，但摘要已被改动 ⇒ 拒绝只是响应层面的装饰");
    }

    @Test
    @DisplayName("AT-132-3 created_by 为 NULL 的凭证放行（无法判断归属时不误伤）")
    void nullCreatedByIsAllowed() {
        VoucherEntity v = newDraft(null, "NULLBY");
        assertDoesNotThrow(() -> voucherService.update(dto(v.getId()), 9003L),
                "created_by 为 NULL（自动生成/历史数据）时无法判断归属，应放行而非误伤");
    }

    @Test
    @DisplayName("AT-132-4 非草稿状态仍按原规则拒绝（归属校验不得放宽状态机）")
    void nonDraftStillRejected() {
        VoucherEntity v = newDraft(9001L, "SUBMITTED");
        voucherMapper.updateById(new VoucherEntity() {{
            setId(v.getId());
            setStatus("SUBMITTED");
        }});

        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherService.update(dto(v.getId()), 9001L));
        assertTrue(ex.getMessage().contains("仅草稿状态"),
                "非草稿应报「仅草稿状态可修改」；实际: " + ex.getMessage());
    }
}