package com.huicai.sme.asset.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import com.huicai.sme.asset.mapper.AssetInventoryMapper;
import com.huicai.sme.asset.service.AssetInventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 资产盘点「完成」真库测试（REQ-2026-129 / P105 契约工作带出的 P0）
 *
 * <p><b>缺陷</b>：{@code AssetInventoryServiceImpl#complete()} 原本写
 * {@code status = "COMPLETED"}，而 {@code chk_inv_status} 的允许集是
 * {@code DRAFT / IN_PROGRESS / CONFIRMED / VOUCHERED} —— <b>不含 {@code COMPLETED}</b>
 * ⇒ 紧随的 update 必抛 23514，<b>「完成盘点」必然 500</b>。
 *
 * <p><b>判为代码写错值，而非约束漏值</b>，三条证据：
 * <ol>
 *   <li>同包的 {@code AssetInventoryStateMachineService} 类注释明写
 *       「封装资产盘点 3 状态 (IN_PROGRESS/CONFIRMED/VOUCHERED) 的状态流转检查」
 *       —— {@code CONFIRMED} 正是设计中的「盘点完成态」；</li>
 *   <li>全仓<b>无任何代码</b>给本表写 {@code CONFIRMED}，该合法值一直空置；</li>
 *   <li>库内 {@code t_asset_inventory} <b>0 行</b> —— 与「一调就崩，故从未产生过数据」吻合。</li>
 * </ol>
 * 故改用既有合法态 {@code CONFIRMED}；<b>不</b>给 CHECK 补 {@code COMPLETED} ——
 * 那会造出「盘点完成」的第二种合法拼写，下游 {@code status='CONFIRMED'} 的查询会静默漏掉。
 */
@DisplayName("P105 资产盘点：完成动作不得写入非法状态")
class AssetInventoryCompleteStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private AssetInventoryService inventoryService;

    @Autowired
    private AssetInventoryMapper inventoryMapper;

    private Long createInventory() {
        AssetInventoryEntity e = new AssetInventoryEntity();
        e.setInventoryNo("P105AI" + (System.nanoTime() % 1000000));
        e.setInventoryDate(LocalDate.now());
        e.setPeriod("209912");
        e.setStatus("IN_PROGRESS");
        e.setEnterpriseId(1L);
        return inventoryService.create(e, Collections.emptyList()).getId();
    }

    @Test
    @DisplayName("完成盘点：状态必须是 chk_inv_status 允许的 CONFIRMED（原报 23514）")
    void completeMustUseLegalStatus() {
        Long id = createInventory();

        inventoryService.complete(id, Collections.emptyList());

        AssetInventoryEntity db = inventoryMapper.selectById(id);
        assertNotNull(db, "回读不到");
        assertEquals("CONFIRMED", db.getStatus(),
                "完成盘点后状态必须是 CONFIRMED（chk_inv_status 允许），实际=" + db.getStatus()
                        + "；原写 COMPLETED 不在允许集内 ⇒ 必抛 23514");
    }

    @Test
    @DisplayName("负向：客户端传 status=CONFIRMED 创建时必须被忽略，强制 DRAFT（铁律 #1）")
    void clientSuppliedStatusIsIgnored() {
        AssetInventoryEntity e = new AssetInventoryEntity();
        e.setInventoryNo("P105AI" + (System.nanoTime() % 1000000));
        e.setInventoryDate(LocalDate.now());
        e.setPeriod("209912");
        e.setStatus("CONFIRMED");
        e.setEnterpriseId(1L);

        Long id = inventoryService.create(e, Collections.emptyList()).getId();

        assertEquals("DRAFT", inventoryMapper.selectById(id).getStatus(),
                "客户端传入的 status 必须被忽略（CreateRequest 只是包了一层，内层 Entity 仍可绑定 status）");
    }

    @Test
    @DisplayName("负向：非 DRAFT/IN_PROGRESS 状态不得完成盘点（终态守卫）")
    void completeFromIllegalStateIsRejected() {
        Long id = createInventory();
        inventoryService.complete(id, Collections.emptyList());   // → CONFIRMED

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.huicai.common.exception.BusinessException.class,
                () -> inventoryService.complete(id, Collections.emptyList()));
        assertNotNull(ex.getMessage(), "异常必须有可读信息（铁律 #14）");
    }
}