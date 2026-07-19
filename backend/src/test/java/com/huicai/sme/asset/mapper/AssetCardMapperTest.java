package com.huicai.sme.asset.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetCardMapper 真实 DB 测试
 *
 * 验证：资产卡片 Mapper 的 CRUD 操作与数据库约束正确性
 * 可发现 Mock 测试无法发现的问题：
 * - status 字段 check constraint 枚举值校验
 * - category_id 外键约束
 * - asset_code 唯一约束
 * - NUMERIC(18,2) 精度
 */
public class AssetCardMapperTest extends AbstractMapperTest {

    @Autowired
    private AssetCardMapper assetCardMapper;

    @Autowired
    private AssetCategoryMapper assetCategoryMapper;

    private Long testCategoryId;

    @BeforeEach
    void setupTestData() {
        // 创建测试资产类别（FK 依赖）
        AssetCategoryEntity category = new AssetCategoryEntity();
        category.setCode("TEST-CAT-001");
        category.setName("测试设备类");
        category.setLevel(1);
        category.setDepreciationMethod("STRAIGHT_LINE");
        category.setUsefulLife(5);
        category.setResidualRate(new BigDecimal("0.05"));
        category.setDeleted(0);
        assetCategoryMapper.insert(category);
        testCategoryId = category.getId();
    }

    /**
     * 场景 1：插入测试
     * 验证：所有必填字段可正确插入，主键自动生成
     */
    @Test
    void insert_shouldReturnId() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-TEST-001");
        card.setAssetName("测试办公电脑");
        card.setCategoryId(testCategoryId);
        card.setAcquisitionDate(LocalDate.of(2026, 1, 15));
        card.setOriginalValue(new BigDecimal("12000.00"));
        card.setResidualValue(new BigDecimal("600.00"));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("12000.00"));
        card.setCreatedBy(1L);
        card.setDeleted(0);

        int rows = assetCardMapper.insert(card);

        assertEquals(1, rows);
        assertNotNull(card.getId());
    }

    /**
     * 场景 2：根据 ID 查询测试
     * 验证：插入的数据可正确查询，金额精度无丢失
     */
    @Test
    void selectById_shouldReturnCorrectData() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-TEST-002");
        card.setAssetName("测试服务器");
        card.setCategoryId(testCategoryId);
        card.setAcquisitionDate(LocalDate.of(2026, 3, 1));
        card.setOriginalValue(new BigDecimal("50000.00"));
        card.setResidualValue(new BigDecimal("2500.00"));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("50000.00"));
        card.setLocation("机房A");
        card.setRemark("核心业务服务器");
        card.setCreatedBy(1L);
        card.setDeleted(0);
        assetCardMapper.insert(card);

        AssetCardEntity found = assetCardMapper.selectById(card.getId());

        assertNotNull(found);
        assertEquals("FA-TEST-002", found.getAssetCode());
        assertEquals("测试服务器", found.getAssetName());
        assertEquals(testCategoryId, found.getCategoryId());
        assertEquals(0, new BigDecimal("50000.00").compareTo(found.getOriginalValue()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(found.getResidualValue()));
        assertEquals(5, found.getUsefulLife());
        assertEquals("IN_USE", found.getStatus());
        assertEquals("机房A", found.getLocation());
        assertEquals("核心业务服务器", found.getRemark());
    }

    /**
     * 场景 3：更新测试
     * 验证：状态和金额可正确更新
     */
    @Test
    void updateById_shouldUpdateCorrectly() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-TEST-003");
        card.setAssetName("测试打印机");
        card.setCategoryId(testCategoryId);
        card.setAcquisitionDate(LocalDate.of(2026, 2, 1));
        card.setOriginalValue(new BigDecimal("8000.00"));
        card.setResidualValue(new BigDecimal("400.00"));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("8000.00"));
        card.setCreatedBy(1L);
        card.setDeleted(0);
        assetCardMapper.insert(card);

        // 更新：资产报废
        card.setStatus("DISPOSED");
        card.setRemark("已报废处理");
        int rows = assetCardMapper.updateById(card);

        assertEquals(1, rows);
        AssetCardEntity updated = assetCardMapper.selectById(card.getId());
        assertEquals("DISPOSED", updated.getStatus());
        assertEquals("已报废处理", updated.getRemark());
    }

    /**
     * 场景 4：删除测试
     * 验证：记录可正确逻辑删除（@TableLogic）
     */
    @Test
    void deleteById_shouldSoftDeleteCorrectly() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-TEST-004");
        card.setAssetName("测试办公桌");
        card.setCategoryId(testCategoryId);
        card.setAcquisitionDate(LocalDate.of(2026, 4, 1));
        card.setOriginalValue(new BigDecimal("2000.00"));
        card.setResidualValue(BigDecimal.ZERO);
        card.setUsefulLife(10);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("2000.00"));
        card.setCreatedBy(1L);
        card.setDeleted(0);
        assetCardMapper.insert(card);

        int rows = assetCardMapper.deleteById(card.getId());

        assertEquals(1, rows);
        AssetCardEntity deleted = assetCardMapper.selectById(card.getId());
        assertNull(deleted);  // @TableLogic 自动过滤
    }

    /**
     * 场景 5：自定义查询测试
     * 验证：selectRecent 自定义 SQL 可正确执行
     */
    @Test
    void selectRecent_shouldReturnCorrectData() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-TEST-005");
        card.setAssetName("测试投影仪");
        card.setCategoryId(testCategoryId);
        card.setAcquisitionDate(LocalDate.of(2026, 5, 1));
        card.setOriginalValue(new BigDecimal("15000.00"));
        card.setResidualValue(BigDecimal.ZERO);
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("15000.00"));
        card.setCreatedBy(1L);
        card.setDeleted(0);
        assetCardMapper.insert(card);

        List<Map<String, Object>> recent = assetCardMapper.selectRecent(10);

        assertNotNull(recent);
        assertFalse(recent.isEmpty());
        assertTrue(recent.stream().anyMatch(m ->
                "FA-TEST-005".equals(m.get("asset_code"))));
    }

    /**
     * 场景 6：折旧累积测试
     * 验证：accumulateDepreciation 自定义 SQL 可正确更新
     */
    @Test
    void accumulateDepreciation_shouldUpdateCorrectly() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-TEST-006");
        card.setAssetName("测试折旧资产");
        card.setCategoryId(testCategoryId);
        card.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        card.setOriginalValue(new BigDecimal("60000.00"));
        card.setResidualValue(BigDecimal.ZERO);
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("60000.00"));
        card.setCreatedBy(1L);
        card.setDeleted(0);
        assetCardMapper.insert(card);

        // 计提第一个月折旧 1000.00
        int rows = assetCardMapper.accumulateDepreciation(
                card.getId(), new BigDecimal("1000.00"), "202602");

        assertEquals(1, rows);
        AssetCardEntity updated = assetCardMapper.selectById(card.getId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(updated.getAccumulatedDepreciation()));
        assertEquals(0, new BigDecimal("59000.00").compareTo(updated.getNetValue()));
        assertEquals("202602", updated.getLastDepreciationPeriod());
    }

    /**
     * 场景 7：待折旧资产查询测试
     * 验证：selectToDepreciate 可正确过滤出待折旧资产
     */
    @Test
    void selectToDepreciate_shouldFilterCorrectly() {
        // 已计提折旧的资产
        AssetCardEntity card1 = new AssetCardEntity();
        card1.setAssetCode("FA-TEST-007A");
        card1.setAssetName("已折旧资产");
        card1.setCategoryId(testCategoryId);
        card1.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        card1.setOriginalValue(new BigDecimal("30000.00"));
        card1.setResidualValue(BigDecimal.ZERO);
        card1.setUsefulLife(5);
        card1.setDepreciationMethod("STRAIGHT_LINE");
        card1.setStatus("IN_USE");
        card1.setAccumulatedDepreciation(new BigDecimal("1000.00"));
        card1.setNetValue(new BigDecimal("29000.00"));
        card1.setLastDepreciationPeriod("202601");  // 已计提至 1月
        card1.setCreatedBy(1L);
        card1.setDeleted(0);
        assetCardMapper.insert(card1);

        // 未计提折旧的资产
        AssetCardEntity card2 = new AssetCardEntity();
        card2.setAssetCode("FA-TEST-007B");
        card2.setAssetName("未折旧资产");
        card2.setCategoryId(testCategoryId);
        card2.setAcquisitionDate(LocalDate.of(2026, 2, 1));
        card2.setOriginalValue(new BigDecimal("30000.00"));
        card2.setResidualValue(BigDecimal.ZERO);
        card2.setUsefulLife(5);
        card2.setDepreciationMethod("STRAIGHT_LINE");
        card2.setStatus("IN_USE");
        card2.setAccumulatedDepreciation(BigDecimal.ZERO);
        card2.setNetValue(new BigDecimal("30000.00"));
        card2.setLastDepreciationPeriod(null);  // 从未计提
        card2.setCreatedBy(1L);
        card2.setDeleted(0);
        assetCardMapper.insert(card2);

        // 查询 202602 期待折旧资产
        List<AssetCardEntity> toDepreciate = assetCardMapper.selectToDepreciate(
                testCategoryId, "202602");

        assertNotNull(toDepreciate);
        // card1 lastDepreciationPeriod=202601 < 202602 → 应返回
        // card2 lastDepreciationPeriod=null → 应返回
        assertTrue(toDepreciate.stream().anyMatch(c -> "FA-TEST-007A".equals(c.getAssetCode())));
        assertTrue(toDepreciate.stream().anyMatch(c -> "FA-TEST-007B".equals(c.getAssetCode())));
    }

    /**
     * 场景 8：唯一约束测试
     * 验证：重复 asset_code 会违反唯一约束
     */
    @Test
    void duplicateAssetCode_shouldThrowException() {
        AssetCardEntity card1 = new AssetCardEntity();
        card1.setAssetCode("FA-DUP-001");
        card1.setAssetName("原件");
        card1.setCategoryId(testCategoryId);
        card1.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        card1.setOriginalValue(new BigDecimal("1000.00"));
        card1.setResidualValue(BigDecimal.ZERO);
        card1.setUsefulLife(5);
        card1.setDepreciationMethod("STRAIGHT_LINE");
        card1.setStatus("IN_USE");
        card1.setAccumulatedDepreciation(BigDecimal.ZERO);
        card1.setNetValue(new BigDecimal("1000.00"));
        card1.setCreatedBy(1L);
        card1.setDeleted(0);
        assetCardMapper.insert(card1);

        // 重复 asset_code
        AssetCardEntity card2 = new AssetCardEntity();
        card2.setAssetCode("FA-DUP-001");  // 重复
        card2.setAssetName("副本");
        card2.setCategoryId(testCategoryId);
        card2.setAcquisitionDate(LocalDate.of(2026, 2, 1));
        card2.setOriginalValue(new BigDecimal("2000.00"));
        card2.setResidualValue(BigDecimal.ZERO);
        card2.setUsefulLife(5);
        card2.setDepreciationMethod("STRAIGHT_LINE");
        card2.setStatus("IN_USE");
        card2.setAccumulatedDepreciation(BigDecimal.ZERO);
        card2.setNetValue(new BigDecimal("2000.00"));
        card2.setCreatedBy(1L);
        card2.setDeleted(0);

        assertThrows(Exception.class, () -> assetCardMapper.insert(card2));
    }
}