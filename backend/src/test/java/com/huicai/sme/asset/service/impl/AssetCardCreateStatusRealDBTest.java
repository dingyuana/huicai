package com.huicai.sme.asset.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.service.AssetCardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 资产卡片创建真库测试（REQ-2026-129 / P105 指纹扫描第 5 处）
 *
 * <p><b>缺陷</b>：{@code AssetCardServiceImpl#create()} 原本
 * {@code if (status == null) setStatus(IN_USE)}，而
 * {@code AssetCardController} 直收 {@code @RequestBody AssetCardEntity}（违反铁律 #13）。
 * {@code chk_asset_status} 允许 {@code DRAFT/IN_USE/IDLE/DISPOSED/SCRAPPED}，
 * 故客户端 POST {@code status=DISPOSED} / {@code SCRAPPED} 可
 * <b>直接创建一张「已处置 / 已报废」的资产卡</b>，绕过资产生命周期（铁律 #1 + #4）。
 *
 * <p><b>本类只锁定「客户端不能指定状态」这一条</b>；
 * 新建卡默认 {@code IN_USE} 是否合理（是否应先 DRAFT 待转固）属设计问题，未在本次改动。
 */
@DisplayName("P105 资产卡片创建：状态不可由客户端指定")
class AssetCardCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private AssetCardService assetCardService;

    @Autowired
    private AssetCardMapper assetCardMapper;

    /** t_asset_category 库内无种子数据（count=0）且 t_asset_card.category_id 有外键，
     *  故夹具必须自造分类 —— 不可写死 category_id=1。 */
    private Long categoryId() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_asset_category (code, name, depreciation_method, enterprise_id, deleted) "
                        + "VALUES (?, 'P105分类', 'STRAIGHT_LINE', 1, 0) RETURNING id",
                Long.class, "P105CAT" + (System.nanoTime() % 1000000));
    }

    private AssetCardEntity draft() {
        AssetCardEntity e = new AssetCardEntity();
        e.setAssetCode("P105C" + (System.nanoTime() % 1000000));
        e.setAssetName("P105卡片");
        e.setCategoryId(categoryId());
        e.setAcquisitionDate(LocalDate.now());
        e.setOriginalValue(new java.math.BigDecimal("1000.00"));
        e.setUsefulLife(60);
        e.setEnterpriseId(1L);
        return e;
    }

    @Test
    @DisplayName("不传 status 时落到服务层默认值 IN_USE")
    void defaultStatusComesFromService() {
        AssetCardEntity e = draft();
        e.setStatus(null);

        AssetCardEntity saved = assetCardService.create(e);

        assertNotNull(saved.getId(), "未落库");
        assertEquals(AssetStatus.ASSET_CARD_IN_USE, assetCardMapper.selectById(saved.getId()).getStatus(),
                "默认值应由服务层决定");
    }

    @Test
    @DisplayName("负向：客户端传 status=DISPOSED 必须被忽略（铁律 #1/#4）")
    void clientSuppliedDisposedIsIgnored() {
        AssetCardEntity e = draft();
        e.setStatus(AssetStatus.ASSET_CARD_DISPOSED);

        AssetCardEntity saved = assetCardService.create(e);

        AssetCardEntity db = assetCardMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals(AssetStatus.ASSET_CARD_IN_USE, db.getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可直接创建「已处置」资产卡，"
                        + "绕过资产生命周期（实际=" + db.getStatus() + "）");
    }

    @Test
    @DisplayName("负向：客户端传 status=SCRAPPED 同样必须被忽略")
    void clientSuppliedScrappedIsIgnored() {
        AssetCardEntity e = draft();
        e.setStatus(AssetStatus.ASSET_CARD_SCRAPPED);

        AssetCardEntity saved = assetCardService.create(e);

        assertEquals(AssetStatus.ASSET_CARD_IN_USE, assetCardMapper.selectById(saved.getId()).getStatus(),
                "「已报废」更不允许由客户端直接指定");
    }
}