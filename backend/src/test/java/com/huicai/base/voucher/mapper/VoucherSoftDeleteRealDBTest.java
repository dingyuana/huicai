package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2（REQ-2026-134 / P107）：凭证分录删除必须逻辑删除，不得物理 DELETE。
 *
 * 缺陷：VoucherEntryMapper.deleteByVoucherId 写的是物理 DELETE，
 * 凭证一旦删除其全部审计痕迹（分录）不可恢复，违反铁律 #12 逻辑删除。
 * 同时 selectByVoucherId 缺 deleted = 0 过滤，会把已软删分录读回。
 */
@SlowTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoucherSoftDeleteRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherMapper voucherMapper;
    @Autowired
    private VoucherEntryMapper voucherEntryMapper;
    @Autowired
    private SubjectMapper subjectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long subjectId;

    private Long ensureSubject() {
        if (subjectId != null) {
            return subjectId;
        }
        String code = "D2-SUBJ-1";
        Subject exist = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code).last("LIMIT 1"));
        if (exist != null) {
            subjectId = exist.getId();
            return subjectId;
        }
        Subject s = new Subject();
        s.setCode(code);
        s.setName("D2测试科目");
        s.setLevel(1);
        s.setDirection("debit");
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(1L);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        subjectId = s.getId();
        return subjectId;
    }

    private VoucherEntity newVoucher(String tag) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("D2-" + tag + "-" + System.nanoTime());
        v.setPeriod("202608");
        v.setStatus("DRAFT");
        v.setVoucherTypeId(1L);
        v.setEnterpriseId(1L);
        assertEquals(1, voucherMapper.insert(v));
        assertNotNull(v.getId());
        return v;
    }

    private VoucherEntryEntity newEntry(Long voucherId, String tag) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(ensureSubject());
        e.setDebit(new BigDecimal("100.00"));
        e.setSummary("D2 " + tag);
        e.setSortOrder(1);
        e.setEnterpriseId(1L);
        assertEquals(1, voucherEntryMapper.insert(e));
        return e;
    }

    private int physicalEntryCount(Long voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_entry WHERE voucher_id = ?", Integer.class, voucherId);
    }

    @Test
    void deleteByVoucherId_应逻辑删除_物理行仍在() {
        VoucherEntity v = newVoucher("ENT");
        VoucherEntryEntity e = newEntry(v.getId(), "A");
        assertEquals(1, physicalEntryCount(v.getId()));

        voucherEntryMapper.deleteByVoucherId(v.getId());

        // 负向断言：物理行必须仍在（软删），行数不变
        assertEquals(1, physicalEntryCount(v.getId()),
                "deleteByVoucherId 不得物理删除，审计痕迹必须保留");
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_voucher_entry WHERE id = ?", Integer.class, e.getId());
        assertEquals(1, deletedFlag, "分录 deleted 标志位应置 1");

        // 负向断言：业务查询不得再返回该分录
        assertTrue(voucherEntryMapper.selectByVoucherId(v.getId()).isEmpty(),
                "selectByVoucherId 必须过滤 deleted = 1 的分录");
        assertTrue(voucherEntryMapper.selectById(e.getId()) == null,
                "MyBatis-Plus 逻辑删除后 selectById 应返回 null");
    }

    @Test
    void deleteByVoucherId_重复调用应幂等_不回退标志位() {
        VoucherEntity v = newVoucher("IDEM");
        newEntry(v.getId(), "A");

        voucherEntryMapper.deleteByVoucherId(v.getId());
        voucherEntryMapper.deleteByVoucherId(v.getId());

        assertEquals(1, physicalEntryCount(v.getId()), "重复软删不得改动物理行数");
        assertTrue(voucherEntryMapper.selectByVoucherId(v.getId()).isEmpty());
    }

    @Test
    void 父凭证逻辑删除后分录应级联不可见_但物理保留() {
        VoucherEntity v = newVoucher("CASC");
        VoucherEntryEntity e = newEntry(v.getId(), "A");

        // 父凭证走 MP 逻辑删除（@TableLogic 继承自 BaseEntity）
        assertEquals(1, voucherMapper.deleteById(v.getId()));

        assertTrue(voucherMapper.selectById(v.getId()) == null,
                "父凭证逻辑删除后应查不到");
        assertEquals(1, physicalEntryCount(v.getId()),
                "父凭证软删不得 CASCADE 物理清掉分录审计痕迹");
        assertTrue(voucherEntryMapper.selectByVoucherId(v.getId()).isEmpty(),
                "父凭证已删，其分录不得对业务查询可见");
        // 直接查库可见，证明是软删而非物理删
        Integer rowDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_voucher_entry WHERE id = ?", Integer.class, e.getId());
        assertNotNull(rowDeleted, "分录行应仍在库中可审计");
    }
}
