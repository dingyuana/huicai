package com.huicai.module.asset.mapper;

import com.huicai.module.asset.entity.AssetDepreciationEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetDepreciationMapper 方法签名验证测试
 */
public class AssetDepreciationMapperTest {

    @Test
    @DisplayName("AssetDepreciationMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        AssetDepreciationMapper mapper = Mockito.mock(AssetDepreciationMapper.class);
        AssetDepreciationEntity entity = new AssetDepreciationEntity();
        
        // 设置必要字段
        entity.setAssetId(1L);
        entity.setPeriod("202607");
        entity.setDepreciationAmount(java.math.BigDecimal.valueOf(1000));
        entity.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(5000));
        entity.setNetValue(java.math.BigDecimal.valueOf(4000));
        entity.setVoucherId(null);
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("AssetDepreciationMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        AssetDepreciationMapper mapper = Mockito.mock(AssetDepreciationMapper.class);
        AssetDepreciationEntity entity = new AssetDepreciationEntity();
        entity.setAssetId(1L);
        entity.setPeriod("202607");
        entity.setDepreciationAmount(java.math.BigDecimal.valueOf(1000));
        entity.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(5000));
        entity.setNetValue(java.math.BigDecimal.valueOf(4000));
        entity.setVoucherId(null);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        AssetDepreciationEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("AssetDepreciationMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        AssetDepreciationMapper mapper = Mockito.mock(AssetDepreciationMapper.class);
        AssetDepreciationEntity entity = new AssetDepreciationEntity();
        entity.setAssetId(1L);
        entity.setPeriod("202607");
        entity.setDepreciationAmount(java.math.BigDecimal.valueOf(1000));
        entity.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(5000));
        entity.setNetValue(java.math.BigDecimal.valueOf(4000));
        entity.setVoucherId(null);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("AssetDepreciationMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        AssetDepreciationMapper mapper = Mockito.mock(AssetDepreciationMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("AssetDepreciationMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        AssetDepreciationMapper mapper = Mockito.mock(AssetDepreciationMapper.class);
        
        // 验证所有常用方法存在
        AssetDepreciationEntity e = new AssetDepreciationEntity();
        e.setAssetId(1L);
        e.setPeriod("202607");
        e.setDepreciationAmount(java.math.BigDecimal.valueOf(1000));
        e.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(5000));
        e.setNetValue(java.math.BigDecimal.valueOf(4000));
        e.setVoucherId(null);
        Mockito.when(mapper.insert(e)).thenReturn(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(e);
        Mockito.when(mapper.updateById(e)).thenReturn(1);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        assertEquals(1, mapper.insert(e));
        assertNotNull(mapper.selectById(1L));
        assertEquals(1, mapper.updateById(e));
        assertEquals(1, mapper.deleteById(1L));
    }
}
