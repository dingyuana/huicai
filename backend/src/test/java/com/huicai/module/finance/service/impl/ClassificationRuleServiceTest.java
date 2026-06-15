package com.huicai.module.finance.service.impl;

import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.ClassificationRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 分类规则 Service 纯单元测试
 * <p>
 * 参考 VoucherEntryValidationTest 模式: 不启动 Spring, 仅测试纯业务逻辑.
 * 覆盖 8 方法中的 6 个, 跳过 readOnly 的 page/getById/match.
 */
@ExtendWith(MockitoExtension.class)
class ClassificationRuleServiceTest {

    @Mock
    private ClassificationRuleMapper mapper;

    @InjectMocks
    private ClassificationRuleServiceImpl service;

    // ==================== create ====================

    @Test
    void create_填充默认值() {
        ClassificationRuleEntity input = new ClassificationRuleEntity();
        ClassificationRuleEntity result = service.create(input);

        assertEquals(1L, result.getTenantId());
        assertEquals("keyword_regex", result.getRuleType());
        assertEquals("description", result.getMatchField());
        assertEquals(0, result.getPriority());
        assertTrue(result.getIsActive());
        assertEquals(0, result.getDeleted());
        assertEquals(1L, result.getCreatedBy());
        assertEquals(1L, result.getUpdatedBy());
        verify(mapper).insert(input);
    }

    @Test
    void create_保留已有值() {
        when(mapper.insert(any(ClassificationRuleEntity.class))).thenReturn(1);

        ClassificationRuleEntity input = new ClassificationRuleEntity();
        input.setTenantId(5L);
        input.setRuleType("counterparty_match");
        input.setMatchField("counterparty");
        input.setPriority(10);
        input.setIsActive(false);
        input.setDeleted(0);

        ClassificationRuleEntity result = service.create(input);

        assertEquals(5L, result.getTenantId());
        assertEquals("counterparty_match", result.getRuleType());
        assertEquals("counterparty", result.getMatchField());
        assertEquals(10, result.getPriority());
        assertFalse(result.getIsActive());
    }

    // ==================== update ====================

    @Test
    void update_存在则更新() {
        ClassificationRuleEntity existing = new ClassificationRuleEntity();
        existing.setId(1L);
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.updateById(any(ClassificationRuleEntity.class))).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(existing);

        ClassificationRuleEntity input = new ClassificationRuleEntity();
        input.setName("新规则");

        ClassificationRuleEntity result = service.update(1L, input);

        assertNotNull(result);
        ArgumentCaptor<ClassificationRuleEntity> updateCaptor = ArgumentCaptor.forClass(ClassificationRuleEntity.class);
        verify(mapper).updateById(updateCaptor.capture());
        ClassificationRuleEntity captured = updateCaptor.getValue();
        assertEquals(1L, captured.getId());
        assertEquals("新规则", captured.getName());
        assertNotNull(captured.getUpdatedAt());
        assertEquals(1L, captured.getUpdatedBy());
    }

    @Test
    void update_不存在返回null() {
        when(mapper.selectById(99L)).thenReturn(null);

        ClassificationRuleEntity input = new ClassificationRuleEntity();
        ClassificationRuleEntity result = service.update(99L, input);

        assertNull(result);
        verify(mapper, never()).updateById(any(ClassificationRuleEntity.class));
    }

    // ==================== delete ====================

    @Test
    void delete_走mapper() {
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    // ==================== reorder ====================

    @Test
    void reorder_按ids顺序设priority() {
        when(mapper.updateById(any(ClassificationRuleEntity.class))).thenReturn(1);

        service.reorder(List.of(10L, 5L, 8L));

        ArgumentCaptor<ClassificationRuleEntity> captor = ArgumentCaptor.forClass(ClassificationRuleEntity.class);
        verify(mapper, times(3)).updateById(captor.capture());
        List<ClassificationRuleEntity> updated = captor.getAllValues();

        assertEquals(10L, updated.get(0).getId());
        assertEquals(1, updated.get(0).getPriority());
        assertEquals(1L, updated.get(0).getUpdatedBy());

        assertEquals(5L, updated.get(1).getId());
        assertEquals(2, updated.get(1).getPriority());

        assertEquals(8L, updated.get(2).getId());
        assertEquals(3, updated.get(2).getPriority());
    }

    @Test
    void reorder_空列表不操作() {
        service.reorder(List.of());
        verify(mapper, never()).updateById(any(ClassificationRuleEntity.class));
    }

    // ==================== seedForNewTenant ====================

    @Test
    void seedForNewTenant_已有种子则跳过() {
        when(mapper.selectCount(any())).thenReturn(8L);

        int inserted = service.seedForNewTenant(1L);

        assertEquals(0, inserted);
        verify(mapper, never()).insert(any(ClassificationRuleEntity.class));
    }

    @Test
    void seedForNewTenant_新租户插入8条() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(ClassificationRuleEntity.class))).thenReturn(1);

        int inserted = service.seedForNewTenant(99L);

        assertEquals(8, inserted);
        verify(mapper, times(8)).insert(any(ClassificationRuleEntity.class));
    }

    @Test
    void seedForNewTenant_8条种子内容正确() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(ClassificationRuleEntity.class))).thenReturn(1);

        service.seedForNewTenant(1L);

        ArgumentCaptor<ClassificationRuleEntity> captor = ArgumentCaptor.forClass(ClassificationRuleEntity.class);
        verify(mapper, times(8)).insert(captor.capture());
        List<ClassificationRuleEntity> seeds = captor.getAllValues();

        // 第1条: 银行手续费
        assertEquals("银行手续费", seeds.get(0).getName());
        assertEquals("keyword_regex", seeds.get(0).getRuleType());
        assertEquals(1, seeds.get(0).getPriority());
        assertEquals(1L, seeds.get(0).getTenantId());
        assertEquals("手续费|工本费|年费|账户管理费", seeds.get(0).getPattern());
        assertEquals("description", seeds.get(0).getMatchField());
        assertEquals("out", seeds.get(0).getDirection());
        assertEquals("bank_fee", seeds.get(0).getClassification());
        assertTrue(seeds.get(0).getIsActive());
        assertEquals(0, seeds.get(0).getDeleted());
        assertEquals(1L, seeds.get(0).getCreatedBy());

        // 第2条: 利息收入
        assertEquals("利息收入", seeds.get(1).getName());
        assertEquals("interest_income", seeds.get(1).getClassification());
        assertEquals(2, seeds.get(1).getPriority());
        assertEquals("in", seeds.get(1).getDirection());

        // 第3条: 业务收款
        assertEquals("业务收款", seeds.get(2).getName());
        assertEquals("business_receipt", seeds.get(2).getClassification());
        assertEquals(3, seeds.get(2).getPriority());
        assertEquals("in", seeds.get(2).getDirection());
        assertEquals("货款", seeds.get(2).getPattern());

        // 第4条: 业务付款
        assertEquals("业务付款", seeds.get(3).getName());
        assertEquals("business_payment", seeds.get(3).getClassification());
        assertEquals(4, seeds.get(3).getPriority());
        assertEquals("out", seeds.get(3).getDirection());
        assertEquals("货款", seeds.get(3).getPattern());

        // 第5条: 内部转账
        assertEquals("内部转账", seeds.get(4).getName());
        assertEquals("internal_transfer", seeds.get(4).getClassification());
        assertEquals(5, seeds.get(4).getPriority());
        assertNull(seeds.get(4).getDirection());
        assertEquals("转账|转存|调拨|上划|下拨", seeds.get(4).getPattern());

        // 第6条: 税务缴费
        assertEquals("税务缴费", seeds.get(5).getName());
        assertEquals("tax_payment", seeds.get(5).getClassification());
        assertEquals(6, seeds.get(5).getPriority());
        assertEquals("out", seeds.get(5).getDirection());
        assertTrue(seeds.get(5).getPattern().contains("增值税"));

        // 第7条: 社保缴费
        assertEquals("社保缴费", seeds.get(6).getName());
        assertEquals("social_security", seeds.get(6).getClassification());
        assertEquals(7, seeds.get(6).getPriority());
        assertEquals("out", seeds.get(6).getDirection());

        // 第8条: 保险费用
        assertEquals("保险费用", seeds.get(7).getName());
        assertEquals("insurance_fee", seeds.get(7).getClassification());
        assertEquals(8, seeds.get(7).getPriority());
        assertEquals("out", seeds.get(7).getDirection());
    }

    // ==================== createSeed (private, 通过 seedForNewTenant 间接验证) ====================

    @Test
    void seedForNewTenant_种子共8条方向正确() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(ClassificationRuleEntity.class))).thenReturn(1);

        service.seedForNewTenant(1L);

        ArgumentCaptor<ClassificationRuleEntity> captor = ArgumentCaptor.forClass(ClassificationRuleEntity.class);
        verify(mapper, times(8)).insert(captor.capture());
        List<ClassificationRuleEntity> seeds = captor.getAllValues();

        // in: 利息收入, 业务收款
        assertEquals(2, seeds.stream().filter(s -> "in".equals(s.getDirection())).count());
        // out: 银行手续费, 业务付款, 税务缴费, 社保缴费, 保险费用
        assertEquals(5, seeds.stream().filter(s -> "out".equals(s.getDirection())).count());
        // null: 内部转账
        assertEquals(1, seeds.stream().filter(s -> s.getDirection() == null).count());

        // 所有种子已启用且未删除
        assertTrue(seeds.stream().allMatch(ClassificationRuleEntity::getIsActive));
        assertEquals(0, seeds.stream().filter(s -> s.getDeleted() != 0).count());
    }

    // ==================== match ====================

    private ClassificationRuleEntity rule(Long id, int priority, String name, String direction, String pattern, String classification) {
        ClassificationRuleEntity r = new ClassificationRuleEntity();
        r.setId(id);
        r.setTenantId(1L);
        r.setPriority(priority);
        r.setName(name);
        r.setRuleType("keyword_regex");
        r.setMatchField("description");
        r.setDirection(direction);
        r.setPattern(pattern);
        r.setClassification(classification);
        r.setIsActive(true);
        return r;
    }

    private List<ClassificationRuleEntity> seedRules() {
        return List.of(
                rule(1L, 1, "银行手续费", "out", "手续费|工本费|年费|账户管理费", "bank_fee"),
                rule(2L, 2, "利息收入", "in", "利息|结息|存款利息", "interest_income"),
                rule(3L, 3, "业务收款", "in", "货款", "business_receipt"),
                rule(4L, 4, "业务付款", "out", "货款", "business_payment"),
                rule(5L, 5, "内部转账", null, "转账|转存|调拨|上划|下拨", "internal_transfer"),
                rule(6L, 6, "税务缴费", "out", "税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花", "tax_payment"),
                rule(7L, 7, "社保缴费", "out", "社保|公积金|养老|医疗|失业|工伤|生育", "social_security"),
                rule(8L, 8, "保险费用", "out", "保险|保费|投保|财产险|责任险|雇主责任险|意外险", "insurance_fee")
        );
    }

    @Test
    void match_命中手续费() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("银行账户管理费扣款", "out", null);
        assertNotNull(result);
        assertEquals("银行手续费", result.getName());
        assertEquals("bank_fee", result.getClassification());
    }

    @Test
    void match_命中利息() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("存款结息", "in", null);
        assertNotNull(result);
        assertEquals("利息收入", result.getName());
        assertEquals("interest_income", result.getClassification());
    }

    @Test
    void match_方向过滤() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("存款结息", "out", null);
        assertNull(result);
    }

    @Test
    void match_未命中() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("XXXXX", "in", null);
        assertNull(result);
    }

    @Test
    void match_description为空() {
        assertNull(service.match(null, "in", null));
        assertNull(service.match("", "in", null));
        verify(mapper, never()).selectList(any());
    }
}
