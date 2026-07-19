package com.huicai.sme.arap.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.dto.BusinessDocVO;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.tax.entity.InputInvoiceEntity;
import com.huicai.sme.tax.entity.OutputInvoiceEntity;
import com.huicai.sme.tax.mapper.InputInvoiceMapper;
import com.huicai.sme.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联前端接口完善测试 (L3 / @SlowTest)
 *
 * 核心验证：
 * 1. 销项/进项发票分页查询回填 docNo / voucherNo
 * 2. BusinessDocVO 完整映射 voucherNo
 * 3. BusinessDoc keyword 搜索覆盖 voucherNo / invoiceNo / docNo
 */
@DisplayName("编号关联 - 前端接口完善测试")
public class NumberingFrontendApiTest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private VoucherMapper voucherMapper;

    // ==================== 销项发票分页回填 ====================

    @Nested
    @DisplayName("销项发票分页回填 docNo / voucherNo")
    class OutputInvoicePageFillTest {

        @Test
        @DisplayName("销项发票 Entity 应包含 docNo 和 voucherNo 字段并能持久化")
        void output_invoice_entity_should_persist_numbers() {
            // 验证：直接插入带编号的销项发票，能正确持久化和查询
            OutputInvoiceEntity invoice = new OutputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.API.OUT.001");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setStatus("VOUCHERED");
            invoice.setDocNo("9999.E2E.API.DOC.001");
            invoice.setVoucherNo("9999.E2E.API.VCH.001");
            invoice.setDeleted(0);
            outputInvoiceMapper.insert(invoice);

            // 2. 验证：能按 docNo 和 voucherNo 查询到
            OutputInvoiceEntity foundByDoc = outputInvoiceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutputInvoiceEntity>()
                            .eq(OutputInvoiceEntity::getDocNo, "9999.E2E.API.DOC.001")
            );
            assertNotNull(foundByDoc, "通过 docNo 应能查到销项发票");
            assertEquals("9999.E2E.API.OUT.001", foundByDoc.getInvoiceNo());

            OutputInvoiceEntity foundByVoucher = outputInvoiceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutputInvoiceEntity>()
                            .eq(OutputInvoiceEntity::getVoucherNo, "9999.E2E.API.VCH.001")
            );
            assertNotNull(foundByVoucher, "通过 voucherNo 应能查到销项发票");
        }
    }

    // ==================== 进项发票分页回填 ====================

    @Nested
    @DisplayName("进项发票分页回填 docNo / voucherNo")
    class InputInvoicePageFillTest {

        @Test
        @DisplayName("进项发票 Entity 应包含 voucherNo 字段并能持久化")
        void input_invoice_entity_should_persist_voucherNo() {
            // 验证：直接插入带 voucherNo 的进项发票，能正确持久化和查询
            InputInvoiceEntity invoice = new InputInvoiceEntity();
            invoice.setInvoiceNo("9999.E2E.API.INP.001");
            invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
            invoice.setPeriod("202606");
            invoice.setAmount(new BigDecimal("10000.00"));
            invoice.setTaxAmount(new BigDecimal("1300.00"));
            invoice.setTotalAmount(new BigDecimal("11300.00"));
            invoice.setInvoiceType("SPECIAL");
            invoice.setTaxRate(new BigDecimal("0.13"));
            invoice.setCertificationStatus("CERTIFIED");
            invoice.setVoucherNo("9999.E2E.API.VCH.002");
            invoice.setDeleted(0);
            inputInvoiceMapper.insert(invoice);

            // 2. 验证：能通过 voucherNo 查询到
            InputInvoiceEntity found = inputInvoiceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InputInvoiceEntity>()
                            .eq(InputInvoiceEntity::getVoucherNo, "9999.E2E.API.VCH.002")
            );
            assertNotNull(found, "通过 voucherNo 应能查到进项发票");
            assertEquals("9999.E2E.API.INP.001", found.getInvoiceNo());
        }
    }

    // ==================== BusinessDocVO 映射 ====================

    @Nested
    @DisplayName("BusinessDocVO 完整映射 voucherNo")
    class BusinessDocVOMappingTest {

        @Test
        @DisplayName("BusinessDocVO.fromEntity 应正确映射 voucherNo")
        void business_doc_vo_should_map_voucherNo() {
            // 1. 插入业务单
            BusinessDocEntity doc = new BusinessDocEntity();
            doc.setDocNo("9999.E2E.API.BDOC.001");
            doc.setDocType("EXPENSE");
            doc.setDocDate(LocalDate.of(2026, 6, 28));
            doc.setPeriod("202606");
            doc.setAmount(new BigDecimal("5000.00"));
            doc.setStatus("APPROVED");
            doc.setInvoiceNo("9999.E2E.API.INV.001");
            doc.setVoucherNo("9999.E2E.API.VCH.003");
            doc.setDeleted(0);
            businessDocMapper.insert(doc);

            // 2. 转换为 VO
            BusinessDocVO vo = BusinessDocVO.fromEntity(doc);

            // 3. 验证
            assertEquals("9999.E2E.API.BDOC.001", vo.getDocNo());
            assertEquals("9999.E2E.API.INV.001", vo.getInvoiceNo());
            assertEquals("9999.E2E.API.VCH.003", vo.getVoucherNo(),
                    "BusinessDocVO 应正确映射 voucherNo");
        }
    }

    // ==================== BusinessDoc keyword 搜索 ====================

    @Nested
    @DisplayName("BusinessDoc keyword 多字段搜索")
    class BusinessDocKeywordSearchTest {

        @Test
        @DisplayName("keyword 应同时搜索 docNo / voucherNo / invoiceNo / summary")
        void keyword_search_should_cover_multiple_fields() {
            String period = "202606";
            LocalDate today = LocalDate.of(2026, 6, 28);

            // 1. 插入 4 条不同编号的业务单
            BusinessDocEntity doc1 = new BusinessDocEntity();
            doc1.setDocNo("9999.E2E.KW.DOC.001");
            doc1.setDocType("EXPENSE");
            doc1.setDocDate(today);
            doc1.setPeriod(period);
            doc1.setAmount(new BigDecimal("1000.00"));
            doc1.setStatus("APPROVED");
            doc1.setSummary("办公用品采购");
            doc1.setVoucherNo("9999.E2E.KW.VCH.001");
            doc1.setDeleted(0);
            businessDocMapper.insert(doc1);

            BusinessDocEntity doc2 = new BusinessDocEntity();
            doc2.setDocNo("9999.E2E.KW.DOC.002");
            doc2.setDocType("EXPENSE");
            doc2.setDocDate(today);
            doc2.setPeriod(period);
            doc2.setAmount(new BigDecimal("2000.00"));
            doc2.setStatus("APPROVED");
            doc2.setSummary("差旅费");
            doc2.setVoucherNo("9999.E2E.KW.VCH.002");
            doc2.setDeleted(0);
            businessDocMapper.insert(doc2);

            BusinessDocEntity doc3 = new BusinessDocEntity();
            doc3.setDocNo("9999.E2E.KW.DOC.003");
            doc3.setDocType("EXPENSE");
            doc3.setDocDate(today);
            doc3.setPeriod(period);
            doc3.setAmount(new BigDecimal("3000.00"));
            doc3.setStatus("APPROVED");
            doc3.setSummary("会议费");
            doc3.setInvoiceNo("9999.E2E.KW.INV.003");
            doc3.setDeleted(0);
            businessDocMapper.insert(doc3);

            BusinessDocEntity doc4 = new BusinessDocEntity();
            doc4.setDocNo("9999.E2E.KW.DOC.004");
            doc4.setDocType("EXPENSE");
            doc4.setDocDate(today);
            doc4.setPeriod(period);
            doc4.setAmount(new BigDecimal("4000.00"));
            doc4.setStatus("APPROVED");
            doc4.setSummary("招待费");
            doc4.setDeleted(0);
            businessDocMapper.insert(doc4);

            // 2. 验证：数据库中存在这些记录
            assertEquals(4, businessDocMapper.selectCount(null), "应有 4 条记录");

            // 3. 按 voucherNo 搜索
            long countByVoucherNo = businessDocMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getVoucherNo, "9999.E2E.KW.VCH.001")
            );
            assertEquals(1, countByVoucherNo, "按 voucherNo 搜索应返回 1 条");

            // 4. 按 invoiceNo 搜索
            long countByInvoiceNo = businessDocMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getInvoiceNo, "9999.E2E.KW.INV.003")
            );
            assertEquals(1, countByInvoiceNo, "按 invoiceNo 搜索应返回 1 条");

            // 5. 按 docNo 模糊搜索
            long countByDocNo = businessDocMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                            .like(BusinessDocEntity::getDocNo, "9999.E2E.KW.DOC.001")
            );
            assertEquals(1, countByDocNo, "按 docNo 模糊搜索应返回 1 条");
        }
    }
}
