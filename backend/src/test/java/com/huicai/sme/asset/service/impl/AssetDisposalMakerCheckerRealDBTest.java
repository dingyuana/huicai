package com.huicai.sme.asset.service.impl;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import com.huicai.sme.asset.mapper.AssetDisposalMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P119 真库行为验证：资产处置的制审分离**真的会拦**。
 *
 * <p><b>为什么必须是真库测试，且为什么这个测试本身需要被守卫</b>：
 * 本 SPEC 实施后 L2 全量 {@code 2213/0/0/5} 零回归，
 * 但那只说明<b>既有用例没被拦</b> —— 它们全部无登录态，
 * {@code created_by} 落 {@code NULL} ⇒ 校验按设计放行。
 *
 * <p>⚠️ 因此「零回归」在本例中是<b>假象</b>：它无法区分
 * 「校验正确放行了合法场景」与「校验根本没生效」。
 * 只有构造「制单人 == 审批人 且 created_by 非空」的夹具才能证明后者。
 *
 * @see com.huicai.base.voucher.service.VoucherMakerCheckerGuardTest 凭证层的同类验证
 */
@DisplayName("SPEC-P119：资产处置制审分离真库验证")
class AssetDisposalMakerCheckerRealDBTest extends AbstractMapperTest {

    private static final long MAKER = 770001L;

    @Autowired
    private AssetDisposalServiceImpl service;

    @Autowired
    private AssetDisposalMapper mapper;

    @Autowired
    private AssetCardMapper assetCardMapper;

    @Autowired
    private AssetCategoryMapper categoryMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 构造登录态 —— {@code LoginUser.userId} 是 final 且构造器需 {@code UserEntity}，
     * 故按真实路径构造，不用反射塞字段（反射对 final 不可靠且掩盖真实约束）。
     */
    private void loginAs(long userId) {
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setUsername("p119-user-" + userId);
        u.setPassword("n/a");
        u.setEnterpriseId(1L);
        u.setUserType("ENTERPRISE");

        LoginUser login = new LoginUser(u, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    /**
     * 造一条 DRAFT 处置单；createdBy 传 null 即模拟「无录入人」场景。
     *
     * <p>⚠️ 关联的资产卡<b>用 {@link AssetCardEntity} 造而非裸 SQL</b>：
     * 本测试初版写裸 SQL 时用了不存在的列 {@code asset_category_id}
     * （真实列名是 {@code category_id}），且漏了 NOT NULL 的
     * {@code residual_value}/{@code useful_life} ⇒ 5 例全 ERROR。
     * 走 Entity 后列名随映射自动对齐，Schema 变更不会再打脸夹具。
     */
    private long fixture(Long createdBy) {
        Long entId = jdbc.queryForObject(
                "SELECT id FROM t_enterprise WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);

        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("P119-AST-" + System.nanoTime());
        card.setAssetName("P119 测试资产");
        card.setCategoryId(assetCategoryId(entId));
        card.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        card.setOriginalValue(new BigDecimal("10000"));
        card.setResidualValue(BigDecimal.ZERO);
        card.setUsefulLife(60);
        card.setAccumulatedDepreciation(BigDecimal.ZERO);
        card.setNetValue(new BigDecimal("10000"));
        card.setStatus("IN_USE");
        card.setDeleted(0);
        assetCardMapper.insert(card);

        AssetDisposalEntity e = new AssetDisposalEntity();
        e.setDisposalNo("P119-DISP-" + System.nanoTime());
        e.setDisposalType("SALE");
        e.setDisposalDate(LocalDate.of(2026, 7, 1));
        e.setPeriod("202607");
        e.setAssetId(card.getId());
        e.setOriginalValue(new BigDecimal("10000"));
        e.setAccumulatedDepreciation(BigDecimal.ZERO);
        e.setNetValue(new BigDecimal("10000"));
        e.setDisposalIncome(BigDecimal.ZERO);
        e.setDisposalExpense(BigDecimal.ZERO);
        e.setGainLoss(BigDecimal.ZERO);
        e.setStatus("DRAFT");
        e.setCreatedBy(createdBy);
        e.setDeleted(0);
        mapper.insert(e);
        return e.getId();
    }

    /**
     * 取一个可用的资产分类（t_asset_category 为空时造一条）。
     *
     * <p>⚠️ 同样走 Entity 而非裸 SQL —— 裸 SQL 版本写了
     * {@code category_name}/{@code category_type}/{@code sort_order}/{@code status}，
     * 真实列却是 {@code code}/{@code name}/{@code level}，无一命中。
     */
    private Long assetCategoryId(Long entId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM t_asset_category WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        AssetCategoryEntity cat = new AssetCategoryEntity();
        cat.setCode("P119-CAT-" + (System.nanoTime() % 100000));
        cat.setName("P119 测试分类");
        cat.setLevel(1);
        cat.setUsefulLife(60);
        cat.setDeleted(0);
        categoryMapper.insert(cat);
        return cat.getId();
    }

    /**
     * 切到真实的企业上下文。
     *
     * <p>⚠️ SPEC-P120 修 CI 红：租户夹具一致性门禁判本类为 A 类违规 ——
     * 在实体上直接 {@code setEnterpriseId(entId)} 会被
     * {@code MyMetaObjectHandler} 的强制覆盖改写，使「设的」与「实际落的」不一致。
     * 正确范式是切上下文（与 4 个兄弟测试一致），不在实体上硬设。
     */
    @BeforeEach
    void switchToEnterprise() {
        Long entId = jdbc.queryForObject(
                "SELECT id FROM t_enterprise WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        useEnterprise(entId);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("前置可信度：created_by 必须真的能读回来")
    void createdByIsPersisted() {
        long id = fixture(MAKER);
        AssetDisposalEntity e = mapper.selectById(id);
        assertNotNull(e, "夹具插入失败");
        assertEquals(MAKER, e.getCreatedBy(),
                "created_by 读回为 null ⇒ P117 修复已失效，本类的自审断言会全部走放行分支"
                        + "（即校验完全失效时本测试依然会通过）");
    }

    @Test
    @DisplayName("制单人与审批人相同 ⇒ 必须被拒（核心断言）")
    void selfApprovalRejected() {
        long id = fixture(MAKER);
        loginAs(MAKER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(id),
                "制单人竟能审批自己提交的处置单 ⇒ SPEC-P119 制审分离未生效");
        assertTrue(ex.getMessage().contains("制单人不能审批"),
                "报错文案须指明原因，实际：" + ex.getMessage());

        // 负向断言：被拒后状态不得变化（防止「先改后判」把数据改坏）
        AssetDisposalEntity after = mapper.selectById(id);
        assertEquals("DRAFT", after.getStatus(),
                "自审被拒后状态竟变了 ⇒ 实现是「先改后判」，数据已损坏");
    }

    @Test
    @DisplayName("不同人审批 ⇒ 必须放行（防止守卫变成恒红）")
    void otherUserCanApprove() {
        long id = fixture(MAKER);
        loginAs(MAKER + 1);

        service.approve(id);
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT status FROM t_asset_disposal WHERE id=?", String.class, id),
                "他人审批应放行且状态应变为 APPROVED");
    }

    @Test
    @DisplayName("created_by 为 NULL ⇒ 放行（Excel 导入无「录入人」概念的已知局限）")
    void nullCreatedByIsAllowed() {
        long id = fixture(null);
        loginAs(MAKER);

        service.approve(id);
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT status FROM t_asset_disposal WHERE id=?", String.class, id),
                "created_by 为 NULL 时应放行（与凭证层口径一致）");
    }

    @Test
    @DisplayName("无登录态 ⇒ 放行且不抛异常（定时任务路径）")
    void noLoginIsAllowed() {
        long id = fixture(MAKER);
        SecurityContextHolder.clearContext();

        service.approve(id);
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT status FROM t_asset_disposal WHERE id=?", String.class, id),
                "无登录态时应放行（currentOperatorId 返回 null，三重判空放行）");
    }
}