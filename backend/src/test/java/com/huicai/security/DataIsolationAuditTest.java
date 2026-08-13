package com.huicai.security;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.voucher.dto.VoucherQueryDTO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.entity.AssetCardEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据隔离审计测试 — 验证 enterprise_id 多租户隔离是否生效。
 *
 * <p>⚠️ 本测试揭示当前存在的安全漏洞：
 * 所有业务表的 enterprise_id 列已通过 V102-V105 迁移添加，但自定义 SQL 查询
 * 中未包含 enterprise_id 过滤条件。标准 MyBatis-Plus 方法也未配置租户拦截器。
 *
 * <p><b>当前状态：</b>企业 A 的数据对企业 B 可见（数据泄露）
 * <b>预期状态：</b>企业 A 的数据对企业 B 不可见（数据隔离）
 *
 * <p><b>修复方案：</b>在 Service 层查询方法中增加
 * {@code .eq(BaseEntity::getEnterpriseId, SecurityUtils.getCurrentEnterpriseId())}
 * 或配置 MyBatis-Plus TenantLineInnerInterceptor。
 *
 * <p><b>涉及的 Mapper XML 查询：</b>
 * <ul>
 *   <li>VoucherMapper.selectVoucherPage / selectVoucherList</li>
 *   <li>VoucherMapper.selectVoucherDetail</li>
 *   <li>VoucherEntryMapper.selectByVoucherId</li>
 *   <li>VoucherTemplateMapper.selectActiveByClassification / selectAllActive</li>
 *   <li>SubjectBalanceMapper.selectFromPeriod</li>
 *   <li>VoucherTemplateLineMapper.selectByTemplateId</li>
 * </ul>
 *
 * <p><b>已覆盖的模块：</b>Voucher（已修复）、BusinessDoc、InputInvoice、BankStatement、OutputInvoice、AssetCard
 *
 * <p>注意：本测试使用 Mapper 直接操作 DB，验证的是 SQL 查询层面是否遗漏 enterprise_id 过滤。
 * 当修复完成后，此测试的断言应反转（期望 enterprise B 的查询结果为空）。
 */
@SlowTest
@DisplayName("数据隔离审计 — enterprise_id 多租户隔离检查")
public class DataIsolationAuditTest extends AbstractMapperTest {

    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherService voucherService;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private BankStatementMapper bankStatementMapper;
    @Autowired private AssetCardMapper assetCardMapper;

    @Test
    @DisplayName("[已修复] VoucherMapper.selectVoucherPage 已过滤 enterprise_id")
    void voucherPageQuery_enterpriseIdFilterApplied() {
        // 创建企业 A 的凭证
        VoucherEntity voucherA = new VoucherEntity();
        voucherA.setVoucherNo("AUDIT-VCH-A-001");
        voucherA.setPeriod("202608");
        voucherA.setVoucherTypeId(1L);
        voucherA.setStatus("DRAFT");
        voucherA.setSource("MANUAL");
        voucherA.setSummary("企业A的测试凭证");
        voucherA.setTotalDebit(new BigDecimal("1000.00"));
        voucherA.setTotalCredit(new BigDecimal("1000.00"));
        voucherA.setEnterpriseId(1L);
        voucherMapper.insert(voucherA);

        // 创建企业 B 的凭证
        VoucherEntity voucherB = new VoucherEntity();
        voucherB.setVoucherNo("AUDIT-VCH-B-001");
        voucherB.setPeriod("202608");
        voucherB.setVoucherTypeId(1L);
        voucherB.setStatus("DRAFT");
        voucherB.setSource("MANUAL");
        voucherB.setSummary("企业B的测试凭证");
        voucherB.setTotalDebit(new BigDecimal("2000.00"));
        voucherB.setTotalCredit(new BigDecimal("2000.00"));
        voucherB.setEnterpriseId(2L);
        voucherMapper.insert(voucherB);

        // 通过自定义查询（selectVoucherPage）验证：已加 enterprise_id 过滤
        // 注意：此查询在 VoucherServiceImpl 中传 enterprise_id=1
        // 但 Testcontainers 没有 SecurityContext，所以需要通过 Mapper 直接调用
        // 这里验证的是 XML 中已加 AND v.enterprise_id = #{enterpriseId} 条件
        Page<VoucherEntity> page = new Page<>(1, 20);
        Page<VoucherEntity> result = voucherMapper.selectVoucherPage(page, "202608", null, null, null, null, null);
        assertTrue(result.getRecords().stream().anyMatch(v -> "AUDIT-VCH-A-001".equals(v.getVoucherNo())),
                "企业A的凭证应该被查到");
        assertTrue(result.getRecords().stream().noneMatch(v -> "AUDIT-VCH-B-001".equals(v.getVoucherNo())),
                "✅ 已修复：企业B的凭证不再被查到（enterprise_id 过滤已生效）");
    }

    @Test
    @DisplayName("[基线] BaseMapper.selectList 仍可通过 enterprise_id 列区分（数据层不强制过滤）")
    void baseMapperSelectList_rawData() {
        // 验证 BaseMapper 的 selectList 仍返回所有数据（不自动过滤 enterprise_id）
        // 这是预期的——MyBatis-Plus 不自动加租户过滤，依赖 Service 层自定义查询
        VoucherEntity voucherA = new VoucherEntity();
        voucherA.setVoucherNo("AUDIT-BASE-A-001");
        voucherA.setPeriod("202608");
        voucherA.setVoucherTypeId(1L);
        voucherA.setStatus("DRAFT");
        voucherA.setSource("MANUAL");
        voucherA.setSummary("BaseMapper企业A");
        voucherA.setTotalDebit(new BigDecimal("1000.00"));
        voucherA.setTotalCredit(new BigDecimal("1000.00"));
        voucherA.setEnterpriseId(1L);
        voucherMapper.insert(voucherA);

        VoucherEntity voucherB = new VoucherEntity();
        voucherB.setVoucherNo("AUDIT-BASE-B-001");
        voucherB.setPeriod("202608");
        voucherB.setVoucherTypeId(1L);
        voucherB.setStatus("DRAFT");
        voucherB.setSource("MANUAL");
        voucherB.setSummary("BaseMapper企业B");
        voucherB.setTotalDebit(new BigDecimal("2000.00"));
        voucherB.setTotalCredit(new BigDecimal("2000.00"));
        voucherB.setEnterpriseId(2L);
        voucherMapper.insert(voucherB);

        // BaseMapper 不自动过滤，但可以通过 where 条件手动过滤
        List<VoucherEntity> all = voucherMapper.selectList(null);
        assertTrue(all.stream().anyMatch(v -> "AUDIT-BASE-A-001".equals(v.getVoucherNo())),
                "企业A的凭证应该在BaseMapper中");
        // BaseMapper 不强制过滤，但这是预期的——用户必须通过 Service 层访问数据
        System.out.println("⚠️ BaseMapper 返回所有数据，enterprise_id 过滤依赖 Service 层");
    }

    @Test
    @DisplayName("[漏洞确认] BusinessDocMapper.selectList 未过滤 enterprise_id")
    void businessDocQuery_missingEnterpriseIdFilter() {
        // 创建企业 A 的业务单据
        BusinessDocEntity docA = new BusinessDocEntity();
        docA.setDocNo("AUDIT-DOC-A-001");
        docA.setDocType("INVOICE_OUT");
        docA.setPeriod("202608");
        docA.setAmount(new BigDecimal("10000.00"));
        docA.setStatus("DRAFT");
        docA.setDocDate(LocalDate.of(2026, 8, 1));
        docA.setEnterpriseId(1L);
        businessDocMapper.insert(docA);

        // 创建企业 B 的业务单据
        BusinessDocEntity docB = new BusinessDocEntity();
        docB.setDocNo("AUDIT-DOC-B-001");
        docB.setDocType("INVOICE_OUT");
        docB.setPeriod("202608");
        docB.setAmount(new BigDecimal("20000.00"));
        docB.setStatus("DRAFT");
        docB.setDocDate(LocalDate.of(2026, 8, 1));
        docB.setEnterpriseId(2L);
        businessDocMapper.insert(docB);

        List<BusinessDocEntity> allDocs = businessDocMapper.selectList(null);

        assertTrue(allDocs.stream().anyMatch(d -> "AUDIT-DOC-A-001".equals(d.getDocNo())),
                "企业A的业务单据应该被查到");
        assertTrue(allDocs.stream().anyMatch(d -> "AUDIT-DOC-B-001".equals(d.getDocNo())),
                "⚠️ 漏洞：企业B的业务单据也被查到，说明 enterprise_id 过滤缺失");
    }

    @Test
    @DisplayName("[漏洞确认] InputInvoiceMapper.selectList 未过滤 enterprise_id")
    void inputInvoiceQuery_missingEnterpriseIdFilter() {
        InputInvoiceEntity invA = new InputInvoiceEntity();
        invA.setInvoiceNo("AUDIT-INV-A-001");
        invA.setInvoiceDate(LocalDate.of(2026, 8, 1));
        invA.setPeriod("202608");
        invA.setAmount(new BigDecimal("10000.00"));
        invA.setTaxRate(new BigDecimal("0.13"));
        invA.setTaxAmount(new BigDecimal("1300.00"));
        invA.setTotalAmount(new BigDecimal("11300.00"));
        invA.setInvoiceType("SPECIAL");
        invA.setCertificationStatus("UNCERTIFIED");
        invA.setEnterpriseId(1L);
        inputInvoiceMapper.insert(invA);

        InputInvoiceEntity invB = new InputInvoiceEntity();
        invB.setInvoiceNo("AUDIT-INV-B-001");
        invB.setInvoiceDate(LocalDate.of(2026, 8, 1));
        invB.setPeriod("202608");
        invB.setAmount(new BigDecimal("20000.00"));
        invB.setTaxRate(new BigDecimal("0.13"));
        invB.setTaxAmount(new BigDecimal("2600.00"));
        invB.setTotalAmount(new BigDecimal("22600.00"));
        invB.setInvoiceType("SPECIAL");
        invB.setCertificationStatus("UNCERTIFIED");
        invB.setEnterpriseId(2L);
        inputInvoiceMapper.insert(invB);

        List<InputInvoiceEntity> allInvs = inputInvoiceMapper.selectList(null);

        assertTrue(allInvs.stream().anyMatch(i -> "AUDIT-INV-A-001".equals(i.getInvoiceNo())),
                "企业A的进项发票应该被查到");
        assertTrue(allInvs.stream().anyMatch(i -> "AUDIT-INV-B-001".equals(i.getInvoiceNo())),
                "⚠️ 漏洞：企业B的进项发票也被查到，说明 enterprise_id 过滤缺失");
    }

    @Test
    @DisplayName("[漏洞确认] BankStatementMapper.selectList 未过滤 enterprise_id")
    void bankStatementQuery_missingEnterpriseIdFilter() {
        BankStatementEntity bsA = new BankStatementEntity();
        bsA.setTxDate(LocalDate.of(2026, 8, 1));
        bsA.setTxType("INCOME");
        bsA.setAmount(new BigDecimal("50000.00"));
        bsA.setCounterAccount("企业A客户");
        bsA.setSummary("货款");
        bsA.setReviewStatus("PENDING");
        bsA.setEnterpriseId(1L);
        bankStatementMapper.insert(bsA);

        BankStatementEntity bsB = new BankStatementEntity();
        bsB.setTxDate(LocalDate.of(2026, 8, 1));
        bsB.setTxType("INCOME");
        bsB.setAmount(new BigDecimal("100000.00"));
        bsB.setCounterAccount("企业B客户");
        bsB.setSummary("货款");
        bsB.setReviewStatus("PENDING");
        bsB.setEnterpriseId(2L);
        bankStatementMapper.insert(bsB);

        List<BankStatementEntity> allBs = bankStatementMapper.selectList(null);

        assertTrue(allBs.stream().anyMatch(b -> b.getEnterpriseId() == 1L),
                "企业A的银行流水应该被查到");
        assertTrue(allBs.stream().anyMatch(b -> b.getEnterpriseId() == 2L),
                "⚠️ 漏洞：企业B的银行流水也被查到，说明 enterprise_id 过滤缺失");
    }

    @Test
    @DisplayName("[漏洞确认] OutputInvoiceMapper.selectList 未过滤 enterprise_id")
    void outputInvoiceQuery_missingEnterpriseIdFilter() {
        OutputInvoiceEntity invA = new OutputInvoiceEntity();
        invA.setInvoiceNo("AUDIT-OUT-A-001");
        invA.setInvoiceDate(LocalDate.of(2026, 8, 1));
        invA.setPeriod("202608");
        invA.setCustomerName("客户A");
        invA.setAmount(new BigDecimal("10000.00"));
        invA.setTaxRate(new BigDecimal("0.13"));
        invA.setTaxAmount(new BigDecimal("1300.00"));
        invA.setTotalAmount(new BigDecimal("11300.00"));
        invA.setInvoiceType("SPECIAL");
        invA.setStatus("PENDING_CONFIRM");
        invA.setEnterpriseId(1L);
        outputInvoiceMapper.insert(invA);

        OutputInvoiceEntity invB = new OutputInvoiceEntity();
        invB.setInvoiceNo("AUDIT-OUT-B-001");
        invB.setInvoiceDate(LocalDate.of(2026, 8, 1));
        invB.setPeriod("202608");
        invB.setCustomerName("客户B");
        invB.setAmount(new BigDecimal("20000.00"));
        invB.setTaxRate(new BigDecimal("0.13"));
        invB.setTaxAmount(new BigDecimal("2600.00"));
        invB.setTotalAmount(new BigDecimal("22600.00"));
        invB.setInvoiceType("SPECIAL");
        invB.setStatus("PENDING_CONFIRM");
        invB.setEnterpriseId(2L);
        outputInvoiceMapper.insert(invB);

        List<OutputInvoiceEntity> allInvs = outputInvoiceMapper.selectList(null);

        assertTrue(allInvs.stream().anyMatch(i -> "AUDIT-OUT-A-001".equals(i.getInvoiceNo())),
                "企业A的销项发票应该被查到");
        assertTrue(allInvs.stream().anyMatch(i -> "AUDIT-OUT-B-001".equals(i.getInvoiceNo())),
                "⚠️ 漏洞：企业B的销项发票也被查到，说明 enterprise_id 过滤缺失");
    }

    @Test
    @DisplayName("[漏洞确认] AssetCardMapper.selectList 未过滤 enterprise_id")
    void assetCardQuery_missingEnterpriseIdFilter() {
        AssetCardEntity cardA = new AssetCardEntity();
        cardA.setAssetCode("AUDIT-ASSET-A-001");
        cardA.setAssetName("企业A服务器");
        cardA.setOriginalValue(new BigDecimal("50000.00"));
        cardA.setStatus("IN_USE");
        cardA.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        cardA.setEnterpriseId(1L);
        assetCardMapper.insert(cardA);

        AssetCardEntity cardB = new AssetCardEntity();
        cardB.setAssetCode("AUDIT-ASSET-B-001");
        cardB.setAssetName("企业B打印机");
        cardB.setOriginalValue(new BigDecimal("3000.00"));
        cardB.setStatus("IN_USE");
        cardB.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        cardB.setEnterpriseId(2L);
        assetCardMapper.insert(cardB);

        List<AssetCardEntity> allCards = assetCardMapper.selectList(null);

        assertTrue(allCards.stream().anyMatch(c -> "AUDIT-ASSET-A-001".equals(c.getAssetCode())),
                "企业A的资产卡片应该被查到");
        assertTrue(allCards.stream().anyMatch(c -> "AUDIT-ASSET-B-001".equals(c.getAssetCode())),
                "⚠️ 漏洞：企业B的资产卡片也被查到，说明 enterprise_id 过滤缺失");
    }
}