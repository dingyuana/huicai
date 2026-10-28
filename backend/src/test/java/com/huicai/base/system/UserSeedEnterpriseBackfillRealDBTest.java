package com.huicai.base.system;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P141 V191 真库回归锁：t_user 种子账号 enterprise_id 回填。
 *
 * <p><b>为什么必须有它</b>：V1.90（2026-10-27）曾在运行库手动
 * {@code UPDATE t_user SET enterprise_id=1}，却没落进 Flyway 迁移链——
 * 2026-10-28 scratch 库重放全部 106 个迁移实测：三个种子 AGENCY 账号的
 * {@code enterprise_id} 全为 NULL，每次重建环境必复现。V165 只补过
 * {@code t_agency_user_enterprise} 授权行，从未修过 {@code t_user} 列本身。
 * {@code AbstractMapperTest} 的容器跑全量 Flyway（含 V191），
 * 正是「全新环境组装」的最小复制品。
 *
 * <p><b>为什么走在真库而不是 Mockito</b>：数据回填的正确性只由「迁移执行后的表状态」
 * 定义，Mock 测不出 Flyway 链的缺行/错序（AGENTS §4.5 第 33 条：超管绕 RLS ⇒
 * 数据面断言必须落在真库）。
 *
 * <p><b>反证承诺</b>：摘掉 V191 后 AT-141-1 与 AT-141-2 必红
 * （scratch 探针已先行证明修复前状态红，见 SPEC-P141 §0.2）。
 *
 * @see docs/specs/P141-tai-closure-and-user-seed-backfill.md
 */
@DisplayName("SPEC-P141：t_user 种子 enterprise_id 回填（真实 DB，V191）")
class UserSeedEnterpriseBackfillRealDBTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("AT-141-1 accountant01/reviewer01/assistant01 必须归属企业 1")
    void seedAgencyUsersHaveEnterprise() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, enterprise_id FROM t_user "
                        + "WHERE username IN ('accountant01', 'reviewer01', 'assistant01')");
        assertEquals(3, rows.size(),
                "三个种子账号必须在场——不在场说明 V1 之前的种子结构变了，本守卫需同步重新评估");

        for (Map<String, Object> row : rows) {
            String username = String.valueOf(row.get("username"));
            Object ent = row.get("enterprise_id");
            assertTrue(ent != null && ((Number) ent).longValue() == 1L,
                    username + ".enterprise_id 必须为 1，实际=" + ent
                            + " ⇒ V191 回填缺失或被绕过（登录后该账号 GUC 不落，读接口空数据）");
        }
    }

    @Test
    @DisplayName("AT-141-2 全新组装环境下 t_user 不得存在 NULL 归属行")
    void noNullEnterpriseAnywhereOnFreshAssembledEnv() {
        // 本测试类每次独占一个 Flyway 全量（含 V191）组装的容器，
        // 因此「全表 0 个 NULL」即 scratch 探针（SPEC-P141 §0.2）的常驻化。
        // 若未来种子有意加入 NULL 归属的行，此处红 ⇒ 要求说明业务语义并同步本守卫。
        Integer nulls = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_user WHERE enterprise_id IS NULL", Integer.class);
        assertEquals(0, nulls, "全新环境下 t_user 的 NULL 归属行必须为 0（V191 失效=每环境复现 V1.90 事故）");
    }
}
