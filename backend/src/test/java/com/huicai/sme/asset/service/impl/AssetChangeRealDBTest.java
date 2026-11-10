package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.asset.dto.AssetChangeCreateDTO;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetChangeEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetChangeMapper;
import com.huicai.sme.asset.service.AssetChangeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P144（D-4）真库面：资产变动全链路（V1.106）。
 *
 * <p>锁定五件事（Mockito 面拦不住的）：
 * ①VALUE_ADJUST 真落 t_asset_change 且 voucherId 回填非空；
 * ②t_voucher / t_voucher_entry 真落库，分录借 1601 固定资产 / 贷 1002 银行存款；
 * ③卡片 originalValue/netValue 真更新且 t_asset_depreciation 历史不动（D-4 铁律，隐式：
 *   全链路无该表写入）；④DEPT_TRANSFER 更新 deptId 且不制证；
 * ⑤调减后净值为负 400（真库数值口径）。</p>
 *
 * <p>企业上下文用默认企业 1（1601/1002 科目 V115 种子实测在）。
 * 期间检查按「未配置视为开放」设计放行（演练库无 202611 period 行）。</p>
 */
@DisplayName("SPEC-P144：资产变动（D-4）—— 调整凭证真落库 + 部门转移 + 净值守卫")
class AssetChangeRealDBTest extends AbstractMapperTest {

    @Autowired private AssetChangeService assetChangeService;
    @Autowired private AssetCardMapper cardMapper;
    @Autowired private AssetChangeMapper changeMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherEntryMapper voucherEntryMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private AssetCardEntity makeCard(String original, String accumulated) {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("D4-" + (System.nanoTime() % 100000000));
        card.setAssetName("D4 测试资产");
        card.setCategoryId(ensureAssetCategory(DEFAULT_ENTERPRISE_ID));
        card.setAcquisitionDate(LocalDate.of(2024, 1, 1));
        card.setOriginalValue(new BigDecimal(original));
        card.setUsefulLife(60);
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(new BigDecimal(accumulated));
        card.setNetValue(new BigDecimal(original).subtract(new BigDecimal(accumulated)));
        card.setDeptId(3L);
        cardMapper.insert(card);
        return card;
    }

    private AssetChangeCreateDTO dto(Long assetId, String type, String amount, Long deptId) {
        AssetChangeCreateDTO dto = new AssetChangeCreateDTO();
        dto.setAssetId(assetId);
        dto.setChangeType(type);
        dto.setChangeDate(LocalDate.of(2026, 11, 1));
        dto.setAmount(amount == null ? null : new BigDecimal(amount));
        dto.setDeptId(deptId);
        return dto;
    }

    private Long subjectId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE code=? AND deleted=0 AND enterprise_id=1 LIMIT 1",
                Long.class, code);
    }

    /** 快照串形态 {@code originalValue=<数值>} → 解析数值部分（不绑 DB 标度）。 */
    private static java.math.BigDecimal snapshotAmount(String snapshot) {
        org.junit.jupiter.api.Assertions.assertTrue(
                snapshot != null && snapshot.startsWith("originalValue="),
                "快照前缀必须是 originalValue=，实际：" + snapshot);
        return new java.math.BigDecimal(snapshot.substring("originalValue=".length()));
    }

    @Test
    @DisplayName("VALUE_ADJUST 调增：变动落库+凭证回填+分录借1601/贷1002+卡片原值净值更新")
    void valueAdjustIncreaseFullChain() {
        AssetCardEntity card = makeCard("50000", "10000");

        Long changeId = assetChangeService.create(
                dto(card.getId(), "VALUE_ADJUST", "10000", null), 9L);

        // ① 变动行真落库 + 快照（numeric 列回读带标度，断言解析数值而非字面串）
        AssetChangeEntity change = changeMapper.selectById(changeId);
        assertNotNull(change, "变动行未落库");
        assertEquals("VALUE_ADJUST", change.getChangeType());
        assertEquals(0, snapshotAmount(change.getBeforeValue()).compareTo(new BigDecimal("50000")),
                "before 快照必须记录调整前原值，实际：" + change.getBeforeValue());
        assertEquals(0, snapshotAmount(change.getAfterValue()).compareTo(new BigDecimal("60000")),
                "after 快照必须记录调整后原值，实际：" + change.getAfterValue());

        // ② 凭证真落库：号 = ADJ-{changeId}，回填 voucherId
        assertNotNull(change.getVoucherId(), "调整凭证 voucherId 未回填");
        VoucherEntity voucher = voucherMapper.selectById(change.getVoucherId());
        assertNotNull(voucher, "调整凭证未落库");
        assertEquals("ADJ-" + changeId, voucher.getVoucherNo());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("10000")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("10000")));

        // ③ 分录方向：借 1601 固定资产 / 贷 1002 银行存款
        List<VoucherEntryEntity> entries = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getVoucherId, voucher.getId())
                        .orderByAsc(VoucherEntryEntity::getSortOrder));
        assertEquals(2, entries.size(), "调整凭证必须恰好两行分录");
        Long s1601 = subjectId("1601");
        Long s1002 = subjectId("1002");
        assertEquals(s1601, entries.get(0).getSubjectId(), "第一行必须是借 1601 固定资产");
        assertEquals(0, entries.get(0).getDebit().compareTo(new BigDecimal("10000")));
        assertEquals(0, entries.get(0).getCredit().compareTo(BigDecimal.ZERO));
        assertEquals(s1002, entries.get(1).getSubjectId(), "第二行必须是贷 1002 银行存款");
        assertEquals(0, entries.get(1).getCredit().compareTo(new BigDecimal("10000")));
        assertEquals(0, entries.get(1).getDebit().compareTo(BigDecimal.ZERO));

        // ④ 卡片主数据更新（t_asset_depreciation 历史不动 —— 本链路无该表写入）
        AssetCardEntity after = cardMapper.selectById(card.getId());
        assertEquals(0, after.getOriginalValue().compareTo(new BigDecimal("60000")));
        assertEquals(0, after.getNetValue().compareTo(new BigDecimal("50000")));
    }

    @Test
    @DisplayName("DEPT_TRANSFER：deptId 真更新、快照正确、不制证")
    void deptTransferNoVoucher() {
        AssetCardEntity card = makeCard("50000", "10000");

        Long changeId = assetChangeService.create(
                dto(card.getId(), "DEPT_TRANSFER", null, 7L), 9L);

        AssetChangeEntity change = changeMapper.selectById(changeId);
        assertNotNull(change, "变动行未落库");
        assertEquals("deptId=3", change.getBeforeValue());
        assertEquals("deptId=7", change.getAfterValue());
        assertNull(change.getVoucherId(), "部门转移不得生成凭证");
        assertEquals(7L, cardMapper.selectById(card.getId()).getDeptId());
    }

    @Test
    @DisplayName("负向：调减后净值为负必须 400，卡片与变动表零痕迹")
    void valueAdjustNegativeNetRejected() {
        AssetCardEntity card = makeCard("50000", "10000");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetChangeService.create(
                        dto(card.getId(), "VALUE_ADJUST", "-55000", null), 9L));
        assertTrue(ex.getMessage().contains("净值为负"), "实际报错：" + ex.getMessage());

        // 卡片原值未被改动、变动表零痕迹（原子性：拒绝即整单无副作用）
        assertEquals(0, cardMapper.selectById(card.getId()).getOriginalValue()
                .compareTo(new BigDecimal("50000")));
        assertEquals(0, changeMapper.selectCount(
                new LambdaQueryWrapper<AssetChangeEntity>()
                        .eq(AssetChangeEntity::getAssetId, card.getId())));
    }
}
