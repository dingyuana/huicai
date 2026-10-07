package com.huicai.sme.cash.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.business.constant.BankClassification;
import com.huicai.base.business.entity.ClassificationRuleEntity;
import com.huicai.base.business.mapper.ClassificationRuleMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.cash.service.ClassificationRuleService;
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
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class ClassificationRuleServiceImpl implements ClassificationRuleService {

    private final ClassificationRuleMapper mapper;

    /**
     * 取当前企业上下文；缺失即抛错（fail-closed）。
     *
     * <p><b>为什么必须抛错而不是「不加过滤」</b>（P106 批次 1a-3 实测）：D-1 初版写成
     * {@code if (ctx != null) wrapper.eq(getEnterpriseId, ctx)}，并在注释里声称
     * 「取不到上下文等价于读空集合（过滤条件恒为 0 行，安全）」—— 这句注释与代码
     * <b>完全相反</b>：{@code ctx == null} 时整个 {@code eq} 被跳过，读路径退化成
     * <b>不加任何企业条件、返回全表</b>。注释自证安全反而是最危险的一档
     * （同 AGENTS §4.2 第 19 条：设计意图自洽 ≠ DB/代码认账）。
     *
     * <p>唯一例外是超级用户绕过 RLS 的连接（L2 Testcontainers），那里本就没有第二道防线。
     */
    private Long requireEnterpriseContext(String scene) {
        Long ctx = EnterpriseContextHolder.get();
        if (ctx == null) {
            throw new BusinessException("无当前企业上下文，无法" + scene);
        }
        return ctx;
    }

    @Override
    public IPage<ClassificationRuleEntity> page(Integer current, Integer size) {
        Page<ClassificationRuleEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        // P106 批次 1a-3（D-1）：tenantId 请求参数废弃，按当前企业上下文的 enterprise_id 过滤。
        Long ctx = requireEnterpriseContext("查询分类规则");
        LambdaQueryWrapper<ClassificationRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassificationRuleEntity::getEnterpriseId, ctx);
        wrapper.orderByAsc(ClassificationRuleEntity::getPriority);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public ClassificationRuleEntity getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public ClassificationRuleEntity create(ClassificationRuleEntity entity) {
        // P106 批次 1a-3（D-1）：tenantId 请求参数废弃。新写入行的 tenant_id 仍与
        // enterprise_id 一致取自当前上下文（审计自洽），直至 D-3 把列整个删除。
        // 注意：绝不回退硬编码 1L —— 那正是 P106 1a-1 在预付款上查出的「静默落企业 1」缺陷。
        if (entity.getTenantId() == null) {
            entity.setTenantId(requireEnterpriseContext("创建分类规则"));
        }
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
    public int seedForCurrentEnterprise() {
        // P106 批次 1a-3（D-1）：原 seedForNewTenant(tenantId) 改为按当前上下文企业生成种子。
        // 取不到上下文直接抛错（不许硬编企业 1 —— §0.5 教训）
        // 幂等: 已有种子则直接返回
        Long currentEnterprise = requireEnterpriseContext("生成分类规则种子");
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<ClassificationRuleEntity>()
                        .eq(ClassificationRuleEntity::getEnterpriseId, currentEnterprise)
        );
        if (count != null && count > 0) {
            return 0;
        }

        // 8 条种子规则（与 V20 migration 数据一致，V132 新增三级科目）
        ClassificationRuleEntity[] seeds = new ClassificationRuleEntity[]{
                createSeed(currentEnterprise, 1, "银行利息与手续费", "keyword_regex", "手续费|工本费|年费|账户管理费|利息|结息|存款利息", "description", null, BankClassification.BANK_INTEREST_FEE, null, null, "银行", "手续费与利息", "银行手续费"),
                createSeed(currentEnterprise, 2, "业务收款", "keyword_regex", "货款|收款|销售|回款|客户|应收|收入", "description", "in", BankClassification.BUSINESS_RECEIPT, null, null, "业务收款", "主营业务收入", "货款收入"),
                createSeed(currentEnterprise, 3, "业务付款", "keyword_regex", "货款|付款|采购|支付|供应商|应付|支出", "description", "out", BankClassification.BUSINESS_PAYMENT, null, null, "业务付款", "主营业务成本", "采购支出"),
                createSeed(currentEnterprise, 4, "内部转账", "keyword_regex", "转账|转存|调拨|上划|下拨", "description", null, BankClassification.INTERNAL_TRANSFER, null, null, "内部转账", "银行存款", "行内转账"),
                createSeed(currentEnterprise, 5, "税费扣缴", "keyword_regex", "税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花", "description", "out", BankClassification.TAX_WITHHOLDING, null, null, "税费扣缴", "应交税费", "增值税"),
                createSeed(currentEnterprise, 6, "薪酬与社保", "keyword_regex", "工资|薪酬|社保|公积金|养老|医疗|失业|工伤|生育|代扣|个税", "description", "out", BankClassification.SALARY_SOCIAL, null, null, "薪酬社保", "应付职工薪酬", "工资与社保"),
                createSeed(currentEnterprise, 7, "筹资与投资活动", "keyword_regex", "借款|还款|贷款|理财|投资|融资|分红|股本|债券", "description", null, BankClassification.FINANCING_INVEST, null, null, "筹资投资", "短期借款", "银行贷款"),
                createSeed(currentEnterprise, 8, "其它/待认领", "keyword_regex", "", "description", null, BankClassification.OTHER_UNKNOWN, null, null, "其他", null, null),
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

        // P106 批次 1a-3（D-1）：match() 原按 tenant_id 过滤，属同一缺陷类的读路径 ——
        // 两列一旦不一致（本项目预付款已出现过），会读到别的企业的规则，静默错分。
        Long ctx = requireEnterpriseContext("匹配分类规则");

        List<ClassificationRuleEntity> rules = mapper.selectList(
                new LambdaQueryWrapper<ClassificationRuleEntity>()
                        .eq(ClassificationRuleEntity::getEnterpriseId, ctx)
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

    private ClassificationRuleEntity createSeed(Long enterpriseId, int priority, String name, String ruleType,
                                                String pattern, String matchField, String direction,
                                                String classification, Long debitSubjectId, Long creditSubjectId,
                                                String subjectLevel1, String subjectLevel2, String subjectLevel3) {
        ClassificationRuleEntity entity = new ClassificationRuleEntity();
        entity.setTenantId(enterpriseId);
        entity.setEnterpriseId(enterpriseId);
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
        entity.setSubjectLevel1(subjectLevel1);
        entity.setSubjectLevel2(subjectLevel2);
        entity.setSubjectLevel3(subjectLevel3);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setDeleted(0);
        return entity;
    }
}
