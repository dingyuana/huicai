package com.huicai.sme.tax.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.entity.TaxBurdenConfigEntity;
import com.huicai.sme.tax.mapper.TaxBurdenConfigMapper;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P156：P1-1 税务申报收尾 —— 税负预警阈值真库全链。
 * V200 迁移（t_tax_burden_config + RLS 三件套）+ service upsert（事务内 GUC）。
 */
@DisplayName("P156 税负预警阈值真库：默认值 + upsert + RLS 隔离")
class TaxBurdenConfigRealDBTest extends AbstractMapperTest {

    @Autowired
    private TaxService taxService;

    @Autowired
    private TaxBurdenConfigMapper configMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // 测试事务回滚即可清理；显式兜底防 REQUIRES_NEW 残留（本类无，防御性）
        jdbcTemplate.update("DELETE FROM t_tax_burden_config WHERE remark LIKE 'P156-%'");
    }

    private void loginAs(long enterpriseId) {
        com.huicai.base.system.entity.UserEntity user = new com.huicai.base.system.entity.UserEntity();
        user.setId(9L);
        user.setUsername("p156-real");
        user.setPassword("encoded");
        user.setUserType("AGENCY");
        user.setAgencyId(enterpriseId);
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        com.huicai.config.security.LoginUser loginUser =
                new com.huicai.config.security.LoginUser(user, java.util.List.of(), null,
                        enterpriseId, "AGENCY", "AGENCY_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("全链：save upsert 真落库 → get 回读一致；未配置时默认 3%")
    void saveThenGetRoundTrip() {
        loginAs(1L);

        // ① 未配置 → 默认 3%
        TaxBurdenConfigEntity before = taxService.getBurdenConfig("VAT");
        assertEquals(0, new BigDecimal("0.03").compareTo(before.getThresholdRate()));
        assertEquals(Boolean.TRUE, before.getEnabled());

        // ② save 新行
        TaxBurdenConfigEntity saved = taxService.saveBurdenConfig(
                "VAT", new BigDecimal("0.025"), Boolean.TRUE, "P156-探针", 9L);
        assertNotNull(saved.getId());

        // ③ 回读一致
        TaxBurdenConfigEntity got = taxService.getBurdenConfig("VAT");
        assertEquals(0, new BigDecimal("0.025").compareTo(got.getThresholdRate()));
        assertEquals("P156-探针", got.getRemark());

        // ④ upsert：同 taxType 再存 → 更新同一行
        taxService.saveBurdenConfig("VAT", new BigDecimal("0.04"), Boolean.FALSE, "P156-改", 9L);
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_tax_burden_config WHERE tax_type='VAT' AND deleted=0",
                Integer.class);
        assertEquals(1, rowCount, "upsert 不新增行");
        TaxBurdenConfigEntity updated = taxService.getBurdenConfig("VAT");
        assertEquals(0, new BigDecimal("0.04").compareTo(updated.getThresholdRate()));
        assertEquals(Boolean.FALSE, updated.getEnabled());
    }
}
