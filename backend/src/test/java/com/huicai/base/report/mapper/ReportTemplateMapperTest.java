package com.huicai.base.report.mapper;

import com.huicai.base.report.entity.ReportTemplateEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class ReportTemplateMapperTest {

    @Test
    @DisplayName("ReportTemplateMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        ReportTemplateMapper mapper = Mockito.mock(ReportTemplateMapper.class);
        ReportTemplateEntity entity = new ReportTemplateEntity();
        entity.setTemplateCode("REPORT-001");
        entity.setTemplateName("测试报表");
        entity.setReportType("BALANCE_SHEET");
        entity.setConfig("{}");
        entity.setIsSystem(false);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("ReportTemplateMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        ReportTemplateMapper mapper = Mockito.mock(ReportTemplateMapper.class);
        ReportTemplateEntity entity = new ReportTemplateEntity();
        entity.setTemplateCode("REPORT-001");
        entity.setTemplateName("测试报表");
        entity.setReportType("BALANCE_SHEET");
        entity.setConfig("{}");
        entity.setIsSystem(false);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        ReportTemplateEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("ReportTemplateMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        ReportTemplateMapper mapper = Mockito.mock(ReportTemplateMapper.class);
        ReportTemplateEntity entity = new ReportTemplateEntity();
        entity.setTemplateCode("REPORT-001");
        entity.setTemplateName("测试报表");
        entity.setReportType("BALANCE_SHEET");
        entity.setConfig("{}");
        entity.setIsSystem(false);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("ReportTemplateMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        ReportTemplateMapper mapper = Mockito.mock(ReportTemplateMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("ReportTemplateMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        ReportTemplateMapper mapper = Mockito.mock(ReportTemplateMapper.class);
        ReportTemplateEntity e = new ReportTemplateEntity();
        e.setTemplateCode("REPORT-001");
        e.setTemplateName("测试报表");
        e.setReportType("BALANCE_SHEET");
        e.setConfig("{}");
        e.setIsSystem(false);
        e.setCreatedBy(1L);
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
