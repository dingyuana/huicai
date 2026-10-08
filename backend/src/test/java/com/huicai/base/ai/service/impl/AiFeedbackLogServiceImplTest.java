package com.huicai.base.ai.service.impl;

import com.huicai.base.ai.entity.AiFeedbackLogEntity;
import com.huicai.base.ai.mapper.AiFeedbackLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class AiFeedbackLogServiceImplTest {

    @Mock private AiFeedbackLogMapper mapper;
    @InjectMocks private AiFeedbackLogServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfoCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiFeedbackLogEntity.class);
    }

    @BeforeEach
    void setUpEnterpriseContext() {
        EnterpriseContextHolder.set(1L);
    }

    @AfterEach
    void clearEnterpriseContext() {
        EnterpriseContextHolder.clear();
    }

    private AiFeedbackLogEntity stubEntity() {
        AiFeedbackLogEntity e = new AiFeedbackLogEntity();
        e.setId(1L);
        e.setBankTxnId(1L);
        e.setAiSuggestedAction("CLASSIFY");
        e.setAiConfidence(85);
        e.setAiBusinessScene("报销");
        e.setHumanAction("CONFIRM");
        e.setCreatedBy(1L);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        AiFeedbackLogEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_返回Null() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void deleteByBankTxn_调delete() {
        service.deleteByBankTxn(1L);
        verify(mapper).delete(any());
    }

    @Test
    void page_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.page(null, null, null, null);
        verify(mapper).selectPage(any(), any());
    }

    // ==================== P106 D-1：无企业上下文必须 fail-closed ====================

    @Test
    void page_无企业上下文_抛BusinessException而非返回全表() {
        EnterpriseContextHolder.clear();
        assertThrows(BusinessException.class, () -> service.page(null, null, 1, 20));
        verify(mapper, never()).selectPage(any(), any());
    }

    @Test
    void summary_无企业上下文_抛BusinessException而非全表汇总() {
        EnterpriseContextHolder.clear();
        assertThrows(BusinessException.class, () -> service.summary());
        verify(mapper, never()).selectList(any());
    }

    /**
     * 同 {@code ClassificationRuleServiceTest#page_唯一值条件是当前企业的enterpriseId}：
     * 端到端断言会被 {@code EnterpriseDataPermissionInterceptor} 的 SQL 层注入掩盖，
     * 故在此直接断言 wrapper 的绑定值，证明应用层过滤真实存在。
     */
    @Test
    void page_唯一值条件是当前企业的enterpriseId() {
        EnterpriseContextHolder.set(990001L);
        service.page(null, null, 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<AiFeedbackLogEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("enterprise_id"), "应用层应按 enterprise_id 过滤，实际: " + sql);
        assertFalse(sql.contains("tenant_id"), "D-1 后不得再出现 tenant_id，实际: " + sql);
    }

    @Test
    void summary_唯一值条件是当前企业的enterpriseId() {
        EnterpriseContextHolder.set(990001L);
        service.summary();

        ArgumentCaptor<LambdaQueryWrapper<AiFeedbackLogEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("enterprise_id"), "应用层应按 enterprise_id 过滤，实际: " + sql);
        assertFalse(sql.contains("tenant_id"), "D-1 后不得再出现 tenant_id，实际: " + sql);
    }
}
