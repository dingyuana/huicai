package com.huicai.agency.batch.service.impl;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.security.TenantGucSetter;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgencyBatchTransactionRunnerRealDBTest extends AbstractMapperTest {

    @Autowired
    private AgencyBatchTransactionRunner transactionRunner;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearEnterpriseContext() {
        EnterpriseContextHolder.clear();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void eachRequiresNewTransactionSetsItsTargetEnterpriseGuc() {
        prepareRlsProbe();
        try {
            EnterpriseContextHolder.set(1L);

            List<Long> first = transactionRunner.run(2L, this::visibleProbeTenants);
            List<Long> second = transactionRunner.run(3L, this::visibleProbeTenants);

            assertEquals(List.of(2L), first);
            assertEquals(List.of(3L), second);
            assertEquals(1L, EnterpriseContextHolder.get());
        } finally {
            EnterpriseContextHolder.clear();
            cleanupRlsProbe();
        }
    }

    private List<Long> visibleProbeTenants() {
        jdbcTemplate.execute("SET LOCAL ROLE p162_batch_probe");
        TenantGucSetter.apply(dataSource);
        return jdbcTemplate.queryForList(
                "SELECT enterprise_id FROM p162_batch_probe ORDER BY enterprise_id", Long.class);
    }

    private void prepareRlsProbe() {
        cleanupRlsProbe();
        jdbcTemplate.execute("CREATE ROLE p162_batch_probe NOSUPERUSER NOBYPASSRLS");
        jdbcTemplate.execute("CREATE TABLE p162_batch_probe (enterprise_id BIGINT NOT NULL)");
        jdbcTemplate.execute("ALTER TABLE p162_batch_probe ENABLE ROW LEVEL SECURITY");
        jdbcTemplate.execute("ALTER TABLE p162_batch_probe FORCE ROW LEVEL SECURITY");
        jdbcTemplate.execute("""
                CREATE POLICY p162_tenant_policy ON p162_batch_probe
                USING (enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::BIGINT)
                WITH CHECK (enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::BIGINT)
                """);
        jdbcTemplate.update("INSERT INTO p162_batch_probe (enterprise_id) VALUES (2), (3)");
        jdbcTemplate.execute("GRANT SELECT ON p162_batch_probe TO p162_batch_probe");
    }

    private void cleanupRlsProbe() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS p162_batch_probe");
        jdbcTemplate.execute("DROP ROLE IF EXISTS p162_batch_probe");
    }
}
