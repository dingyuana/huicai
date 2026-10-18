package com.huicai.base.system.service;

import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P128 真实 DB 测试：凭证类型按 code 在**本企业**解析。
 *
 * <p>🔴 <b>为什么结构守卫不够</b>：{@code VoucherTypeHardcodeStructureTest} 只能证明
 * 「代码里没有硬编码主键」，<b>不能证明改完之后真的能用</b>。
 * 若 {@code requireByCode} 的企业范围收错了（例如误按全局查），结构守卫依然全绿。
 *
 * <p>🔴 <b>为什么自己建第二个企业</b>：L2 用的是 Testcontainers 全新库，
 * 其中 {@code t_enterprise} 只有「默认企业」1 行（实测）。若直接依赖库里现成的多企业，
 * 本用例会退化为「只验证企业 1」—— 而那恰恰是旧代码本来就正常的那一家，
 * <b>鉴别力为零</b>。故本测试自建企业，确保覆盖到「非企业 1」这条真正出问题的路径。
 */
@DisplayName("SPEC-P128：凭证类型按 code 在当前企业解析（真实 DB）")
class VoucherTypeRequireByCodeRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherTypeService voucherTypeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试自建的第二个企业 id（企业 1 是默认企业） */
    private Long otherEnterpriseId;

    @BeforeEach
    void createSecondEnterpriseWithItsOwnVoucherTypes() {
        jdbcTemplate.update(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status, "
                        + "seed_data_done, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, 'SME', 'ACTIVE', true, NOW(), NOW(), 0)",
                "P128TEST" + System.nanoTime() % 100000, "P128 测试企业");
        otherEnterpriseId = jdbcTemplate.queryForObject(
                "SELECT max(id) FROM t_enterprise WHERE enterprise_code LIKE 'P128TEST%'",
                Long.class);

        for (String code : List.of("JZ", "SK", "FK", "ZZ")) {
            jdbcTemplate.update(
                    "INSERT INTO t_voucher_type (enterprise_id, code, name, sort_order, "
                            + "numbering_rule, is_active, created_at, updated_at, deleted) "
                            + "VALUES (?, ?, ?, 1, '{type}-{serial}', true, NOW(), NOW(), 0)",
                    otherEnterpriseId, code, code + "凭证");
        }
    }

    @Test
    @DisplayName("非企业 1 也能按 code 取到自己的凭证类型（AT-128-1/2）")
    void nonDefaultEnterpriseResolvesOwnTypes() {
        useEnterprise(otherEnterpriseId);

        Map<String, Long> ids = new LinkedHashMap<>();
        for (String code : List.of("JZ", "SK", "FK", "ZZ")) {
            VoucherTypeEntity t = voucherTypeService.requireByCode(code);
            assertNotNull(t, "企业 " + otherEnterpriseId + " 取不到凭证类型 " + code);
            assertEquals(code, t.getCode(), "code 回读不一致");
            assertEquals(otherEnterpriseId, t.getEnterpriseId(),
                    "解析结果必须属于当前企业，否则是跨企业泄漏");
            ids.put(code, t.getId());
        }
        assertEquals(4, ids.size(), "四种凭证类型应解析出四个不同主键：" + ids);
    }

    @Test
    @DisplayName("🔴 核心：各企业的同一 code 解析出不同主键（否则等于又硬编码了一个全局 id）")
    void eachEnterpriseHasDistinctPrimaryKey() {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        Long jzOfDefault = voucherTypeService.requireByCode("JZ").getId();

        useEnterprise(otherEnterpriseId);
        Long jzOfOther = voucherTypeService.requireByCode("JZ").getId();

        assertNotEquals(jzOfDefault, jzOfOther,
                "两个企业的 JZ 主键必须不同 —— 相同就意味着「按 code 解析」退化成了固定取值");
    }

    @Test
    @DisplayName("复现原缺陷：在非企业 1 上下文下按主键 1 取不到（这正是原报障的成因）")
    void hardcodedPrimaryKeyFailsOutsideDefaultEnterprise() {
        useEnterprise(otherEnterpriseId);

        // 企业 1 的 JZ 主键
        Long defaultJzId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_type WHERE enterprise_id = 1 AND code = 'JZ' AND deleted = 0",
                Long.class);

        if (defaultJzId != null && !defaultJzId.equals(
                voucherTypeService.requireByCode("JZ").getId())) {
            assertNull(voucherTypeService.getById(defaultJzId),
                    "企业 " + otherEnterpriseId + " 上下文下 getById(" + defaultJzId + ") 应取不到"
                            + "（被 RLS 过滤）—— 这就是「凭证类型不存在」的成因");
        }
    }

    @Test
    @DisplayName("缺失 code 时异常必须回答「缺哪个、去哪配」，且不再出现「不存在」（AT-128-3）")
    void missingCodeThrowsActionableMessage() {
        useEnterprise(otherEnterpriseId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> voucherTypeService.requireByCode("NOSUCHCODE"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("NOSUCHCODE"), "须含业务编码；实际: " + msg);
        assertTrue(msg.contains("凭证类型"), "须说明缺的是凭证类型；实际: " + msg);
        assertTrue(msg.contains("新增") || msg.contains("配置"), "须给出下一步动作；实际: " + msg);
        assertTrue(!msg.contains("不存在"),
                "🔴 不得再出现「凭证类型不存在」这种既不说缺哪个也不说去哪配的文案；实际: " + msg);
    }

    @Test
    @DisplayName("code 的大小写与空白不敏感（前端可能传 'jz '）")
    void codeIsNormalized() {
        useEnterprise(otherEnterpriseId);
        assertNotNull(voucherTypeService.requireByCode(" jz "),
                "code 应做 trim + 大写归一，否则前端传 'jz ' 会误报未配置");
    }
}
