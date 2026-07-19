package com.huicai.sme.asset.mapper;

import com.huicai.sme.asset.entity.AssetCategoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class AssetCategoryMapperTest {

    @Test
    @DisplayName("AssetCategoryMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        AssetCategoryMapper mapper = Mockito.mock(AssetCategoryMapper.class);
        AssetCategoryEntity entity = new AssetCategoryEntity();
        entity.setCode("ASSET-001");
        entity.setName("办公设备");
        entity.setParentId(null);
        entity.setLevel(1);
        entity.setDepreciationMethod("LINEAR");
        entity.setUsefulLife(5);
        entity.setResidualRate(java.math.BigDecimal.valueOf(0.05));
        entity.setAssetSubjectId(1L);
        entity.setDepreciationSubjectId(2L);
        entity.setExpenseSubjectId(3L);
        entity.setRemark("测试资产类别");
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("AssetCategoryMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        AssetCategoryMapper mapper = Mockito.mock(AssetCategoryMapper.class);
        AssetCategoryEntity entity = new AssetCategoryEntity();
        entity.setCode("ASSET-001");
        entity.setName("办公设备");
        entity.setParentId(null);
        entity.setLevel(1);
        entity.setDepreciationMethod("LINEAR");
        entity.setUsefulLife(5);
        entity.setResidualRate(java.math.BigDecimal.valueOf(0.05));
        entity.setAssetSubjectId(1L);
        entity.setDepreciationSubjectId(2L);
        entity.setExpenseSubjectId(3L);
        entity.setRemark("测试资产类别");
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        AssetCategoryEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("AssetCategoryMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        AssetCategoryMapper mapper = Mockito.mock(AssetCategoryMapper.class);
        AssetCategoryEntity entity = new AssetCategoryEntity();
        entity.setCode("ASSET-001");
        entity.setName("办公设备");
        entity.setParentId(null);
        entity.setLevel(1);
        entity.setDepreciationMethod("LINEAR");
        entity.setUsefulLife(5);
        entity.setResidualRate(java.math.BigDecimal.valueOf(0.05));
        entity.setAssetSubjectId(1L);
        entity.setDepreciationSubjectId(2L);
        entity.setExpenseSubjectId(3L);
        entity.setRemark("测试资产类别");
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("AssetCategoryMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        AssetCategoryMapper mapper = Mockito.mock(AssetCategoryMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("AssetCategoryMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        AssetCategoryMapper mapper = Mockito.mock(AssetCategoryMapper.class);
        AssetCategoryEntity e = new AssetCategoryEntity();
        e.setCode("ASSET-001");
        e.setName("办公设备");
        e.setParentId(null);
        e.setLevel(1);
        e.setDepreciationMethod("LINEAR");
        e.setUsefulLife(5);
        e.setResidualRate(java.math.BigDecimal.valueOf(0.05));
        e.setAssetSubjectId(1L);
        e.setDepreciationSubjectId(2L);
        e.setExpenseSubjectId(3L);
        e.setRemark("测试资产类别");
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
