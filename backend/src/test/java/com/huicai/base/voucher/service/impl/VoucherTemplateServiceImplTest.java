package com.huicai.base.voucher.service.impl;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;
import com.huicai.base.voucher.mapper.VoucherTemplateLineMapper;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoucherTemplateService 专用测试 — 覆盖模板匹配/创建/更新/激活/删除
 *
 * <p>P0 优先级：5 个方法在 VoucherTemplateControllerTest 中被 mock。
 */
@ExtendWith(MockitoExtension.class)
class VoucherTemplateServiceImplTest {

    @Mock
    private VoucherTemplateMapper templateMapper;

    @Mock
    private VoucherTemplateLineMapper lineMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VoucherTemplateServiceImpl service;

    private static final Long TEMPLATE_ID = 100L;
    private static final String CLASSIFICATION = "SALES_INVOICE";
    private static final String TEMPLATE_NAME = "销售发票模板";

    @BeforeEach
    void setUp() {
        service = new VoucherTemplateServiceImpl(templateMapper, lineMapper, jdbcTemplate);
    }

    private VoucherTemplateEntity mockTemplate() {
        VoucherTemplateEntity t = new VoucherTemplateEntity();
        t.setId(TEMPLATE_ID);
        t.setName(TEMPLATE_NAME);
        t.setBusinessType(CLASSIFICATION);
        t.setIsActive(true);
        return t;
    }

    // ==================== matchByClassification ====================

    @Test
    @DisplayName("matchByClassification — 匹配成功返回模板")
    void matchByClassification_匹配成功() {
        VoucherTemplateEntity template = mockTemplate();
        when(templateMapper.selectActiveByClassification(CLASSIFICATION)).thenReturn(template);

        VoucherTemplateEntity result = service.matchByClassification(CLASSIFICATION);

        assertNotNull(result);
        assertEquals(TEMPLATE_ID, result.getId());
        assertEquals(TEMPLATE_NAME, result.getName());
    }

    @Test
    @DisplayName("matchByClassification — 无匹配返回 null")
    void matchByClassification_无匹配返回Null() {
        when(templateMapper.selectActiveByClassification(CLASSIFICATION)).thenReturn(null);

        VoucherTemplateEntity result = service.matchByClassification(CLASSIFICATION);

        assertNull(result);
    }

    @Test
    @DisplayName("matchByClassification — 空分类返回 null")
    void matchByClassification_空分类返回Null() {
        assertNull(service.matchByClassification(null));
        assertNull(service.matchByClassification(""));
        verify(templateMapper, never()).selectActiveByClassification(any());
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById — 存在返回模板")
    void getById_存在() {
        when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(mockTemplate());

        VoucherTemplateEntity result = service.getById(TEMPLATE_ID);

        assertNotNull(result);
        assertEquals(TEMPLATE_ID, result.getId());
    }

    @Test
    @DisplayName("getById — 不存在返回空")
    void getById_不存在返回空() {
        when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

        VoucherTemplateEntity result = service.getById(TEMPLATE_ID);

        assertNull(result);
    }

    // ==================== create ====================

    @Test
    @DisplayName("create — 创建模板及分录行")
    void create_创建模板和分录行() {
        VoucherTemplateEntity entity = new VoucherTemplateEntity();
        entity.setName(TEMPLATE_NAME);
        entity.setBusinessType(CLASSIFICATION);

        VoucherTemplateLineEntity line1 = new VoucherTemplateLineEntity();
        line1.setSubjectId(1001L);
        line1.setDirection("debit");
        VoucherTemplateLineEntity line2 = new VoucherTemplateLineEntity();
        line2.setSubjectId(2001L);
        line2.setDirection("credit");// 无同名校验冲突
        // selectCount 默认返回 0L，无需显式 mock
        when(templateMapper.insert(any(VoucherTemplateEntity.class))).thenAnswer(invocation -> {
            VoucherTemplateEntity e = invocation.getArgument(0);
            e.setId(TEMPLATE_ID);
            return 1;
        });
        when(lineMapper.insert(any(VoucherTemplateLineEntity.class))).thenReturn(1);

        VoucherTemplateEntity result = service.create(entity, List.of(line1, line2));

        assertNotNull(result.getId());
        verify(templateMapper).insert(any(VoucherTemplateEntity.class));
        verify(lineMapper, times(2)).insert(any(VoucherTemplateLineEntity.class));
        assertEquals(TEMPLATE_ID, line1.getTemplateId());
        assertEquals(TEMPLATE_ID, line2.getTemplateId());
        assertNotNull(line1.getLineOrder());
        assertNotNull(line2.getLineOrder());
    }

    @Test
    @DisplayName("create — 同名模板抛异常")
    void create_同名模板_抛异常() {
        VoucherTemplateEntity entity = new VoucherTemplateEntity();
        entity.setName(TEMPLATE_NAME);

        when(templateMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.create(entity, List.of()));
        verify(templateMapper, never()).insert(any(VoucherTemplateEntity.class));
    }

    // ==================== update ====================

    @Test
    @DisplayName("update — 更新模板名称和分类")
    void update_更新模板() {
        VoucherTemplateEntity existing = mockTemplate();
        existing.setIsActive(false);
        when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(existing);
        when(templateMapper.updateById(any(VoucherTemplateEntity.class))).thenReturn(1);

        VoucherTemplateEntity update = new VoucherTemplateEntity();
        update.setId(TEMPLATE_ID);
        update.setName("新名称");
        service.update(update);

        ArgumentCaptor<VoucherTemplateEntity> captor = ArgumentCaptor.forClass(VoucherTemplateEntity.class);
        verify(templateMapper).updateById(captor.capture());
        assertEquals(TEMPLATE_ID, captor.getValue().getId());
    }

    @Test
    @DisplayName("update — 模板不存在抛异常")
    void update_不存在_抛异常() {
        when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

        VoucherTemplateEntity update = new VoucherTemplateEntity();
        update.setId(TEMPLATE_ID);
        assertThrows(RuntimeException.class, () -> service.update(update));
    }

    // ==================== toggleActive ====================

    @Test
    @DisplayName("toggleActive — 激活模板")
    void toggleActive_激活模板() {
        VoucherTemplateEntity existing = mockTemplate();
        existing.setIsActive(false);
        when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(existing);
        // mockTemplate 未设 classification，deactivateSiblings 分支跳过
        when(templateMapper.updateById(any(VoucherTemplateEntity.class))).thenReturn(1);

        service.toggleActive(TEMPLATE_ID, true);

        ArgumentCaptor<VoucherTemplateEntity> captor = ArgumentCaptor.forClass(VoucherTemplateEntity.class);
        verify(templateMapper, atLeastOnce()).updateById(captor.capture());
        assertTrue(captor.getValue().getIsActive());
    }

    @Test
    @DisplayName("toggleActive — 停用模板")
    void toggleActive_停用模板() {
        VoucherTemplateEntity existing = mockTemplate();
        when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(existing);
        when(templateMapper.updateById(any(VoucherTemplateEntity.class))).thenReturn(1);

        service.toggleActive(TEMPLATE_ID, false);

        ArgumentCaptor<VoucherTemplateEntity> captor = ArgumentCaptor.forClass(VoucherTemplateEntity.class);
        verify(templateMapper).updateById(captor.capture());
        assertFalse(captor.getValue().getIsActive());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete — 逻辑删除模板及分录行")
    void delete_逻辑删除() {
        // 服务直接 deleteById，不先查 selectById
        service.delete(TEMPLATE_ID);
        verify(templateMapper).deleteById(TEMPLATE_ID);
        verify(lineMapper).deleteByTemplateId(TEMPLATE_ID);
    }

    // ==================== getLines ====================

    @Test
    @DisplayName("getLines — 查询模板分录行")
    void getLines_返回分录行列表() {
        VoucherTemplateLineEntity line = new VoucherTemplateLineEntity();
        line.setTemplateId(TEMPLATE_ID);
        line.setSubjectId(1001L);
        when(lineMapper.selectByTemplateId(TEMPLATE_ID)).thenReturn(List.of(line));

        List<VoucherTemplateLineEntity> result = service.getLines(TEMPLATE_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getSubjectId());
    }

    // ==================== listAllActive ====================

    @Test
    @DisplayName("listAllActive — 查询所有激活模板")
    void listAllActive_返回激活列表() {
        when(templateMapper.selectList(any())).thenReturn(List.of(mockTemplate()));

        var result = service.listAllActive();

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}