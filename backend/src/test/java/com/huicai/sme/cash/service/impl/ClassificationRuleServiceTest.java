package com.huicai.sme.cash.service.impl;

import com.huicai.sme.cash.entity.ClassificationRuleEntity;
import com.huicai.sme.cash.mapper.ClassificationRuleMapper;
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
 * 分类规则 Service 纯单元测试 — 新8分类体系
 * <p>
 * 参考 VoucherEntryValidationTest 模式: 不启动 Spring, 仅测试纯业务逻辑.
 * 覆盖 create/update/delete/reorder/seedForNewTenant/match 方法.
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

        // 第1条: 银行利息与手续费 (direction=null)
        assertEquals("银行利息与手续费", seeds.get(0).getName());
        assertEquals("keyword_regex", seeds.get(0).getRuleType());
        assertEquals(1, seeds.get(0).getPriority());
        assertEquals(1L, seeds.get(0).getTenantId());
        assertEquals("手续费|工本费|年费|账户管理费|利息|结息|存款利息", seeds.get(0).getPattern());
        assertEquals("description", seeds.get(0).getMatchField());
        assertNull(seeds.get(0).getDirection());
        assertEquals("bank_interest_fee", seeds.get(0).getClassification());
        assertTrue(seeds.get(0).getIsActive());
        assertEquals(0, seeds.get(0).getDeleted());
        assertEquals(1L, seeds.get(0).getCreatedBy());

        // 第2条: 业务收款 (direction=in)
        assertEquals("业务收款", seeds.get(1).getName());
        assertEquals("business_receipt", seeds.get(1).getClassification());
        assertEquals(2, seeds.get(1).getPriority());
        assertEquals("in", seeds.get(1).getDirection());
        assertTrue(seeds.get(1).getPattern().contains("货款"));

        // 第3条: 业务付款 (direction=out)
        assertEquals("业务付款", seeds.get(2).getName());
        assertEquals("business_payment", seeds.get(2).getClassification());
        assertEquals(3, seeds.get(2).getPriority());
        assertEquals("out", seeds.get(2).getDirection());
        assertTrue(seeds.get(2).getPattern().contains("货款"));

        // 第4条: 内部转账 (direction=null)
        assertEquals("内部转账", seeds.get(3).getName());
        assertEquals("internal_transfer", seeds.get(3).getClassification());
        assertEquals(4, seeds.get(3).getPriority());
        assertNull(seeds.get(3).getDirection());
        assertEquals("转账|转存|调拨|上划|下拨", seeds.get(3).getPattern());

        // 第5条: 税费扣缴 (direction=out)
        assertEquals("税费扣缴", seeds.get(4).getName());
        assertEquals("tax_withholding", seeds.get(4).getClassification());
        assertEquals(5, seeds.get(4).getPriority());
        assertEquals("out", seeds.get(4).getDirection());
        assertTrue(seeds.get(4).getPattern().contains("增值税"));

        // 第6条: 薪酬与社保 (direction=out)
        assertEquals("薪酬与社保", seeds.get(5).getName());
        assertEquals("salary_social", seeds.get(5).getClassification());
        assertEquals(6, seeds.get(5).getPriority());
        assertEquals("out", seeds.get(5).getDirection());
        assertTrue(seeds.get(5).getPattern().contains("工资"));
        assertTrue(seeds.get(5).getPattern().contains("社保"));

        // 第7条: 筹资与投资活动 (direction=null)
        assertEquals("筹资与投资活动", seeds.get(6).getName());
        assertEquals("financing_invest", seeds.get(6).getClassification());
        assertEquals(7, seeds.get(6).getPriority());
        assertNull(seeds.get(6).getDirection());
        assertTrue(seeds.get(6).getPattern().contains("借款"));
        assertTrue(seeds.get(6).getPattern().contains("投资"));

        // 第8条: 其它/待认领 (direction=null)
        assertEquals("其它/待认领", seeds.get(7).getName());
        assertEquals("other_unknown", seeds.get(7).getClassification());
        assertEquals(8, seeds.get(7).getPriority());
        assertNull(seeds.get(7).getDirection());
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

        // in: 业务收款
        assertEquals(1, seeds.stream().filter(s -> "in".equals(s.getDirection())).count());
        // out: 业务付款, 税费扣缴, 薪酬与社保
        assertEquals(3, seeds.stream().filter(s -> "out".equals(s.getDirection())).count());
        // null: 银行利息与手续费, 内部转账, 筹资与投资活动, 其它/待认领
        assertEquals(4, seeds.stream().filter(s -> s.getDirection() == null).count());

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
                rule(1L, 1, "银行利息与手续费", null, "手续费|工本费|年费|账户管理费|利息|结息|存款利息", "bank_interest_fee"),
                rule(2L, 2, "业务收款", "in", "货款|收款|销售|回款|客户|应收|收入", "business_receipt"),
                rule(3L, 3, "业务付款", "out", "货款|付款|采购|支付|供应商|应付|支出", "business_payment"),
                rule(4L, 4, "内部转账", null, "转账|转存|调拨|上划|下拨", "internal_transfer"),
                rule(5L, 5, "税费扣缴", "out", "税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花", "tax_withholding"),
                rule(6L, 6, "薪酬与社保", "out", "工资|薪酬|社保|公积金|养老|医疗|失业|工伤|生育|代扣|个税", "salary_social"),
                rule(7L, 7, "筹资与投资活动", null, "借款|还款|贷款|理财|投资|融资|分红|股本|债券", "financing_invest"),
                rule(8L, 8, "其它/待认领", null, "", "other_unknown")
        );
    }

    @Test
    void match_命中手续费() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("银行账户管理费扣款", "out", null);
        assertNotNull(result);
        assertEquals("银行利息与手续费", result.getName());
        assertEquals("bank_interest_fee", result.getClassification());
    }

    @Test
    void match_命中利息_in方向() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("存款结息", "in", null);
        assertNotNull(result);
        assertEquals("银行利息与手续费", result.getName());
        assertEquals("bank_interest_fee", result.getClassification());
    }

    @Test
    void match_命中手续费_out方向() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("转账手续费", "out", null);
        assertNotNull(result);
        assertEquals("银行利息与手续费", result.getName());
        assertEquals("bank_interest_fee", result.getClassification());
    }

    @Test
    void match_命中税费() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("缴纳增值税", "out", null);
        assertNotNull(result);
        assertEquals("税费扣缴", result.getName());
        assertEquals("tax_withholding", result.getClassification());
    }

    @Test
    void match_命中工资() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("发放5月工资", "out", null);
        assertNotNull(result);
        assertEquals("薪酬与社保", result.getName());
        assertEquals("salary_social", result.getClassification());
    }

    @Test
    void match_命中社保() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("缴社保", "out", null);
        assertNotNull(result);
        assertEquals("薪酬与社保", result.getName());
        assertEquals("salary_social", result.getClassification());
    }

    @Test
    void match_命中借款() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        ClassificationRuleEntity result = service.match("收到银行借款", "in", null);
        assertNotNull(result);
        assertEquals("筹资与投资活动", result.getName());
        assertEquals("financing_invest", result.getClassification());
    }

    @Test
    void match_方向过滤() {
        when(mapper.selectList(any())).thenReturn(seedRules());
        // 工资需要out方向，传in方向应该不命中工资规则（会命中不限方向的规则或返回null）
        ClassificationRuleEntity result = service.match("发放5月工资", "in", null);
        // 应该返回null，因为第6条要求out方向，其他关键词不匹配in方向的业务收款
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
