package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.BankStatementEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface BankStatementMapper extends BaseMapper<BankStatementEntity> {
    @Select("SELECT * FROM t_bank_statement WHERE account_id = #{accountId} AND match_status = #{status} AND deleted = 0")
    List<BankStatementEntity> selectByAccountAndStatus(@Param("accountId") Long accountId, @Param("status") String status);

    @Update("UPDATE t_bank_statement SET matched_journal_id = #{journalId}, match_status = #{status} WHERE id = #{id}")
    int updateMatch(@Param("id") Long id, @Param("journalId") Long journalId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM t_bank_statement WHERE account_id = #{accountId} AND tx_date = #{txDate} AND external_no = #{externalNo} AND amount = #{amount} AND deleted = 0")
    int countDuplicate(@Param("accountId") Long accountId, @Param("txDate") LocalDate txDate, @Param("externalNo") String externalNo, @Param("amount") BigDecimal amount);

    @Delete("DELETE FROM t_bank_statement")
    int physicalDeleteAll();

    @Select("SELECT category AS classification, COUNT(*) AS cnt "
          + "FROM t_bank_statement "
          + "WHERE account_id = #{accountId} AND deleted = 0 "
          + "GROUP BY category")
    List<Map<String, Object>> countByClassification(@Param("accountId") Long accountId);

    @Select("SELECT category AS classification, COUNT(*) AS cnt "
          + "FROM t_bank_statement "
          + "WHERE account_id = #{accountId} AND deleted = 0 AND review_status = #{reviewStatus} "
          + "GROUP BY category")
    List<Map<String, Object>> countByClassificationByReview(@Param("accountId") Long accountId,
                                                            @Param("reviewStatus") String reviewStatus);
}
