package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetDepreciationEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import com.huicai.sme.asset.mapper.AssetDepreciationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetCardServiceImpl - 资产卡片服务")
class AssetCardServiceImplTest {

    @Mock
    private AssetCardMapper cardMapper;

    @Mock
    private AssetCategoryMapper categoryMapper;

    @Mock
    private AssetDepreciationMapper depreciationMapper;

    @InjectMocks
    private AssetCardServiceImpl service;

    @Captor
    private ArgumentCaptor<AssetCardEntity> cardCaptor;

    @Captor
    private ArgumentCaptor<AssetDepreciationEntity> depCaptor;

    private AssetCardEntity stubCard(Long id) {
        AssetCardEntity e = new AssetCardEntity();
        e.setId(id);
        e.setAssetCode("ASSET-001");
        e.setAssetName("办公电脑");
        e.setCategoryId(1L);
        e.setOriginalValue(BigDecimal.valueOf(10000));
        e.setResidualValue(BigDecimal.ZERO);
        e.setAccumulatedDepreciation(BigDecimal.ZERO);
        e.setNetValue(BigDecimal.valueOf(10000));
        e.setStatus(AssetStatus.ASSET_CARD_IN_USE);
        e.setUsefulLife(5);
        e.setAcquisitionDate(LocalDate.of(2024, 1, 1));
        return e;
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById - 存在返回实体")
    void getById_存在_返回实体() {
        when(cardMapper.selectById(1L)).thenReturn(stubCard(1L));

        AssetCardEntity result = service.getById(1L);

        assertNotNull(result);
        assertEquals("ASSET-001", result.getAssetCode());
    }

    @Test
    @DisplayName("getById - 不存在抛BusinessException")
    void getById_不存在_抛BusinessException() {
        when(cardMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("资产卡片不存在"));
    }

    // ==================== create ====================

    @Test
    @DisplayName("create - 成功创建并填充默认值")
    void create_成功_默认值填充() {
        when(cardMapper.selectCount(any())).thenReturn(0L);

        AssetCardEntity entity = new AssetCardEntity();
        entity.setAssetCode("ASSET-001");
        entity.setAssetName("办公电脑");
        entity.setOriginalValue(BigDecimal.valueOf(10000));

        AssetCardEntity result = service.create(entity);

        assertEquals(AssetStatus.ASSET_CARD_IN_USE, result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getAccumulatedDepreciation());
        assertEquals(BigDecimal.ZERO, result.getResidualValue());
        assertEquals(BigDecimal.valueOf(10000), result.getNetValue());
        verify(cardMapper).insert(entity);
    }

    @Test
    @DisplayName("create - 资产编码重复抛异常")
    void create_编码重复_抛BusinessException() {
        when(cardMapper.selectCount(any())).thenReturn(1L);

        AssetCardEntity entity = new AssetCardEntity();
        entity.setAssetCode("ASSET-001");
        entity.setOriginalValue(BigDecimal.valueOf(10000));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(entity));
        assertTrue(ex.getMessage().contains("资产编码已存在"));
        verify(cardMapper, never()).insert(any(AssetCardEntity.class));
    }

    // ==================== update ====================

    @Test
    @DisplayName("update - 更新成功并使用ArgumentCaptor验证参数")
    void update_成功_ArgumentCaptor验证参数() {
        AssetCardEntity existing = stubCard(1L);
        when(cardMapper.selectById(1L)).thenReturn(existing);
        when(cardMapper.selectCount(any())).thenReturn(0L);

        AssetCardEntity updateData = new AssetCardEntity();
        updateData.setId(1L);
        updateData.setAssetCode("ASSET-002");
        updateData.setAssetName("新办公电脑");
        updateData.setCategoryId(2L);
        updateData.setSpec("i7-16G");
        updateData.setLocation("A栋3楼");
        updateData.setSerialNo("SN-2024-001");
        updateData.setRemark("升级设备");

        service.update(updateData);

        verify(cardMapper).updateById(cardCaptor.capture());
        AssetCardEntity captured = cardCaptor.getValue();
        assertEquals("ASSET-002", captured.getAssetCode());
        assertEquals("新办公电脑", captured.getAssetName());
        assertEquals(Long.valueOf(2L), captured.getCategoryId());
        assertEquals("i7-16G", captured.getSpec());
        assertEquals("A栋3楼", captured.getLocation());
        assertEquals("SN-2024-001", captured.getSerialNo());
        assertEquals("升级设备", captured.getRemark());
    }

    @Test
    @DisplayName("update - 不存在抛异常")
    void update_不存在_抛BusinessException() {
        when(cardMapper.selectById(99L)).thenReturn(null);

        AssetCardEntity entity = new AssetCardEntity();
        entity.setId(99L);

        assertThrows(BusinessException.class, () -> service.update(entity));
        verify(cardMapper, never()).updateById(any(AssetCardEntity.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete - IN_USE状态可删除")
    void delete_IN_USE_成功删除() {
        AssetCardEntity card = stubCard(1L);
        card.setStatus(AssetStatus.ASSET_CARD_IN_USE);
        when(cardMapper.selectById(1L)).thenReturn(card);

        service.delete(1L);

        verify(cardMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete - DRAFT状态不可删除抛异常")
    void delete_DRAFT_抛BusinessException() {
        AssetCardEntity card = stubCard(1L);
        card.setStatus(AssetStatus.ASSET_CARD_DRAFT);
        when(cardMapper.selectById(1L)).thenReturn(card);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("仅可删除在用或闲置状态的资产"));
        verify(cardMapper, never()).deleteById(any(Long.class));
    }

    // ==================== pageQuery ====================

    @Test
    @DisplayName("pageQuery - 关键字搜索")
    void pageQuery_关键字搜索() {
        Page<AssetCardEntity> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(stubCard(1L)));
        when(cardMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        IPage<AssetCardEntity> result = service.pageQuery("办公", null, null, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        verify(cardMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("pageQuery - 状态过滤")
    void pageQuery_状态过滤() {
        Page<AssetCardEntity> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(stubCard(1L)));
        when(cardMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        IPage<AssetCardEntity> result = service.pageQuery(null, AssetStatus.ASSET_CARD_IN_USE, null, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        verify(cardMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("pageQuery - 分类过滤")
    void pageQuery_分类过滤() {
        Page<AssetCardEntity> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(stubCard(1L)));
        when(cardMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        IPage<AssetCardEntity> result = service.pageQuery(null, null, 1L, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        verify(cardMapper).selectPage(any(Page.class), any());
    }

    // ==================== calculateDepreciation ====================

    @Test
    @DisplayName("calculateDepreciation - 直线法计算折旧")
    void calculateDepreciation_直线法() {
        AssetCardEntity card = new AssetCardEntity();
        card.setOriginalValue(BigDecimal.valueOf(12000));
        card.setResidualValue(BigDecimal.ZERO);
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(BigDecimal.valueOf(12000));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setAcquisitionDate(LocalDate.of(2024, 1, 1));

        BigDecimal amount = service.calculateDepreciation(card, "2024-01");

        // 12000 / (5*12) = 200
        assertEquals(0, new BigDecimal("200.00").compareTo(amount));
    }

    @Test
    @DisplayName("calculateDepreciation - 原值或年限为空返回0")
    void calculateDepreciation_原值或年限为空_返回0() {
        AssetCardEntity card = new AssetCardEntity();
        card.setOriginalValue(null);
        card.setUsefulLife(null);

        BigDecimal amount = service.calculateDepreciation(card, "2024-01");
        assertEquals(BigDecimal.ZERO, amount);
    }

    @Test
    @DisplayName("calculateDepreciation - 双倍余额递减法")
    void calculateDepreciation_双倍余额递减法() {
        AssetCardEntity card = new AssetCardEntity();
        card.setOriginalValue(BigDecimal.valueOf(12000));
        card.setResidualValue(BigDecimal.ZERO);
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(BigDecimal.valueOf(12000));
        card.setUsefulLife(5);
        card.setDepreciationMethod("DOUBLE_DECLINING");
        card.setAcquisitionDate(LocalDate.of(2024, 1, 1));

        BigDecimal amount = service.calculateDepreciation(card, "2024-01");

        // 月折旧率 = 2/5 = 0.4, 月折旧额 = 12000 * 0.4 / 12 = 400
        assertEquals(0, new BigDecimal("400.00").compareTo(amount));
    }

    @Test
    @DisplayName("calculateDepreciation - 年数总和法")
    void calculateDepreciation_年数总和法() {
        AssetCardEntity card = new AssetCardEntity();
        card.setOriginalValue(BigDecimal.valueOf(12000));
        card.setResidualValue(BigDecimal.ZERO);
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(BigDecimal.valueOf(12000));
        card.setUsefulLife(5);
        card.setDepreciationMethod("SUM_OF_YEARS");
        card.setAcquisitionDate(LocalDate.of(2024, 1, 1));

        BigDecimal amount = service.calculateDepreciation(card, "2024-01");

        // 年数总和 = 5*6/2 = 15, 第一年折旧率 = 5/15 = 0.333333
        // 月折旧额 = 12000 * 0.333333 / 12 = 333.33
        assertEquals(0, new BigDecimal("333.33").compareTo(amount));
    }

    // ==================== depreciateOne ====================

    @Test
    @DisplayName("depreciateOne - 计提单个资产折旧")
    void depreciateOne_计提折旧() {
        AssetCardEntity card = new AssetCardEntity();
        card.setId(1L);
        card.setAssetCode("ASSET-001");
        card.setOriginalValue(BigDecimal.valueOf(12000));
        card.setResidualValue(BigDecimal.ZERO);
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(BigDecimal.valueOf(12000));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setAcquisitionDate(LocalDate.of(2024, 1, 1));

        when(cardMapper.selectById(1L)).thenReturn(card);

        service.depreciateOne(1L, "2024-01");

        verify(depreciationMapper).insert(depCaptor.capture());
        AssetDepreciationEntity dep = depCaptor.getValue();
        assertEquals(Long.valueOf(1L), dep.getAssetId());
        assertEquals("2024-01", dep.getPeriod());
        assertEquals(0, new BigDecimal("200.00").compareTo(dep.getDepreciationAmount()));
        assertEquals(0, new BigDecimal("200.00").compareTo(dep.getAccumulatedDepreciation()));
        assertEquals(0, new BigDecimal("11800.00").compareTo(dep.getNetValue()));

        verify(cardMapper).accumulateDepreciation(1L, new BigDecimal("200.00"), "2024-01");
    }

    // ==================== recentCards ====================

    @Test
    @DisplayName("recentCards - 返回最近卡片列表")
    void recentCards_返回最近卡片列表() {
        Map<String, Object> row = Map.of(
                "id", 1L,
                "asset_code", "ASSET-001",
                "asset_name", "办公电脑"
        );
        when(cardMapper.selectRecent(5)).thenReturn(List.of(row));

        List<Map<String, Object>> result = service.recentCards(5);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ASSET-001", result.get(0).get("asset_code"));
        verify(cardMapper).selectRecent(5);
    }
}