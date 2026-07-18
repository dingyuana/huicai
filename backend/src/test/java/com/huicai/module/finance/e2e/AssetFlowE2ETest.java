package com.huicai.module.finance.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.asset.entity.AssetCardEntity;
import com.huicai.module.asset.entity.AssetCategoryEntity;
import com.huicai.module.asset.entity.AssetDisposalEntity;
import com.huicai.module.asset.mapper.AssetCardMapper;
import com.huicai.module.asset.mapper.AssetCategoryMapper;
import com.huicai.module.asset.mapper.AssetDisposalMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资产全生命周期 E2E 测试.
 * <p>
 * 模拟: 资产卡片创建(DRAFT) → 启用(IN_USE) → 折旧 → 处置(DISPOSED)
 * 一个 @Test 方法完成完整流程，使用 @Transactional 自动回滚清理数据.
 */
public class AssetFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private AssetCardMapper assetCardMapper;

    @Autowired
    private AssetCategoryMapper assetCategoryMapper;

    @Autowired
    private AssetDisposalMapper assetDisposalMapper;

    @Test
    void fullAssetLifecycle_endToEnd_shouldCompleteSuccessfully() {
        // ==================== Step 0: 创建资产类别（外键依赖） ====================
        AssetCategoryEntity category = new AssetCategoryEntity();
        category.setCode("E2E-CAT-001");
        category.setName("E2E测试设备类");
        category.setLevel(1);
        category.setDepreciationMethod("STRAIGHT_LINE");
        category.setUsefulLife(5);
        category.setResidualRate(new BigDecimal("0.05"));
        category.setDeleted(0);
        assetCategoryMapper.insert(category);
        assertNotNull(category.getId(), "资产类别创建后应有 ID");
        Long categoryId = category.getId();

        // ==================== Step 1: 创建资产卡片 (DRAFT) ====================
        BigDecimal originalValue = new BigDecimal("120000.00");
        BigDecimal residualRate = new BigDecimal("0.05");
        BigDecimal residualValue = originalValue.multiply(residualRate).setScale(2, RoundingMode.HALF_UP);
        int usefulLife = 5; // 5年
        // 月折旧额 = (原值 - 残值) / (使用年限 * 12)
        BigDecimal monthlyDepreciation = originalValue.subtract(residualValue)
                .divide(BigDecimal.valueOf(usefulLife * 12L), 2, RoundingMode.HALF_UP);

        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-E2E-" + System.currentTimeMillis());
        card.setAssetName("E2E测试生产设备");
        card.setCategoryId(categoryId);
        card.setSpec("E2E-SPEC-001");
        card.setDeptId(101L);
        card.setCustodianId(1L);
        card.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        card.setOriginalValue(originalValue);
        card.setResidualValue(residualValue);
        card.setUsefulLife(usefulLife);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("DRAFT");
        card.setLocation("E2E测试车间A");
        card.setSerialNo("SN-E2E-001");
        card.setRemark("E2E全流程测试资产");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(originalValue);
        card.setCreatedBy(1L);
        card.setDeleted(0);
        assetCardMapper.insert(card);

        assertNotNull(card.getId(), "资产卡片创建后应有 ID");
        assertEquals("DRAFT", card.getStatus(), "新建资产卡片状态应为 DRAFT");

        // 验证数据库持久化
        AssetCardEntity savedCard = assetCardMapper.selectById(card.getId());
        assertNotNull(savedCard, "资产卡片应从数据库可查询");
        assertEquals(card.getAssetCode(), savedCard.getAssetCode());
        assertEquals(card.getAssetName(), savedCard.getAssetName());
        assertEquals(0, originalValue.compareTo(savedCard.getOriginalValue()), "原值应正确");
        assertEquals(0, residualValue.compareTo(savedCard.getResidualValue()), "残值应正确");
        assertEquals(usefulLife, savedCard.getUsefulLife(), "使用年限应正确");
        assertEquals("DRAFT", savedCard.getStatus(), "数据库状态应为 DRAFT");

        // ==================== Step 2: 启用资产卡片 (DRAFT → IN_USE) ====================
        savedCard.setStatus("IN_USE");
        int updateRows = assetCardMapper.updateById(savedCard);
        assertEquals(1, updateRows, "启用更新应影响 1 行");

        // 验证数据库持久化
        AssetCardEntity inUseCard = assetCardMapper.selectById(card.getId());
        assertNotNull(inUseCard, "启用后资产卡片应可查询");
        assertEquals("IN_USE", inUseCard.getStatus(), "启用后状态应为 IN_USE");
        assertEquals(0, originalValue.compareTo(inUseCard.getNetValue()), "启用时净值应等于原值");
        assertEquals(0, BigDecimal.ZERO.compareTo(inUseCard.getAccumulatedDepreciation()), "启用时累计折旧应为 0");

        // ==================== Step 3: 计提折旧 ====================
        // 模拟第一期折旧（2026年1月）
        BigDecimal depAmount1 = monthlyDepreciation;
        int depRows1 = assetCardMapper.accumulateDepreciation(
                card.getId(), depAmount1, "202601");
        assertEquals(1, depRows1, "第一期折旧应影响 1 行");

        AssetCardEntity afterDep1 = assetCardMapper.selectById(card.getId());
        assertEquals(0, depAmount1.compareTo(afterDep1.getAccumulatedDepreciation()),
                "第一期折旧后累计折旧应为 " + depAmount1);
        BigDecimal expectedNetValue1 = originalValue.subtract(depAmount1);
        assertEquals(0, expectedNetValue1.compareTo(afterDep1.getNetValue()),
                "第一期折旧后净值应为 " + expectedNetValue1);
        assertEquals("202601", afterDep1.getLastDepreciationPeriod(), "最后折旧期间应为 202601");

        // 模拟第二期折旧（2026年2月）
        BigDecimal depAmount2 = monthlyDepreciation;
        int depRows2 = assetCardMapper.accumulateDepreciation(
                card.getId(), depAmount2, "202602");
        assertEquals(1, depRows2, "第二期折旧应影响 1 行");

        AssetCardEntity afterDep2 = assetCardMapper.selectById(card.getId());
        BigDecimal expectedAccumulated2 = depAmount1.add(depAmount2);
        assertEquals(0, expectedAccumulated2.compareTo(afterDep2.getAccumulatedDepreciation()),
                "第二期折旧后累计折旧应为 " + expectedAccumulated2);
        BigDecimal expectedNetValue2 = originalValue.subtract(expectedAccumulated2);
        assertEquals(0, expectedNetValue2.compareTo(afterDep2.getNetValue()),
                "第二期折旧后净值应为 " + expectedNetValue2);
        assertEquals("202602", afterDep2.getLastDepreciationPeriod(), "最后折旧期间应为 202602");

        // ==================== Step 4: 创建资产处置记录 ====================
        BigDecimal disposalIncome = new BigDecimal("95000.00");
        BigDecimal disposalExpense = new BigDecimal("2000.00");
        // 处置损益 = 处置收入 - 处置费用 - 资产净值
        BigDecimal gainLoss = disposalIncome.subtract(disposalExpense).subtract(afterDep2.getNetValue());

        AssetDisposalEntity disposal = new AssetDisposalEntity();
        disposal.setDisposalNo("DSP-E2E-" + System.currentTimeMillis());
        disposal.setAssetId(card.getId());
        disposal.setDisposalType("SALE");
        disposal.setDisposalDate(LocalDate.of(2026, 3, 15));
        disposal.setPeriod("202603");
        disposal.setOriginalValue(afterDep2.getOriginalValue());
        disposal.setAccumulatedDepreciation(afterDep2.getAccumulatedDepreciation());
        disposal.setNetValue(afterDep2.getNetValue());
        disposal.setDisposalIncome(disposalIncome);
        disposal.setDisposalExpense(disposalExpense);
        disposal.setGainLoss(gainLoss);
        disposal.setStatus("PENDING_APPROVAL");
        disposal.setCreatedBy(1L);
        disposal.setDeleted(0);
        assetDisposalMapper.insert(disposal);

        assertNotNull(disposal.getId(), "处置记录创建后应有 ID");
        assertEquals("PENDING_APPROVAL", disposal.getStatus(), "处置记录状态应为 PENDING_APPROVAL");

        // 验证数据库持久化
        AssetDisposalEntity savedDisposal = assetDisposalMapper.selectById(disposal.getId());
        assertNotNull(savedDisposal, "处置记录应从数据库可查询");
        assertEquals(disposal.getDisposalNo(), savedDisposal.getDisposalNo());
        assertEquals(card.getId(), savedDisposal.getAssetId(), "处置记录的资产 ID 应匹配");
        assertEquals(0, afterDep2.getOriginalValue().compareTo(savedDisposal.getOriginalValue()), "处置原值应匹配");
        assertEquals(0, afterDep2.getAccumulatedDepreciation().compareTo(savedDisposal.getAccumulatedDepreciation()),
                "处置累计折旧应匹配");
        assertEquals(0, afterDep2.getNetValue().compareTo(savedDisposal.getNetValue()), "处置净值应匹配");
        assertEquals(0, gainLoss.compareTo(savedDisposal.getGainLoss()), "处置损益应正确");

        // ==================== Step 5: 处置资产卡片 (IN_USE → DISPOSED) ====================
        afterDep2.setStatus("DISPOSED");
        afterDep2.setRemark("E2E测试-已出售处置");
        int disposeRows = assetCardMapper.updateById(afterDep2);
        assertEquals(1, disposeRows, "处置更新应影响 1 行");

        AssetCardEntity disposedCard = assetCardMapper.selectById(card.getId());
        assertNotNull(disposedCard, "处置后资产卡片应可查询");
        assertEquals("DISPOSED", disposedCard.getStatus(), "处置后资产状态应为 DISPOSED");

        // ==================== 最终数据完整性验证 ====================
        // 验证资产卡片最终状态
        AssetCardEntity finalCard = assetCardMapper.selectById(card.getId());
        assertNotNull(finalCard);
        assertEquals("DISPOSED", finalCard.getStatus(), "最终状态应为 DISPOSED");
        assertEquals("E2E测试-已出售处置", finalCard.getRemark());
        assertTrue(finalCard.getAssetCode().startsWith("FA-E2E"), "资产编号前缀应一致");

        // 验证资产类别仍存在
        AssetCategoryEntity finalCategory = assetCategoryMapper.selectById(categoryId);
        assertNotNull(finalCategory, "资产类别应仍存在");
        assertEquals("E2E测试设备类", finalCategory.getName());

        // 验证处置记录完整
        AssetDisposalEntity finalDisposal = assetDisposalMapper.selectById(disposal.getId());
        assertNotNull(finalDisposal, "处置记录应仍存在");
        assertEquals("PENDING_APPROVAL", finalDisposal.getStatus());
        assertEquals(card.getId(), finalDisposal.getAssetId());

        // 验证处置金额关系: 处置损益 = 处置收入 - 处置费用 - 净值
        BigDecimal calculatedGainLoss = finalDisposal.getDisposalIncome()
                .subtract(finalDisposal.getDisposalExpense())
                .subtract(finalDisposal.getNetValue());
        assertEquals(0, calculatedGainLoss.compareTo(finalDisposal.getGainLoss()),
                "处置损益计算应一致: 收入 - 费用 - 净值 = 损益");

        // 验证资产卡片金额与处置记录金额一致
        assertEquals(0, finalCard.getOriginalValue().compareTo(finalDisposal.getOriginalValue()),
                "资产卡片原值应与处置记录原值一致");
        assertEquals(0, finalCard.getAccumulatedDepreciation().compareTo(finalDisposal.getAccumulatedDepreciation()),
                "资产卡片累计折旧应与处置记录累计折旧一致");
        assertEquals(0, finalCard.getNetValue().compareTo(finalDisposal.getNetValue()),
                "资产卡片净值应与处置记录净值一致");
    }
}