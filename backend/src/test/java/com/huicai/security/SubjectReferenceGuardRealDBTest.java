package com.huicai.security;

import com.huicai.base.system.service.SubjectService;
import com.huicai.base.system.service.impl.SubjectServiceImpl;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P134 —— 引用完整性守卫（{@code t_subject}）真库守卫。
 *
 * <p><b>为什么必须有真库测试</b>：本守卫的价值完全建立在「DB 里确实有引用行」之上。
 * 用 Mock 造数据只能证明切面代码分支可达，<b>证明不了外键真的不会拦截</b> ——
 * 而那恰恰是本缺陷的根因（逻辑删除使 FK 永不触发）。
 * 若只在 Mock 下测，一个「其实 FK 会拦、守卫多余」的世界也能全绿，
 * 而真实世界（软删）里守卫缺失就是数据损坏。</p>
 */
class SubjectReferenceGuardRealDBTest extends AbstractMapperTest {

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 造一个科目；企业走上下文，不在实体上硬设（§4.5 第 23 条）。 */
    private Long createSubject(String suffix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, "
                        + "is_active, enterprise_id, deleted) "
                        + "VALUES (?, ?, NULL, 1, 'debit', true, true, ?, 0) RETURNING id",
                Long.class, "P134." + suffix, "P134科目" + suffix, currentEnterprise());
    }

    private Long createVoucherWithEntry(Long subjectId, String suffix) {
        Long voucherId = jdbcTemplate.queryForObject(
                "INSERT INTO t_voucher (voucher_no, period, voucher_type_id, status, total_debit, "
                        + "total_credit, enterprise_id, deleted) "
                        + "VALUES (?, '202601', 1, 'DRAFT', 100, 100, ?, 0) RETURNING id",
                Long.class, "P134-V" + suffix, currentEnterprise());
        jdbcTemplate.update(
                "INSERT INTO t_voucher_entry (voucher_id, subject_id, debit, credit, sort_order, "
                        + "enterprise_id, deleted) VALUES (?, ?, 100, 0, 1, ?, 0)",
                voucherId, subjectId, currentEnterprise());
        return voucherId;
    }

    private int deletedFlag(Long subjectId) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_subject WHERE id = ?", Integer.class, subjectId);
    }

    private int entryCount(Long subjectId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_entry WHERE subject_id = ? AND deleted = 0",
                Integer.class, subjectId);
    }

    private Long currentEnterprise() {
        return com.huicai.common.context.EnterpriseContextHolder.get();
    }

    @Test
    @DisplayName("AT-134-1 被凭证引用的科目删除被拒")
    void referencedSubjectCannotBeDeleted() {
        Long subjectId = createSubject("REF1");
        createVoucherWithEntry(subjectId, "REF1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> subjectService.delete(subjectId));
        assertTrue(ex.getMessage().contains("被以下数据引用"),
                "错误消息须说明是引用问题，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("AT-134-2 删除被拒后数据必须原封不动（负向断言）")
    void rejectedDeleteLeavesDataUntouched() {
        Long subjectId = createSubject("REF2");
        createVoucherWithEntry(subjectId, "REF2");
        int before = entryCount(subjectId);

        assertThrows(BusinessException.class, () -> subjectService.delete(subjectId));

        // 🔴 只验「抛了异常」不够：那也兼容「先删再校验」的实现（数据已改坏）。
        assertEquals(0, deletedFlag(subjectId), "科目不得被标记删除");
        assertEquals(before, entryCount(subjectId), "引用它的分录数不得变化");
    }

    @Test
    @DisplayName("AT-134-3 无任何引用的科目可以删除（正控：守卫不得把功能全打挂）")
    void unreferencedSubjectCanBeDeleted() {
        Long subjectId = createSubject("FREE");
        subjectService.delete(subjectId);
        assertEquals(1, deletedFlag(subjectId), "无引用科目应被成功逻辑删除");
    }

    @Test
    @DisplayName("AT-134-4 错误消息含子表中文名与条数")
    void messageNamesChildTableAndCount() {
        Long subjectId = createSubject("MSG");
        createVoucherWithEntry(subjectId, "MSG");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> subjectService.delete(subjectId));
        assertTrue(ex.getMessage().contains("凭证分录"),
                "消息须指名子表，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("1条"),
                "消息须给出条数，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("AT-134-6 其它企业的引用不得导致误杀")
    void referencesFromOtherEnterpriseDoNotBlock() {
        Long subjectId = createSubject("ISO");

        // 在**另一个企业**下造引用
        useEnterprise(990077L);
        try {
            Long otherSubject = createSubject("OTHER");
            createVoucherWithEntry(otherSubject, "OTHER");
        } finally {
            useEnterprise(DEFAULT_ENTERPRISE_ID);
        }

        // 本企业的科目与其它企业的分录无任何关系 —— 必须能删
        subjectService.delete(subjectId);
        assertEquals(1, deletedFlag(subjectId),
                "其它企业的引用不应导致本企业科目被拒（AT-134-6）");
    }

    @Test
    @DisplayName("守卫自证：@ReferenceGuard 注解确实挂在 delete 上（防注解被摘掉后全绿）")
    void guardAnnotationIsPresentOnDelete() throws Exception {
        var m = SubjectServiceImpl.class.getMethod("delete", Long.class);
        var guard = m.getAnnotation(com.huicai.common.security.ReferenceGuard.class);
        assertTrue(guard != null, "delete 方法必须标注 @ReferenceGuard");
        assertTrue(guard.children().length >= 5,
                "科目至少被 5 类数据引用，守卫须逐一声明，实际声明数：" + guard.children().length);
    }
}