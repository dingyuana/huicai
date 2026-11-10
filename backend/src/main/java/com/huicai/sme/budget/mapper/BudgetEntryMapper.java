package com.huicai.sme.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.budget.entity.BudgetEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BudgetEntryMapper extends BaseMapper<BudgetEntryEntity> {

    /**
     * P142（D-6，收口 REQ-2026-126 遗留）：显式 AS 别名替代原先 {@code be.*} 依赖
     * snake_case key + Service 侧 {@code pick()} 双读的治标方案。
     * PostgreSQL 带引号别名保留大小写 ⇒ Map key 即 camelCase，读写双方同源。
     */
    @Select("""
        SELECT be.*, be.used_amount AS "usedAmount", be.control_type AS "controlType",
               b.period, s.code AS subject_code, s.name AS subject_name
        FROM t_budget_entry be
        LEFT JOIN t_budget b ON b.id = be.budget_id
        LEFT JOIN t_subject s ON s.id = be.subject_id
        WHERE be.subject_id = #{subjectId}
          AND b.period = #{period}
          AND b.deleted = 0
          AND b.status IN ('APPROVED', 'ACTIVE')
    """)
    List<Map<String, Object>> findBySubjectAndPeriod(@Param("subjectId") Long subjectId,
                                                      @Param("period") String period);

    /**
     * P142（D-5A）：与 {@link #findBySubjectAndPeriod} 同谓词，但加行锁——
     * 过账钩子（enforcePost/releasePost）在事务内先锁条目行再算余额+回写，
     * 「先查后写」非原子窗口即被行锁封死。检查端点 /budgets/check 不使用本方法
     * （GET 读路径不得带上 FOR UPDATE）。{@code FOR UPDATE OF be} 锁预算条目行，
     * join 侧不加锁。
     */
    @Select("""
        SELECT be.id, be.amount, be.used_amount AS "usedAmount", be.control_type AS "controlType",
               b.period, s.code AS "subjectCode", s.name AS "subjectName"
        FROM t_budget_entry be
        LEFT JOIN t_budget b ON b.id = be.budget_id
        LEFT JOIN t_subject s ON s.id = be.subject_id
        WHERE be.subject_id = #{subjectId}
          AND b.period = #{period}
          AND b.deleted = 0
          AND b.status IN ('APPROVED', 'ACTIVE')
        FOR UPDATE OF be
    """)
    List<Map<String, Object>> findBySubjectAndPeriodForUpdate(@Param("subjectId") Long subjectId,
                                                               @Param("period") String period);

    @Update("""
        UPDATE t_budget_entry
        SET used_amount = used_amount + #{amount}
        WHERE id = #{id}
    """)
    int addUsedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
