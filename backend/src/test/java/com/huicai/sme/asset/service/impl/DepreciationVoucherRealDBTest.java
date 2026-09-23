package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.asset.dto.DepreciationVoucherResult;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.entity.AssetDepreciationEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import com.huicai.sme.asset.mapper.AssetDepreciationMapper;
import org.junit.jupiter.api.BeforeEach;
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
 * DEPR 折旧制证 真实 DB 测试（P85-C）.
 *
 * mock 测不到、必须真库验证的点：
 * <ul>
 *   <li><b>enterprise_id 自动填充</b>——VoucherEntity 未显式设企业ID，靠
 *       MyMetaObjectHandler 从 EnterpriseContextHolder 注入；若注入失败会撞
 *       t_voucher.enterprise_id NOT NULL 直接 PSQLException</li>
 *   <li><b>真实 FK/CHECK 约束</b>——分录 subject_id 必须指向真实 t_subject；
 *       chk_entry_amount (debit&gt;=0, credit&gt;=0)、chk_entry_not_both_zero</li>
 *   <li><b>幂等查询命中</b>——likeRight(DEPR-202608) 在真实索引上的行为，
 *       重复调用必须报错而非生成第二张凭证</li>
 *   <li><b>溯源回写</b>——t_asset_depreciation.voucher_id 真实落库</li>
 * </ul>
 *
 * 依赖 V102 种子的 6602 管理费用 / 1602 累计折旧（enterprise_id=1）。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
class DepreciationVoucherRealDBTest extends AbstractMapperTest {

    private static final Long ENTERPRISE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final String PERIOD = "202608";

    @Autowired private PeriodService periodService;
    @Autowired private SubjectMapper subjectMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherEntryMapper voucherEntryMapper;
    @Autowired private AssetCardMapper assetCardMapper;
    @Autowired private AssetCategoryMapper assetCategoryMapper;
    @Autowired private AssetDepreciationMapper depreciationMapper;

    private DepreciationVoucherServiceImpl service;

    @BeforeEach
    void setUp() {
        EnterpriseContextHolder.set(ENTERPRISE_ID);
        service = new DepreciationVoucherServiceImpl(depreciationMapper, subjectMapper,
                periodService, voucherMapper, voucherEntryMapper);
        insertPeriod(PERIOD, "open");
    }

    private void insertPeriod(String periodCode, String status) {
        PeriodEntity p = new PeriodEntity();
        p.setYear(2026);
        p.setMonth(Integer.parseInt(periodCode.substring(4)));
        p.setPeriodCode(periodCode);
        p.setStartDate(LocalDate.of(2026, Integer.parseInt(periodCode.substring(4)), 1));
        p.setEndDate(LocalDate.of(2026, Integer.parseInt(periodCode.substring(4)), 31));
        p.setStatus(status);
        p.setEnterpriseId(ENTERPRISE_ID);
        p.setDeleted(0);
        periodService.save(p);
        assertNotNull(p.getId(), "期间插入后应有 ID");
    }

    private void insertDepreciation(Long assetId, String amount) {
        AssetDepreciationEntity r = new AssetDepreciationEntity();
        r.setAssetId(assetId);
        r.setPeriod(PERIOD);
        r.setDepreciationAmount(new BigDecimal(amount));
        r.setAccumulatedDepreciation(new BigDecimal(amount));
        r.setNetValue(new BigDecimal("10000.00").subtract(new BigDecimal(amount)));
        r.setEnterpriseId(ENTERPRISE_ID);
        r.setDeleted(0);
        assertEquals(1, depreciationMapper.insert(r));
        assertNotNull(r.getId());
    }

    /**
     * 造一条真实的折旧计提记录。
     * t_asset_depreciation.asset_id 有 fk_dep_asset → t_asset_card，且 t_asset_card.category_id
     * 为 NOT NULL，因此必须先造类别再造卡片，不能直接用伪造的 asset_id。
     */
    private void insertDepreciationWithRealAsset(String amount) {
        AssetCategoryEntity cat = new AssetCategoryEntity();
        cat.setCode("ZTC" + (System.nanoTime() % 1000000000L));
        cat.setName("折旧制证测试类别");
        cat.setLevel(1);
        cat.setEnterpriseId(ENTERPRISE_ID);
        cat.setDeleted(0);
        assertEquals(1, assetCategoryMapper.insert(cat));
        assertNotNull(cat.getId());

        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("ZTCARD" + (System.nanoTime() % 100000000L));
        card.setAssetName("折旧制证测试资产");
        card.setCategoryId(cat.getId());
        card.setAcquisitionDate(LocalDate.of(2025, 1, 1));
        card.setOriginalValue(new BigDecimal("10000.00"));
        card.setResidualValue(BigDecimal.ZERO);
        card.setUsefulLife(12);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("10000.00"));
        card.setEnterpriseId(ENTERPRISE_ID);
        card.setDeleted(0);
        assertEquals(1, assetCardMapper.insert(card));
        assertNotNull(card.getId());

        insertDepreciation(card.getId(), amount);
    }

    @Test
    @DisplayName("端到端：DEPR 凭证落库 DRAFT，enterprise_id 自动填充，分录借贷平衡，溯源回写")
    void generate_endToEnd_balancedAndBackfilled() {
        // 前置：企业 1 下 6602 / 1602 科目必须存在（V102 种子）
        Long cnt6602 = subjectMapper.selectCount(new LambdaQueryWrapper<com.huicai.base.system.entity.Subject>()
                .eq(com.huicai.base.system.entity.Subject::getCode, "6602")
                .eq(com.huicai.base.system.entity.Subject::getDeleted, 0));
        assertNotNull(cnt6602);
        assertTrue(cnt6602 > 0, "6602 管理费用科目未种子，前置数据缺失");

        insertDepreciationWithRealAsset("1200.00");
        insertDepreciationWithRealAsset("800.00");

        DepreciationVoucherResult result = service.generate(PERIOD, USER_ID);

        assertEquals("DEPR-202608", result.voucherNo());
        // 金额比较用 compareTo：BigDecimal.equals 对 scale 敏感（2000.00 vs 2000.000 不相等），
        // 而 compareTo 只比数值。
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.totalDebit()),
                "借方合计应为 2000.00，实际: " + result.totalDebit());
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.totalCredit()),
                "贷方合计应为 2000.00，实际: " + result.totalCredit());
        assertEquals(2, result.detailCount());
        assertEquals("DRAFT", result.status());

        VoucherEntity voucher = voucherMapper.selectById(result.voucherId());
        assertNotNull(voucher);
        assertEquals(PERIOD, voucher.getPeriod());
        assertEquals("DRAFT", voucher.getStatus());
        assertEquals("GENERATED", voucher.getSource());
        // 注意：t_voucher.created_by 不落库。BaseEntity.createdBy 标了 @TableField(exist=false)，
        // MyBatis-Plus 不生成该列的 INSERT —— 这是项目既有行为（库里既有的 CLOSE-* 凭证
        // created_by 同样为 NULL，非 P85-C 引入）。审计追溯靠 t_close_log.operator_id 与应用日志。
        assertNull(voucher.getCreatedBy(),
                "createdBy 是 exist=false 字段，不会落库（与既有 CLOSE-* 凭证一致）");
        assertNotNull(voucher.getCreatedAt(), "created_at 应有 DB 默认值 CURRENT_TIMESTAMP");
        // 核心断言：enterprise_id 由 MyMetaObjectHandler 自动填充，未显式设置
        assertEquals(ENTERPRISE_ID, voucher.getEnterpriseId(),
                "enterprise_id 必须自动填充自 EnterpriseContextHolder");
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()),
                "凭证头借贷必须平衡");

        List<VoucherEntryEntity> entries = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getVoucherId, result.voucherId()));
        assertEquals(2, entries.size());
        BigDecimal totalD = entries.stream().map(VoucherEntryEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalC = entries.stream().map(VoucherEntryEntity::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalD.compareTo(totalC), "分录明细借贷必须平衡");
        assertEquals(0, totalD.compareTo(new BigDecimal("2000.00")));
        // 两行都非零（chk_entry_not_both_zero），且金额非负（chk_entry_amount）
        for (VoucherEntryEntity e : entries) {
            assertTrue(e.getDebit().signum() >= 0);
            assertTrue(e.getCredit().signum() >= 0);
            assertTrue(e.getDebit().add(e.getCredit()).signum() > 0,
                    "chk_entry_not_both_zero：借贷不能同时为 0");
            assertNotNull(e.getSubjectId());
        }

        // 溯源：两行折旧记录的 voucher_id 都回填
        List<AssetDepreciationEntity> recs = depreciationMapper.selectList(
                new LambdaQueryWrapper<AssetDepreciationEntity>()
                        .eq(AssetDepreciationEntity::getPeriod, PERIOD));
        assertEquals(2, recs.size());
        for (AssetDepreciationEntity rec : recs) {
            assertEquals(result.voucherId(), rec.getVoucherId(),
                    "t_asset_depreciation.voucher_id 必须回填指向本凭证");
        }
    }

    @Test
    @DisplayName("幂等：重复调用报错，不产生第二张 DEPR 凭证")
    void generate_twice_secondCallFails() {
        insertDepreciationWithRealAsset("500.00");
        service.generate(PERIOD, USER_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate(PERIOD, USER_ID));
        assertTrue(ex.getMessage().contains("已存在"), "应为幂等拒绝信息，实际: " + ex.getMessage());

        Long cnt = voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .likeRight(VoucherEntity::getVoucherNo, "DEPR-" + PERIOD)
                .eq(VoucherEntity::getDeleted, 0));
        assertEquals(1L, cnt, "幂等拒绝后仍应只有 1 张 DEPR 凭证");
    }

    @Test
    @DisplayName("期间已结账：拒绝制证且不产生任何写入")
    void generate_closedPeriodFails() {
        insertPeriod("202607", "closed");
        PeriodEntity closed = periodService.getByPeriodCode("202607");
        assertNotNull(closed);
        // 造一条 202607 的折旧记录用于验证"没有误写入"
        // 需要真类别+真卡片（fk_dep_asset → t_asset_card, category_id NOT NULL）
        AssetCategoryEntity cat = new AssetCategoryEntity();
        cat.setCode("ZTC2" + (System.nanoTime() % 1000000000L));
        cat.setName("已结账期间测试类别");
        cat.setLevel(1);
        cat.setEnterpriseId(ENTERPRISE_ID);
        cat.setDeleted(0);
        assertEquals(1, assetCategoryMapper.insert(cat));

        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("ZTCARD2" + (System.nanoTime() % 100000000L));
        card.setAssetName("已结账期间测试资产");
        card.setCategoryId(cat.getId());
        card.setAcquisitionDate(LocalDate.of(2025, 1, 1));
        card.setOriginalValue(new BigDecimal("10000.00"));
        card.setResidualValue(BigDecimal.ZERO);
        card.setUsefulLife(12);
        card.setStatus("IN_USE");
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("10000.00"));
        card.setEnterpriseId(ENTERPRISE_ID);
        card.setDeleted(0);
        assertEquals(1, assetCardMapper.insert(card));

        AssetDepreciationEntity r = new AssetDepreciationEntity();
        r.setAssetId(card.getId());
        r.setPeriod("202607");
        r.setDepreciationAmount(new BigDecimal("100.00"));
        r.setAccumulatedDepreciation(new BigDecimal("100.00"));
        r.setNetValue(new BigDecimal("9900.00"));
        r.setEnterpriseId(ENTERPRISE_ID);
        r.setDeleted(0);
        assertEquals(1, depreciationMapper.insert(r));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate("202607", USER_ID));
        assertTrue(ex.getMessage().contains("已结账"));

        Long cnt = voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .likeRight(VoucherEntity::getVoucherNo, "DEPR-202607")
                .eq(VoucherEntity::getDeleted, 0));
        assertEquals(0L, cnt, "已结账期间不应产生任何 DEPR 凭证");
    }

    @Test
    @DisplayName("无折旧数据：提示先计提，不生成空凭证")
    void generate_noDepreciationFails() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate(PERIOD, USER_ID));
        assertTrue(ex.getMessage().contains("折旧数据"));
        Long cnt = voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .likeRight(VoucherEntity::getVoucherNo, "DEPR-" + PERIOD)
                .eq(VoucherEntity::getDeleted, 0));
        assertEquals(0L, cnt);
    }

    @Test
    @DisplayName("期间未配置：拒绝")
    void generate_unknownPeriodFails() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate("209913", USER_ID));
        assertTrue(ex.getMessage().contains("未配置"));
    }
}
