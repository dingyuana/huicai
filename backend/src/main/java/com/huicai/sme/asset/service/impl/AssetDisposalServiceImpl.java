package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetDisposalMapper;
import com.huicai.sme.asset.service.AssetDisposalService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class AssetDisposalServiceImpl implements AssetDisposalService {

    /**
     * 取当前制单人 —— 有登录态用真实用户。
     *
     * <p><b>SPEC-P119</b>：P117 把 created_by 修成真实落库列后，本类多条创建
     * 路径此前无人写入该列，导致制审分离校验恒不触发（恒绿门禁形态）。
     *
     * <p><b>Q2 裁定：无登录态返回 null，不写兜底常量。</b>
     * Excel 导入 / 定时任务 / 系统初始化本就没有「录入人」概念，
     * 填 DEFAULT_USER_ID 等于凭空造一个假用户（与 P118 清理的问题同型）。
     * 代价是这些单据永远无法做制审分离校验 —— 属已登记的已知局限，
     * 校验对 created_by IS NULL 放行，与凭证层/业务单据层口径一致。
     */
    private Long currentOperatorId() {
        try {
            return com.huicai.base.system.util.SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 制审分离：制单人不能审批自己提交的单据。
     *
     * <p><b>SPEC-P119</b>：本校验此前完全不存在。与 P116 已补的凭证层、
     * 业务单据层口径逐字对齐 —— 含 {@code created_by} 为 NULL 时放行。
     *
     * <p>NULL 放行是刻意为之：Excel 导入 / 定时任务 / 系统初始化无登录态，
     * 本就没有「录入人」概念（Q2 裁定），此时若反过来说「他自审」是错的。
     * 代价是这些单据永远无法做制审分离校验，属已登记的已知局限。
     */
    private void assertNotSelfApprove(Long userId, Long createdBy, String docName) {
        if (userId != null && createdBy != null && userId.equals(createdBy)) {
            throw BusinessException.badRequest(
                    "制单人不能审批自己提交的" + docName + "（制审分离）");
        }
    }

    private final AssetDisposalMapper mapper;
    private final AssetCardMapper cardMapper;

    @Override
    public IPage<AssetDisposalEntity> pageQuery(String status, Integer current, Integer size) {
        Page<AssetDisposalEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AssetDisposalEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AssetDisposalEntity::getStatus, status);
        }
        wrapper.orderByDesc(AssetDisposalEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public AssetDisposalEntity getById(Long id) {
        AssetDisposalEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("资产处置单不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDisposalEntity create(AssetDisposalEntity entity) {
        AssetCardEntity card = cardMapper.selectById(entity.getAssetId());
        if (card == null) {
            throw new BusinessException("资产不存在");
        }
        if (!"IN_USE".equals(card.getStatus())) {
            throw new BusinessException("仅可在用资产可处置");
        }
        // 从卡片同步数据
        entity.setOriginalValue(card.getOriginalValue());
        entity.setAccumulatedDepreciation(card.getAccumulatedDepreciation());
        entity.setNetValue(card.getNetValue());
        // 计算处置损益
        BigDecimal income = entity.getDisposalIncome() == null ? BigDecimal.ZERO : entity.getDisposalIncome();
        BigDecimal expense = entity.getDisposalExpense() == null ? BigDecimal.ZERO : entity.getDisposalExpense();
        BigDecimal gainLoss = income.subtract(entity.getNetValue()).subtract(expense);
        entity.setGainLoss(gainLoss);
        // P0-fix: 原为 `if (entity.getStatus() == null) entity.setStatus("DRAFT")`。
        // AssetDisposalController#create 直接接 @RequestBody AssetDisposalEntity（违反铁律 #13），
        // 只在 null 时兜底 ⇒ 客户端显式传 status 会被照单全收。
        // chk_disposal_status 允许 DRAFT/APPROVED/VOUCHERED，故 POST status=APPROVED 可
        // **一步跳过** approve() 这次人工审批（违反铁律 #1：人是唯一审核主体）。
        // 与 TaxServiceImpl.createOutput / createDeclaration 同一写法，属第 3 处。
        // 改为**无条件**强制 DRAFT —— 创建态是唯一合法起点。
        entity.setStatus("DRAFT");
        entity.setCreatedBy(currentOperatorId());
        mapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDisposalEntity approve(Long id) {
        AssetDisposalEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可审批");
        }
        assertNotSelfApprove(currentOperatorId(), entity.getCreatedBy(), "资产处置单");
        entity.setStatus("APPROVED");
        mapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AssetDisposalEntity entity = getById(id);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        mapper.deleteById(id);
    }
}
