package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.service.PrepaymentService;
import com.huicai.sme.arap.vo.PrepaymentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 批次 6：预付款出参 4 个「恒空白字段」的真库回归锁。
 *
 * <p><b>缺陷（本次修掉的真实前端 bug）</b>：{@code PrepaymentList.vue} 一直读 4 个字段，
 * 而 {@code t_prepayment} <b>一个都没有</b>：
 * <pre>
 *   前端字段        真实来源                                      修复前
 *   prepayNo      → doc_id → t_business_doc.doc_no  （跨表）      恒空白
 *   vendorName    → vendor_id → t_vendor.name       （跨表）      恒空白
 *   customerName  → customer_id → t_customer.name   （跨表）      恒空白
 *   appliedAmount → settled_amount                  （改名）        恒 0
 * </pre>
 * ⇒ 页面的「单据编号 / 供应商 / 客户 / 已核销金额」四列一直是空的。
 *
 * <p><b>为什么必须是真库测试</b>：这 4 个字段里有 3 个来自<b>跨表关联</b>，
 * Mock 只能证明 Service 的搬运分支，<b>证明不了关联查得到、表名列名对</b> ——
 * 而后者恰恰是 {@code AGENTS §4.2 第 7 条}（JOIN 里的表名必须与库实际一致）
 * 与 {@code §4.3 第 17 条}（key 大小写/别名）真正咬人的地方。
 * 已有先例：{@code ArapSettlementMapper.pageWithPartyName} 曾把 {@code t_supplier}
 * 写成实际不存在的表，而 Mock 夹具照样全绿。
 */
@DisplayName("P102 批次 6 预付款出参：单据号/供应商名/客户名/已核销金额真库有值")
class PrepaymentViewRealDBTest extends AbstractMapperTest {

    private static final String MARK = "P102B6-PREPAY-";

    @Autowired
    private PrepaymentService prepaymentService;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    private BusinessDocEntity newDoc(String no) {
        BusinessDocEntity d = new BusinessDocEntity();
        d.setDocNo(no);
        d.setDocType("PREPAYMENT");
        d.setDocDate(LocalDate.now());
        d.setPeriod("209912");
        d.setStatus("DRAFT");
        d.setAmount(new BigDecimal("3000.00"));
        businessDocMapper.insert(d);
        return d;
    }

    private PrepaymentEntity newPrepayment(Long docId, Long vendorId, Long customerId,
                                           String marker, BigDecimal settled) {
        PrepaymentEntity e = new PrepaymentEntity();
        e.setPeriod("209912");
        e.setTxDate(LocalDate.now());
        e.setAmount(new BigDecimal("1000.00"));
        e.setSettledAmount(settled);
        e.setSummary(marker);
        e.setDocId(docId);
        e.setVendorId(vendorId);
        e.setCustomerId(customerId);
        return prepaymentService.create(e);
    }

    /** 在分页结果里定位本用例造的记录（不用裸 count，避免与种子/其它用例数据混淆） */
    private PrepaymentVO findInPage(IPage<PrepaymentVO> page, String marker) {
        return page.getRecords().stream()
                .filter(v -> marker.equals(v.getSummary()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("分页结果里找不到 summary=" + marker
                        + " 的记录，records=" + page.getRecords().size()));
    }

    @Test
    @DisplayName("正向：单据号/供应商名/客户名/已核销金额四项都有真实值")
    void fourColumnsCarryRealData() {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        BusinessDocEntity doc = newDoc(MARK + "DOC-1");
        String marker = MARK + "A";
        PrepaymentEntity saved = newPrepayment(doc.getId(), vendorId, customerId, marker,
                new BigDecimal("250.00"));
        assertNotNull(saved.getId(), "未落库");

        IPage<PrepaymentVO> page = prepaymentService.pageView(
                null, null, null, null, null, null, 1, 200);
        PrepaymentVO vo = findInPage(page, marker);

        // ① 单据号：跨表 doc_id → t_business_doc.doc_no
        assertEquals(MARK + "DOC-1", vo.getPrepayNo(),
                "单据号应来自 t_business_doc.doc_no（修复前该列恒空白）");
        // ② 供应商名：跨表 vendor_id → t_vendor.name
        assertEquals("测试供应商-" + DEFAULT_ENTERPRISE_ID, vo.getVendorName(),
                "供应商名应来自 t_vendor.name（修复前该列恒空白）");
        // ③ 客户名：跨表 customer_id → t_customer.name
        assertEquals("测试客户-" + DEFAULT_ENTERPRISE_ID, vo.getCustomerName(),
                "客户名应来自 t_customer.name（修复前该列恒空白）");
        // ④ 已核销金额：前端原字段名 appliedAmount，真实列名 settled_amount
        assertEquals(0, new BigDecimal("250.00").compareTo(vo.getSettledAmount()),
                "已核销金额应来自 settled_amount（前端原读 appliedAmount，恒为 undefined ⇒ 显示 0）");
        // 未核销金额应等于 金额 - 已核销（页面原先用减法现算，此处锁住该不变量）
        assertEquals(0, new BigDecimal("750.00").compareTo(vo.getUnsettledAmount()),
                "未核销金额 = amount - settled_amount");

        // 正控：证明关联确实命中了库里的行，而不是碰巧相等
        assertNotNull(vendorMapper.selectById(vendorId), "对照组：供应商行必须真实存在");
        assertNotNull(customerMapper.selectById(customerId), "对照组：客户行必须真实存在");
    }

    @Test
    @DisplayName("负向：doc_id 为空则单据号为 null —— 不许用兜底字符串伪装成有数据")
    void prepayNoIsNullWhenNoDocId() {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        String marker = MARK + "B";
        newPrepayment(null, null, null, marker, BigDecimal.ZERO);

        IPage<PrepaymentVO> page = prepaymentService.pageView(
                null, null, null, null, null, null, 1, 200);
        PrepaymentVO vo = findInPage(page, marker);

        assertNull(vo.getPrepayNo(),
                "手工建单的预付款没有 doc_id，单据号只能是 null。"
                        + "写 '未生成' 之类兜底值会把「来源缺失」伪装成正常数据。");
        assertNull(vo.getVendorName(), "无 vendorId 时供应商名必须是 null");
        assertNull(vo.getCustomerName(), "无 customerId 时客户名必须是 null");
    }

    @Test
    @DisplayName("负向：关联行不存在时名称为 null，不抛异常也不返回脏值")
    void nameIsNullWhenReferencedRowMissing() {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        String marker = MARK + "C";
        // 指向一个不存在的 vendor/customer：DB 无 FK 约束，故可构造此状态
        newPrepayment(null, 987654321L, 987654322L, marker, BigDecimal.ZERO);

        IPage<PrepaymentVO> page = prepaymentService.pageView(
                null, null, null, null, null, null, 1, 200);
        PrepaymentVO vo = findInPage(page, marker);

        assertNull(vo.getVendorName(), "关联行不存在时不得返回任何占位名称");
        assertNull(vo.getCustomerName(), "关联行不存在时不得返回任何占位名称");
        assertTrue(vo.getId() != null, "记录本身仍应正常返回（不能因补全失败而整条丢失）");
    }

    @Test
    @DisplayName("负向：viewOf 与 pageView 补全口径一致（详情页与列表页不能一个有一个没有）")
    void viewOfAndPageViewAgreeOnNames() {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        BusinessDocEntity doc = newDoc(MARK + "DOC-2");
        String marker = MARK + "D";
        PrepaymentEntity saved = newPrepayment(doc.getId(), vendorId, null, marker, BigDecimal.ZERO);

        PrepaymentVO single = prepaymentService.viewOf(saved);
        IPage<PrepaymentVO> page = prepaymentService.pageView(
                null, null, null, null, null, null, 1, 200);
        PrepaymentVO inPage = findInPage(page, marker);

        assertEquals(single.getPrepayNo(), inPage.getPrepayNo(), "两处单据号必须一致");
        assertEquals(single.getVendorName(), inPage.getVendorName(), "两处供应商名必须一致");
        assertEquals(MARK + "DOC-2", single.getPrepayNo(), "单条视图也要补全，不能只补分页");
    }

    @Test
    @DisplayName("负向：viewList 批量补全（抵扣对话框用），空列表不 NPE")
    void viewListEnrichesAndHandlesEmpty() {
        useEnterprise(DEFAULT_ENTERPRISE_ID);
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        String marker = MARK + "E";
        newPrepayment(null, null, customerId, marker, BigDecimal.ZERO);

        List<PrepaymentEntity> open = prepaymentService.getOpenPrepaymentsForCustomer(
                DEFAULT_ENTERPRISE_ID.equals(customerId) ? customerId : customerId);
        List<PrepaymentVO> views = prepaymentService.viewList(open);
        assertNotNull(views, "viewList 不得返回 null");
        assertTrue(views.stream().noneMatch(v -> MARK.equals(v.getSummary())
                        && v.getCustomerName() == null),
                "抵扣对话框依赖的列表也必须补全客户名，否则该处同样空白");

        assertTrue(prepaymentService.viewList(java.util.Collections.emptyList()).isEmpty(),
                "空列表应返回空列表而不是 null");
        assertNull(prepaymentService.viewList(null), "null 入参按 null 返回，保持与 from() 一致");
    }
}