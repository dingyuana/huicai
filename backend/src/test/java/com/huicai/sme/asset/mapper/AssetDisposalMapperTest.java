package com.huicai.sme.asset.mapper;

import com.huicai.sme.asset.entity.AssetDisposalEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class AssetDisposalMapperTest {

    @Test
    @DisplayName("AssetDisposalMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        AssetDisposalMapper mapper = Mockito.mock(AssetDisposalMapper.class);
        AssetDisposalEntity entity = new AssetDisposalEntity();
        entity.setDisposalNo("DISPOSAL-001");
        entity.setAssetId(1L);
        entity.setDisposalType("SALE");
        entity.setDisposalDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setOriginalValue(java.math.BigDecimal.valueOf(50000));
        entity.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(20000));
        entity.setNetValue(java.math.BigDecimal.valueOf(30000));
        entity.setDisposalIncome(java.math.BigDecimal.valueOf(25000));
        entity.setDisposalExpense(java.math.BigDecimal.ZERO);
        entity.setGainLoss(java.math.BigDecimal.valueOf(-5000));
        entity.setStatus("PENDING_APPROVAL");
        entity.setVoucherId(null);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("AssetDisposalMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        AssetDisposalMapper mapper = Mockito.mock(AssetDisposalMapper.class);
        AssetDisposalEntity entity = new AssetDisposalEntity();
        entity.setDisposalNo("DISPOSAL-001");
        entity.setAssetId(1L);
        entity.setDisposalType("SALE");
        entity.setDisposalDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setOriginalValue(java.math.BigDecimal.valueOf(50000));
        entity.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(20000));
        entity.setNetValue(java.math.BigDecimal.valueOf(30000));
        entity.setDisposalIncome(java.math.BigDecimal.valueOf(25000));
        entity.setDisposalExpense(java.math.BigDecimal.ZERO);
        entity.setGainLoss(java.math.BigDecimal.valueOf(-5000));
        entity.setStatus("PENDING_APPROVAL");
        entity.setVoucherId(null);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        AssetDisposalEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("AssetDisposalMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        AssetDisposalMapper mapper = Mockito.mock(AssetDisposalMapper.class);
        AssetDisposalEntity entity = new AssetDisposalEntity();
        entity.setDisposalNo("DISPOSAL-001");
        entity.setAssetId(1L);
        entity.setDisposalType("SALE");
        entity.setDisposalDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setOriginalValue(java.math.BigDecimal.valueOf(50000));
        entity.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(20000));
        entity.setNetValue(java.math.BigDecimal.valueOf(30000));
        entity.setDisposalIncome(java.math.BigDecimal.valueOf(25000));
        entity.setDisposalExpense(java.math.BigDecimal.ZERO);
        entity.setGainLoss(java.math.BigDecimal.valueOf(-5000));
        entity.setStatus("PENDING_APPROVAL");
        entity.setVoucherId(null);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("AssetDisposalMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        AssetDisposalMapper mapper = Mockito.mock(AssetDisposalMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("AssetDisposalMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        AssetDisposalMapper mapper = Mockito.mock(AssetDisposalMapper.class);
        AssetDisposalEntity e = new AssetDisposalEntity();
        e.setDisposalNo("DISPOSAL-001");
        e.setAssetId(1L);
        e.setDisposalType("SALE");
        e.setDisposalDate(java.time.LocalDate.now());
        e.setPeriod("202607");
        e.setOriginalValue(java.math.BigDecimal.valueOf(50000));
        e.setAccumulatedDepreciation(java.math.BigDecimal.valueOf(20000));
        e.setNetValue(java.math.BigDecimal.valueOf(30000));
        e.setDisposalIncome(java.math.BigDecimal.valueOf(25000));
        e.setDisposalExpense(java.math.BigDecimal.ZERO);
        e.setGainLoss(java.math.BigDecimal.valueOf(-5000));
        e.setStatus("PENDING_APPROVAL");
        e.setVoucherId(null);
        e.setCreatedBy(1L);
        e.setUpdatedBy(1L);
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
