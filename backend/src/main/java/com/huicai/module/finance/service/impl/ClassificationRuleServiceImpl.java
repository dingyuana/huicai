package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.finance.constant.BankClassification;
import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.ClassificationRuleMapper;
import com.huicai.module.finance.service.ClassificationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分类规则 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ClassificationRuleServiceImpl implements ClassificationRuleService {

    private final ClassificationRuleMapper mapper;

    @Override
    public IPage<ClassificationRuleEntity> page(Long tenantId, Integer current, Integer size) {
        Page<ClassificationRuleEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<ClassificationRuleEntity> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(ClassificationRuleEntity::getTenantId, tenantId);
        }
        wrapper.orderByAsc(ClassificationRuleEntity::getPriority);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public ClassificationRuleEntity getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public ClassificationRuleEntity create(ClassificationRuleEntity entity) {
        if (entity.getTenantId() == null) entity.setTenantId(1L);
        if (entity.getRuleType() == null) entity.setRuleType("keyword_regex");
        if (entity.getMatchField() == null) entity.setMatchField("description");
        if (entity.getPriority() == null) entity.setPriority(0);
        if (entity.getIsActive() == null) entity.setIsActive(true);
        if (entity.getDeleted() == null) entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public ClassificationRuleEntity update(Long id, ClassificationRuleEntity entity) {
        ClassificationRuleEntity existing = mapper.selectById(id);
        if (existing == null) {
            return null;
        }
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(1L);
        mapper.updateById(entity);
        return mapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorder(List<Long> ids) {
        AtomicInteger priority = new AtomicInteger(1);
        for (Long id : ids) {
            ClassificationRuleEntity entity = new ClassificationRuleEntity();
            entity.setId(id);
            entity.setPriority(priority.getAndIncrement());
            entity.setUpdatedBy(1L);
            mapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int seedForNewTenant(Long tenantId) {
        // 幂等: 已有种子则直接返回
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<ClassificationRuleEntity>()
                        .eq(ClassificationRuleEntity::getTenantId, tenantId)
        );
        if (count != null && count > 0) {
            return 0;
        }

        // 8 条种子规则（与 V20 migration 数据一致）
        ClassificationRuleEntity[] seeds = new ClassificationRuleEntity[]{
                createSeed(tenantId, 1, "银行利息与手续费", "keyword_regex", "手续费|工本费|年费|账户管理费|利息|结息|存款利息", "description", null, BankClassification.BANK_INTEREST_FEE, null, null),
                createSeed(tenantId, 2, "业务收款", "keyword_regex", "货款|收款|销售|回款|客户|应收|收入", "description", "in", BankClassification.BUSINESS_RECEIPT, null, null),
                createSeed(tenantId, 3, "业务付款", "keyword_regex", "货款|付款|采购|支付|供应商|应付|支出", "description", "out", BankClassification.BUSINESS_PAYMENT, null, null),
                createSeed(tenantId, 4, "内部转账", "keyword_regex", "转账|转存|调拨|上划|下拨", "description", null, BankClassification.INTERNAL_TRANSFER, null, null),
                createSeed(tenantId, 5, "税费扣缴", "keyword_regex", "税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花", "description", "out", BankClassification.TAX_WITHHOLDING, null, null),
                createSeed(tenantId, 6, "薪酬与社保", "keyword_regex", "工资|薪酬|社保|公积金|养老|医疗|失业|工伤|生育|代扣|个税", "description", "out", BankClassification.SALARY_SOCIAL, null, null),
                createSeed(tenantId, 7, "筹资与投资活动", "keyword_regex", "借款|还款|贷款|理财|投资|融资|分红|股本|债券", "description", null, BankClassification.FINANCING_INVEST, null, null),
                createSeed(tenantId, 8, "其它/待认领", "keyword_regex", "", "description", null, BankClassification.OTHER_UNKNOWN, null, null),
        };

        int inserted = 0;
        for (ClassificationRuleEntity seed : seeds) {
            mapper.insert(seed);
            inserted++;
        }
        return inserted;
    }

    @Override
    public ClassificationRuleEntity match(String description, String direction, String counterparty) {
        if (StrUtil.isBlank(description) && StrUtil.isBlank(counterparty)) return null;

        // @TODO P1 阶段硬编码 tenantId=1L, 后续从 SecurityContext 或 statement 反查
        Long tenantId = 1L;

        List<ClassificationRuleEntity> rules = mapper.selectList(
                new LambdaQueryWrapper<ClassificationRuleEntity>()
                        .eq(ClassificationRuleEntity::getTenantId, tenantId)
                        .eq(ClassificationRuleEntity::getIsActive, true)
                        .orderByAsc(ClassificationRuleEntity::getPriority)
        );

        for (ClassificationRuleEntity rule : rules) {
            if (!matchDirection(rule, direction)) continue;
            if (!matchByRule(rule, description, counterparty)) continue;
            return rule;
        }
        return null;
    }

    private boolean matchDirection(ClassificationRuleEntity rule, String direction) {
        if (StrUtil.isBlank(rule.getDirection())) return true;
        return rule.getDirection().equalsIgnoreCase(direction);
    }

    /**
     * 按规则的 matchField + ruleType 执行匹配.
     * matchField 决定匹配来源（摘要/对方户名）, ruleType 决定匹配方式（包含/正则/对方匹配）
     */
    private boolean matchByRule(ClassificationRuleEntity rule, String description, String counterparty) {
        if (StrUtil.isBlank(rule.getPattern())) return false;

        String textToMatch = "counterparty".equals(rule.getMatchField()) ? counterparty : description;
        if (StrUtil.isBlank(textToMatch)) return false;

        switch (rule.getRuleType()) {
            case "keyword":
            case "counterparty_match":
                return textToMatch.contains(rule.getPattern().trim());
            default: // keyword_regex
                String[] keywords = rule.getPattern().split("\\|");
                for (String kw : keywords) {
                    if (StrUtil.isNotBlank(kw) && textToMatch.contains(kw.trim())) {
                        return true;
                    }
                }
                return false;
        }
    }

    private ClassificationRuleEntity createSeed(Long tenantId, int priority, String name, String ruleType,
                                                String pattern, String matchField, String direction,
                                                String classification, Long debitSubjectId, Long creditSubjectId) {
        ClassificationRuleEntity entity = new ClassificationRuleEntity();
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setRuleType(ruleType);
        entity.setPattern(pattern);
        entity.setMatchField(matchField);
        entity.setDirection(direction);
        entity.setClassification(classification);
        entity.setPriority(priority);
        entity.setIsActive(true);
        entity.setDebitSubjectId(debitSubjectId);
        entity.setCreditSubjectId(creditSubjectId);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setDeleted(0);
        return entity;
    }
}
