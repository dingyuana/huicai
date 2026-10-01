package com.huicai.sme.asset.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.mapper.AssetDisposalMapper;
import com.huicai.sme.asset.service.AssetDisposalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 资产处置创建真库测试（REQ-2026-129 / P102 DTO 隔离 + 全仓指纹扫描）
 *
 * <p><b>缺陷来源</b>：销项发票、纳税申报两处 P0 修完后，用
 * 「{@code if (x.getStatus() == null) setStatus(...)}」作指纹全仓扫描，
 * 本类是命中的第 3 处。
 *
 * <p>{@code AssetDisposalController#create} 直接接 {@code @RequestBody AssetDisposalEntity}
 * （违反铁律 #13），而 {@code AssetDisposalServiceImpl#create} 只在 null 时兜底 DRAFT。
 * {@code chk_disposal_status} 允许 {@code DRAFT / APPROVED / VOUCHERED}，
 * 故 POST {@code status=APPROVED} 可**一步跳过** {@code approve()} 这次人工动作 ——
 * 违反铁律 #1（人是唯一审核主体）。
 *
 * <p>注：AGENTS §4.2 第 14 条已记 {@code chk_disposal_status} 允许集为
 * DRAFT/APPROVED/VOUCHERED（<b>没有</b> PENDING_APPROVAL），本次实测一致。
 */
@DisplayName("P105 资产处置创建：状态不可由客户端指定")
class AssetDisposalCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private AssetDisposalService disposalService;

    @Autowired
    private AssetDisposalMapper disposalMapper;

        /** 造一张「在用」资产卡：create() 会按 assetId 查卡并要求 status=IN_USE。
     *  ⚠️ t_asset_category 在库内**完全没有种子数据**（count=0），而 t_asset_card.category_id
     *  有外键 fk_asset_category ⇒ 夹具必须先自造分类，不能假设 category_id=1 可用。 */
    private Long inUseCard() {
        Long categoryId = jdbcTemplate.queryForObject(
                "INSERT INTO t_asset_category (code, name, depreciation_method, enterprise_id, deleted) "
                        + "VALUES (?, 'P105分类', 'STRAIGHT_LINE', 1, 0) RETURNING id",
                Long.class, "P105CAT" + (System.nanoTime() % 1000000));
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_asset_card (asset_code, asset_name, category_id, acquisition_date, "
                        + "original_value, useful_life, status, enterprise_id, deleted) "
                        + "VALUES (?, 'P105资产', ?, CURRENT_DATE, 1000.00, 60, 'IN_USE', 1, 0) RETURNING id",
                Long.class, "P105AC" + (System.nanoTime() % 1000000), categoryId);
    }

    private AssetDisposalEntity draft() {
        AssetDisposalEntity e = new AssetDisposalEntity();
        e.setDisposalNo("P105ADP" + (System.nanoTime() % 1000000));
        e.setAssetId(inUseCard());
        e.setDisposalType("SALE");
        e.setDisposalDate(LocalDate.now());
        e.setPeriod("209912");
        e.setEnterpriseId(1L);
        return e;
    }

    @Test
    @DisplayName("不传 status 时默认 DRAFT")
    void defaultStatusIsDraft() {
        AssetDisposalEntity e = draft();
        e.setStatus(null);

        AssetDisposalEntity saved = disposalService.create(e);

        assertNotNull(saved.getId(), "未落库");
        assertEquals("DRAFT", disposalMapper.selectById(saved.getId()).getStatus(),
                "默认状态必须是 DRAFT（chk_disposal_status 允许且为合法起点）");
    }

    @Test
    @DisplayName("负向：客户端传 status=APPROVED 必须被忽略，强制 DRAFT（铁律 #1 人审）")
    void clientSuppliedApprovedIsIgnored() {
        AssetDisposalEntity e = draft();
        e.setStatus("APPROVED");   // 越权尝试：一步跳过 approve()

        AssetDisposalEntity saved = disposalService.create(e);

        AssetDisposalEntity db = disposalMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals("DRAFT", db.getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可直接创建「已审核」的资产处置单，"
                        + "绕过 DRAFT→APPROVED 的人工审批（铁律 #1）");
    }

    @Test
    @DisplayName("负向：客户端传 status=VOUCHERED 同样必须被忽略")
    void clientSuppliedVoucheredIsIgnored() {
        AssetDisposalEntity e = draft();
        e.setStatus("VOUCHERED");

        AssetDisposalEntity saved = disposalService.create(e);

        assertEquals("DRAFT", disposalMapper.selectById(saved.getId()).getStatus(),
                "「已制证」是更靠后的状态，更不允许由客户端直接指定");
    }
}