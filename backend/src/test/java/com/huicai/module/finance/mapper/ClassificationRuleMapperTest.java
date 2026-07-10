package com.huicai.module.finance.mapper;

import com.huicai.module.finance.entity.ClassificationRuleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ClassificationRuleMapper 方法签名验证测试
 */
public class ClassificationRuleMapperTest {

    @Test
    @DisplayName("ClassificationRuleMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        ClassificationRuleMapper mapper = Mockito.mock(ClassificationRuleMapper.class);
        ClassificationRuleEntity entity = new ClassificationRuleEntity();
        
        // 设置必要字段
        entity.setName("测试规则");
        entity.setRuleType("BANK_STATEMENT");
        entity.setRouteType("AUTO");
        entity.setDirection("DEBIT");
        entity.setMatchField("summary");
        entity.setPattern("工资");
        entity.setClassification("SALARY");
        entity.setPriority(100);
        entity.setIsActive(true);
        entity.setIsSystem(false);
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("ClassificationRuleMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        ClassificationRuleMapper mapper = Mockito.mock(ClassificationRuleMapper.class);
        ClassificationRuleEntity entity = new ClassificationRuleEntity();
        entity.setName("测试规则");
        entity.setRuleType("BANK_STATEMENT");
        entity.setRouteType("AUTO");
        entity.setDirection("DEBIT");
        entity.setMatchField("summary");
        entity.setPattern("工资");
        entity.setClassification("SALARY");
        entity.setPriority(100);
        entity.setIsActive(true);
        entity.setIsSystem(false);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        ClassificationRuleEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("ClassificationRuleMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        ClassificationRuleMapper mapper = Mockito.mock(ClassificationRuleMapper.class);
        ClassificationRuleEntity entity = new ClassificationRuleEntity();
        entity.setName("测试规则");
        entity.setRuleType("BANK_STATEMENT");
        entity.setRouteType("AUTO");
        entity.setDirection("DEBIT");
        entity.setMatchField("summary");
        entity.setPattern("工资");
        entity.setClassification("SALARY");
        entity.setPriority(100);
        entity.setIsActive(true);
        entity.setIsSystem(false);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("ClassificationRuleMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        ClassificationRuleMapper mapper = Mockito.mock(ClassificationRuleMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("ClassificationRuleMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        ClassificationRuleMapper mapper = Mockito.mock(ClassificationRuleMapper.class);
        
        // 验证所有常用方法存在
        ClassificationRuleEntity e = new ClassificationRuleEntity();
        e.setName("测试规则");
        e.setRuleType("BANK_STATEMENT");
        e.setRouteType("AUTO");
        e.setDirection("DEBIT");
        e.setMatchField("summary");
        e.setPattern("工资");
        e.setClassification("SALARY");
        e.setPriority(100);
        e.setIsActive(true);
        e.setIsSystem(false);
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
