package com.huicai.module.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.budget.entity.BudgetEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BudgetEntryMapper extends BaseMapper<BudgetEntryEntity> {

    @Select("""
        SELECT be.*, b.period, s.code AS subject_code, s.name AS subject_name
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

    @Update("""
        UPDATE t_budget_entry
        SET used_amount = used_amount + #{amount}
        WHERE id = #{id}
    """)
    int addUsedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
