package com.huicai.performance;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 性能基线测试 — 关键接口响应时间门禁。
 *
 * <p>测量核心数据操作（selectList / insert / selectPage）的响应时间，
 * 确保在 CI 环境下不会出现明显的性能退化。
 *
 * <p><b>阈值说明：</b>
 * <ul>
 *   <li>单表 INSERT：≤ 500ms</li>
 *   <li>单表 SELECT（selectList）：≤ 500ms</li>
 *   <li>分页查询（selectPage of 100 items）：≤ 1000ms</li>
 *   <li>带条件查询（selectByMap）：≤ 500ms</li>
 * </ul>
 *
 * <p>注意：这些阈值对 CI 环境（PostgreSQL 空库）适用。
 * 生产环境数据量增长后需重新校准。
 */
@SlowTest
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("性能基线 — 关键接口响应时间门禁")
public class PerformanceBaselineTest extends AbstractMapperTest {

    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private BankStatementMapper bankStatementMapper;
    @Autowired private AssetCardMapper assetCardMapper;

    private static final int QUERY_THRESHOLD_MS = 500;
    private static final int PAGE_THRESHOLD_MS = 1000;

    @BeforeEach
    void setUp() {
        // 预热：确保连接池和 JIT 已初始化
        voucherMapper.selectCount(null);
    }

    @Test
    @DisplayName("Voucher - INSERT 性能 (≤500ms)")
    void voucherInsert_performance() {
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("PERF-VCH-001");
        entity.setPeriod("202608");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setSource("MANUAL");
        entity.setSummary("性能测试凭证");
        entity.setTotalDebit(new BigDecimal("1000.00"));
        entity.setTotalCredit(new BigDecimal("1000.00"));
        entity.setEnterpriseId(1L);

        long elapsed = measure(() -> voucherMapper.insert(entity));
        assertTrue(elapsed <= QUERY_THRESHOLD_MS,
                "Voucher INSERT 耗时 " + elapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("Voucher - SELECT 性能 (≤500ms)")
    void voucherSelect_performance() {
        // 批量插入测试数据
        for (int i = 0; i < 50; i++) {
            VoucherEntity e = new VoucherEntity();
            e.setVoucherNo("PERF-VCH-LIST-" + i);
            e.setPeriod("202608");
            e.setVoucherTypeId(1L);
            e.setStatus("DRAFT");
            e.setSource("MANUAL");
            e.setSummary("性能测试");
            e.setTotalDebit(new BigDecimal("1000.00"));
            e.setTotalCredit(new BigDecimal("1000.00"));
            e.setEnterpriseId(1L);
            voucherMapper.insert(e);
        }

        long elapsed = measure(() -> voucherMapper.selectList(null));
        assertTrue(elapsed <= QUERY_THRESHOLD_MS,
                "Voucher selectList 耗时 " + elapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("BusinessDoc - INSERT 性能 (≤500ms)")
    void businessDocInsert_performance() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("PERF-DOC-001");
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202608");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("DRAFT");
        doc.setDocDate(LocalDate.of(2026, 8, 1));
        doc.setEnterpriseId(1L);

        long elapsed = measure(() -> businessDocMapper.insert(doc));
        assertTrue(elapsed <= QUERY_THRESHOLD_MS,
                "BusinessDoc INSERT 耗时 " + elapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("BusinessDoc - SELECT 性能 (≤500ms)")
    void businessDocSelect_performance() {
        for (int i = 0; i < 50; i++) {
            BusinessDocEntity d = new BusinessDocEntity();
            d.setDocNo("PERF-DOC-LIST-" + i);
            d.setDocType("INVOICE_OUT");
            d.setPeriod("202608");
            d.setAmount(new BigDecimal("10000.00"));
            d.setStatus("DRAFT");
            d.setDocDate(LocalDate.of(2026, 8, 1));
            d.setEnterpriseId(1L);
            businessDocMapper.insert(d);
        }

        long elapsed = measure(() -> businessDocMapper.selectList(null));
        assertTrue(elapsed <= QUERY_THRESHOLD_MS,
                "BusinessDoc selectList 耗时 " + elapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("InputInvoice - INSERT 性能 (≤500ms)")
    void inputInvoiceInsert_performance() {
        InputInvoiceEntity inv = new InputInvoiceEntity();
        inv.setInvoiceNo("PERF-INV-001");
        inv.setInvoiceDate(LocalDate.of(2026, 8, 1));
        inv.setPeriod("202608");
        inv.setAmount(new BigDecimal("10000.00"));
        inv.setTaxRate(new BigDecimal("0.13"));
        inv.setTaxAmount(new BigDecimal("1300.00"));
        inv.setTotalAmount(new BigDecimal("11300.00"));
        inv.setInvoiceType("SPECIAL");
        inv.setCertificationStatus("UNCERTIFIED");
        inv.setEnterpriseId(1L);

        long elapsed = measure(() -> inputInvoiceMapper.insert(inv));
        assertTrue(elapsed <= QUERY_THRESHOLD_MS,
                "InputInvoice INSERT 耗时 " + elapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("BankStatement - INSERT + SELECT 性能 (≤500ms)")
    void bankStatement_performance() {
        BankStatementEntity bs = new BankStatementEntity();
        bs.setTxDate(LocalDate.of(2026, 8, 1));
        bs.setTxType("INCOME");
        bs.setAmount(new BigDecimal("50000.00"));
        bs.setCounterAccount("性能测试客户");
        bs.setSummary("货款");
        bs.setReviewStatus("PENDING");
        bs.setEnterpriseId(1L);

        long insertElapsed = measure(() -> bankStatementMapper.insert(bs));
        assertTrue(insertElapsed <= QUERY_THRESHOLD_MS,
                "BankStatement INSERT 耗时 " + insertElapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");

        long selectElapsed = measure(() -> bankStatementMapper.selectList(null));
        assertTrue(selectElapsed <= QUERY_THRESHOLD_MS,
                "BankStatement selectList 耗时 " + selectElapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("AssetCard - INSERT + SELECT 性能 (≤500ms)")
    void assetCard_performance() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("PERF-ASSET-001");
        card.setAssetName("性能测试资产");
        card.setOriginalValue(new BigDecimal("50000.00"));
        card.setStatus("IN_USE");
        card.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        card.setEnterpriseId(1L);

        long insertElapsed = measure(() -> assetCardMapper.insert(card));
        assertTrue(insertElapsed <= QUERY_THRESHOLD_MS,
                "AssetCard INSERT 耗时 " + insertElapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");

        long selectElapsed = measure(() -> assetCardMapper.selectList(null));
        assertTrue(selectElapsed <= QUERY_THRESHOLD_MS,
                "AssetCard selectList 耗时 " + selectElapsed + "ms，超过阈值 " + QUERY_THRESHOLD_MS + "ms");
    }

    @Test
    @DisplayName("分页查询性能 (≤1000ms)")
    void pagination_performance() {
        // 批量插入 100 条凭证
        for (int i = 0; i < 100; i++) {
            VoucherEntity e = new VoucherEntity();
            e.setVoucherNo("PERF-PAGE-" + i);
            e.setPeriod("202608");
            e.setVoucherTypeId(1L);
            e.setStatus("DRAFT");
            e.setSource("MANUAL");
            e.setSummary("分页性能测试");
            e.setTotalDebit(new BigDecimal("1000.00"));
            e.setTotalCredit(new BigDecimal("1000.00"));
            e.setEnterpriseId(1L);
            voucherMapper.insert(e);
        }

        // 第 5 页，每页 20 条
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VoucherEntity> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(5, 20);

        long elapsed = measure(() -> voucherMapper.selectPage(page, null));
        assertTrue(elapsed <= PAGE_THRESHOLD_MS,
                "Voucher 分页查询耗时 " + elapsed + "ms，超过阈值 " + PAGE_THRESHOLD_MS + "ms");
    }

    // ====== 辅助方法 ======

    /**
     * 测量操作耗时（毫秒）
     */
    private long measure(Runnable operation) {
        Instant start = Instant.now();
        operation.run();
        return Duration.between(start, Instant.now()).toMillis();
    }
}