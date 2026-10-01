package com.huicai.sme.tax.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.mapper.TaxDeclarationMapper;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 纳税申报创建真库测试（REQ-2026-129 / P102 DTO 隔离带出的 P0，同 createOutput 一类）
 *
 * <p><b>缺陷</b>：{@code TaxServiceImpl.createDeclaration()} 与修复前的
 * {@code createOutput()} 是<b>同一个写法</b> ——
 * {@code if (entity.getStatus() == null) entity.setStatus("DRAFT")}，
 * 只在 null 时兜底。端点又直接接 {@code @RequestBody TaxDeclarationEntity}（违反铁律 #13），
 * Entity 的 {@code status} 可被客户端任意指定。
 *
 * <p>{@code chk_declaration_status} 允许 {@code DRAFT / SUBMITTED / APPROVED / REJECTED}，
 * 故客户端 POST {@code status=APPROVED} 可<b>一步跳过</b>
 * {@code submitDeclaration()} + {@code approveDeclaration()} 两次人工动作
 * —— 直接违反铁律 #1（人是唯一审核主体）。比 createOutput 更严重：
 * 那边至少要凑齐 8 个字段，这边连金额/期间都不校验。
 *
 * <p>另：该方法<b>零字段校验</b>，完全依赖 DB 的 NOT NULL 兜底
 * （而 DB 抛的是 23502 数据完整性异常，不是 400 业务错误）。
 */
@DisplayName("P105 纳税申报创建：状态不可由客户端指定")
class TaxDeclarationCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private TaxService taxService;

    @Autowired
    private TaxDeclarationMapper declarationMapper;

    private TaxDeclarationEntity draft() {
        TaxDeclarationEntity e = new TaxDeclarationEntity();
        e.setDeclarationNo("P105TD" + (System.nanoTime() % 1000000));
        e.setPeriod("209912");
        e.setTaxType("VAT");
        e.setDeclaredDate(LocalDate.now());
        e.setPayableAmount(new BigDecimal("1000.00"));
        e.setEnterpriseId(1L);
        return e;
    }

    @Test
    @DisplayName("不传 status 时默认 DRAFT")
    void defaultStatusIsDraft() {
        TaxDeclarationEntity e = draft();
        e.setStatus(null);

        TaxDeclarationEntity saved = taxService.createDeclaration(e);

        assertNotNull(saved.getId(), "未落库");
        assertEquals("DRAFT", declarationMapper.selectById(saved.getId()).getStatus(),
                "默认状态必须是 DRAFT（chk_declaration_status 允许且为合法起点）");
    }

    @Test
    @DisplayName("负向：客户端传 status=APPROVED 必须被忽略，强制 DRAFT（铁律 #1 人审）")
    void clientSuppliedStatusIsIgnored() {
        TaxDeclarationEntity e = draft();
        e.setStatus("APPROVED");   // 越权尝试：一步跳过 submit + approve 两次人工动作

        TaxDeclarationEntity saved = taxService.createDeclaration(e);

        TaxDeclarationEntity db = declarationMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals("DRAFT", db.getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可直接创建「已审核」申报，"
                        + "绕过 DRAFT→SUBMITTED→APPROVED 的人工审核链（铁律 #1）");
    }

    @Test
    @DisplayName("负向：客户端传 status=REJECTED 也必须被忽略")
    void clientSuppliedRejectedIsIgnored() {
        TaxDeclarationEntity e = draft();
        e.setStatus("REJECTED");

        TaxDeclarationEntity saved = taxService.createDeclaration(e);

        assertEquals("DRAFT", declarationMapper.selectById(saved.getId()).getStatus(),
                "REJECTED 是终态，更不允许由客户端直接指定");
    }
}