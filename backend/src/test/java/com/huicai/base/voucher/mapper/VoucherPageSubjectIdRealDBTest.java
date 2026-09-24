package com.huicai.base.voucher.mapper;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.common.test.AbstractMapperTest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * selectVoucherPage 的 subjectId 过滤真实 DB 测试（P89-C 数字穿透）。
 *
 * 穿透链路：科目余额表点击科目 → 凭证列表按该科目过滤。
 * 这是穿透的唯一数据来源，SQL 写错用户会直接看到错误的凭证列表，且报错很难察觉，
 * 所以必须走真实 DB 验证（mock 只能验证参数透传，验证不了 SQL）。
 *
 * 覆盖三个必须正确的点：
 * 1. 只返回"含该科目分录"的凭证 —— 同科目多分录的凭证不应重复出现（IN 子查询天然去重）
 * 2. 已删除分录(deleted=1)不参与匹配 —— 凭证不应被错误地查出来
 * 3. subjectId=null 时退化为原查询 —— 不能破坏既有列表查询行为
 */
class VoucherPageSubjectIdRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private VoucherEntryMapper voucherEntryMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    private Long insertSubject(int code) {
        Subject s = new Subject();
        s.setCode("ZPAGE_" + code);
        s.setName("穿透测试科目" + code);
        s.setLevel(1);
        s.setDirection("debit");
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(1L);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    private VoucherEntity insertVoucher(String period, String suffix) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("V_" + period + "_" + suffix);
        v.setPeriod(period);
        v.setStatus("POSTED");
        v.setVoucherTypeId(1L);
        v.setEnterpriseId(1L);
        v.setDeleted(0);
        assertEquals(1, voucherMapper.insert(v));
        assertNotNull(v.getId());
        return v;
    }

    private VoucherEntryEntity insertEntry(Long voucherId, Long subjectId, String debit, String credit) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        e.setSummary("穿透测试分录");
        e.setSortOrder(1);
        e.setEnterpriseId(1L);
        e.setDeleted(0);
        assertEquals(1, voucherEntryMapper.insert(e));
        return e;
    }

    private static long count(IPage<VoucherEntity> page) {
        return page == null ? 0 : page.getRecords().size();
    }

    @Test
    void subjectId_只返回含该科目分录的凭证() {
        Long sHit = insertSubject(1);
        Long sMiss = insertSubject(2);

        // v1: 含 sHit 的两条分录（同一凭证）→ 应返回，且不重复
        VoucherEntity v1 = insertVoucher("202610", "A");
        insertEntry(v1.getId(), sHit, "100", null);
        insertEntry(v1.getId(), sHit, "200", null);

        // v2: 含 sHit 的一条分录 → 应返回
        VoucherEntity v2 = insertVoucher("202610", "B");
        insertEntry(v2.getId(), sHit, "300", null);

        // v3: 只有 sMiss 的分录 → 不应返回
        VoucherEntity v3 = insertVoucher("202610", "C");
        insertEntry(v3.getId(), sMiss, "999", null);

        // v4: 同一凭证同时含 sHit 和 sMiss → 应返回（命中 sHit 即可）
        VoucherEntity v4 = insertVoucher("202610", "D");
        insertEntry(v4.getId(), sHit, "400", null);
        insertEntry(v4.getId(), sMiss, "500", null);

        IPage<VoucherEntity> page = voucherMapper.selectVoucherPage(
                new Page<>(1, 100), "202610", null, null, null, null, null, sHit, null, null, null);

        assertEquals(3, count(page), "应返回含 sHit 的 v1/v2/v4 三笔，不含只有 sMiss 的 v3");
        assertTrue(page.getRecords().stream().anyMatch(v -> v.getId().equals(v1.getId())));
        assertTrue(page.getRecords().stream().anyMatch(v -> v.getId().equals(v2.getId())));
        assertTrue(page.getRecords().stream().anyMatch(v -> v.getId().equals(v4.getId())));
        assertTrue(page.getRecords().stream().noneMatch(v -> v.getId().equals(v3.getId())),
                "只有 sMiss 分录的凭证不应出现");
        // v1 有两条 sHit 分录，IN 子查询不能导致凭证重复
        assertEquals(1, page.getRecords().stream()
                .filter(v -> v.getId().equals(v1.getId())).count(),
                "同一凭证含多条匹配分录时应去重");
    }

    @Test
    void subjectId_排除已删除分录() {
        Long sId = insertSubject(3);

        // 正常凭证：含 sId 分录
        VoucherEntity vOk = insertVoucher("202611", "OK");
        insertEntry(vOk.getId(), sId, "100", null);

        // 凭证只有"已删除分录"引用 sId → 不应被匹配出来
        VoucherEntity vDead = insertVoucher("202611", "DEAD");
        VoucherEntryEntity deadEntry = insertEntry(vDead.getId(), sId, "200", null);
        assertEquals(1, voucherEntryMapper.deleteById(deadEntry.getId()));

        IPage<VoucherEntity> page = voucherMapper.selectVoucherPage(
                new Page<>(1, 100), "202611", null, null, null, null, null, sId, null, null, null);

        assertEquals(1, count(page), "只有已删除分录的凭证不应被查出");
        assertTrue(page.getRecords().stream().anyMatch(v -> v.getId().equals(vOk.getId())));
        assertTrue(page.getRecords().stream().noneMatch(v -> v.getId().equals(vDead.getId())));
    }

    @Test
    void subjectId_为空时退化为原查询_不破坏既有列表行为() {
        Long s1 = insertSubject(4);
        Long s2 = insertSubject(5);

        VoucherEntity v1 = insertVoucher("202612", "X1");
        insertEntry(v1.getId(), s1, "100", null);
        VoucherEntity v2 = insertVoucher("202612", "X2");
        insertEntry(v2.getId(), s2, "200", null);

        // subjectId 传 null → 不应做任何科目过滤，期间内全部返回
        IPage<VoucherEntity> page = voucherMapper.selectVoucherPage(
                new Page<>(1, 100), "202612", null, null, null, null, null, null, null, null, null);

        assertEquals(2, count(page), "subjectId=null 时应退化为普通期间查询，返回该期间全部凭证");
    }

    @Test
    void subjectId_与期间联合过滤_不串期间() {
        Long sId = insertSubject(6);

        VoucherEntity vCur = insertVoucher("202601", "CUR");
        insertEntry(vCur.getId(), sId, "100", null);
        VoucherEntity vOther = insertVoucher("202512", "OTHER");
        insertEntry(vOther.getId(), sId, "999", null);

        IPage<VoucherEntity> page = voucherMapper.selectVoucherPage(
                new Page<>(1, 100), "202601", null, null, null, null, null, sId, null, null, null);

        assertEquals(1, count(page), "科目过滤必须叠加期间过滤");
        assertTrue(page.getRecords().stream().anyMatch(v -> v.getId().equals(vCur.getId())));
        assertTrue(page.getRecords().stream().noneMatch(v -> v.getId().equals(vOther.getId())),
                "其他期间即使命中该科目也不应返回");
    }
}
