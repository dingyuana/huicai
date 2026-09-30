package com.huicai.sme.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessDoc Mapper 真实 DB 测试.
 * ✅ 正向插入 + 5 项约束校验
 */
class BusinessDocMapperTest extends AbstractMapperTest {

    @Autowired
    private BusinessDocMapper mapper;

    private BusinessDocEntity createValidDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setDocNo("TEST-" + System.currentTimeMillis());
        e.setDocType("RECEIPT");
        e.setDocDate(LocalDate.now());
        e.setPeriod("202607");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus("DRAFT");
        e.setSource("MANUAL");
        e.setSummary("测试业务单据");
        return e;
    }

    @Test
    void insert_shouldSucceedWithAllRequiredFields() {
        BusinessDocEntity e = createValidDoc();
        mapper.insert(e);
        assertNotNull(e.getId());

        BusinessDocEntity found = mapper.selectById(e.getId());
        assertEquals("DRAFT", found.getStatus());
        assertEquals(0, found.getAmount().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void insert_shouldEnforceNotNullDocNo() {
        BusinessDocEntity e = createValidDoc();
        e.setDocNo(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "doc_no 为 NOT NULL，插入应失败");
    }

    @Test
    void insert_shouldEnforceNotNullDocType() {
        BusinessDocEntity e = createValidDoc();
        e.setDocType(null);
        assertThrows(Exception.class, () -> mapper.insert(e));
    }

    @Test
    void insert_shouldEnforceChkDocType() {
        BusinessDocEntity e = createValidDoc();
        e.setDocType("INVALID_TYPE");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "doc_type 有 CHECK 约束，INVALID_TYPE 应失败");
    }

    @Test
    void insert_shouldEnforceChkDocStatus() {
        BusinessDocEntity e = createValidDoc();
        e.setStatus("INVALID_STATUS");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "status 有 CHECK 约束，INVALID_STATUS 应失败");
    }

    @Test
    void insert_shouldEnforceUniqueDocNoType() {
        BusinessDocEntity e1 = createValidDoc();
        mapper.insert(e1);

        BusinessDocEntity e2 = createValidDoc();
        e2.setDocNo(e1.getDocNo()); // 相同 doc_no + doc_type
        e2.setDocType(e1.getDocType());
        assertThrows(Exception.class, () -> mapper.insert(e2),
                "(doc_type, doc_no) 有 UNIQUE 约束，重复应失败");
    }

    /**
     * 乐观锁：过期 version 的更新必须命中 0 行（不生效）。
     *
     * <h3>为什么必须显式构造过期 version（AGENTS §4.4 第 12 条）</h3>
     * 原写法是「插一次 → 查出 e2 → 更新 e2 → 再用 e 旧对象更新」，看似两版，
     * 实则<b>不成立</b>：
     * <ul>
     *   <li>MyBatis-Plus <b>不把 {@code @Version} 列的 DB 默认值回填</b>到插入时的内存对象，
     *       所以刚 insert 完的 {@code e.getVersion()} 仍是 {@code null}；</li>
     *   <li>同一 SqlSession 内两次 {@code selectById} 返回<b>同一个对象实例</b>（一级缓存），
     *       且 {@code updateById} 成功后新 version 会<b>回写进该对象</b> ——
     *       于是「旧对象 e」和「新对象 e2」其实是同一个，且 version 已被刷新。</li>
     * </ul>
     * 正确做法：从 DB 重新查出当前 version，再<b>手工构造</b>一个 version 落后 1 的实体。
     * 另外，<b>MyBatis-Plus 乐观锁冲突不抛异常</b>，而是把 version 条件加进 WHERE、
     * 冲突时返回 0 行，故断言应为「命中 0 行」而非 `assertThrows`。
     */
    @Test
    void update_shouldEnforceVersionOptimisticLock() {
        BusinessDocEntity e = createValidDoc();
        mapper.insert(e);

        // 拿到 DB 中的真实 version（MyBatis-Plus 不会回填，必须重查）
        BusinessDocEntity fresh = mapper.selectById(e.getId());
        assertNotNull(fresh);
        int currentVersion = fresh.getVersion();

        // 第一次更新：合法，version 递增
        fresh.setSummary("第一次修改");
        assertEquals(1, mapper.updateById(fresh));
        assertEquals(currentVersion + 1, fresh.getVersion(), "乐观锁应自动递增 version");

        // 显式构造一个 version 已过期的实体
        BusinessDocEntity stale = new BusinessDocEntity();
        stale.setId(e.getId());
        stale.setVersion(currentVersion);
        stale.setSummary("用过期 version 修改");
        // 其余 NOT NULL 列必须补齐，否则先撞 NOT NULL 约束而非乐观锁
        stale.setDocNo(e.getDocNo());
        stale.setDocType(e.getDocType());
        stale.setDocDate(e.getDocDate());
        stale.setPeriod(e.getPeriod());
        stale.setAmount(e.getAmount());
        stale.setStatus("DRAFT");
        stale.setDeleted(0);

        // 过期 version 更新命中 0 行。
        // 注意：MyBatis-Plus 的乐观锁**不抛异常**，只把 version 条件加进 WHERE，
        // 冲突时返回 0 行受影响；原断言「应抛 OptimisticLockingFailureException」是错的方向。
        assertEquals(0, mapper.updateById(stale), "乐观锁：过期 version 更新应命中 0 行");

        // 负向断言：过期更新不应生效
        BusinessDocEntity after = mapper.selectById(e.getId());
        assertEquals("第一次修改", after.getSummary(), "过期 version 的更新不应生效");
    }
}