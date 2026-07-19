package com.huicai.sme.cash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.cash.entity.BankStatementEntity;
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
    List<BankStatementEntity> selectByAccountAndStatus(@Param("accountId") Long accountId, @Param("status") String status);
    int updateMatch(@Param("id") Long id, @Param("journalId") Long journalId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM t_bank_statement WHERE account_id = #{accountId} AND tx_date = #{txDate} AND external_no = #{externalNo} AND amount = #{amount} AND deleted = 0")
    int countDuplicate(@Param("accountId") Long accountId, @Param("txDate") LocalDate txDate, @Param("externalNo") String externalNo, @Param("amount") BigDecimal amount);

    @Delete("DELETE FROM t_bank_statement")
    int physicalDeleteAll();

    @Update("UPDATE t_bank_statement SET generated_voucher_id = NULL WHERE generated_voucher_id IS NOT NULL")
    int nullOutGeneratedVoucherIds();

    @Select("SELECT classification AS classification, COUNT(*) AS cnt "
          + "FROM t_bank_statement "
          + "WHERE account_id = #{accountId} AND deleted = 0 "
          + "GROUP BY classification")
    List<Map<String, Object>> countByClassification(@Param("accountId") Long accountId);

    @Select("SELECT classification AS classification, COUNT(*) AS cnt "
          + "FROM t_bank_statement "
          + "WHERE account_id = #{accountId} AND deleted = 0 AND review_status = #{reviewStatus} "
          + "GROUP BY classification")
    List<Map<String, Object>> countByClassificationByReview(@Param("accountId") Long accountId,
                                                            @Param("reviewStatus") String reviewStatus);

    @Select("""
        SELECT id, tx_date, amount, review_status, classification
        FROM t_bank_statement
        WHERE deleted = 0
          AND review_status IN ('voucher_generated', 'payment_created')
          AND generated_voucher_id IS NULL
          AND generated_doc_id IS NULL
        LIMIT 50
    """)
    List<Map<String, Object>> findStatusGeneratedMismatch();
}
