package com.huicai.sme.cash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.cash.entity.BankJournalEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BankJournalMapper extends BaseMapper<BankJournalEntity> {

    @Select("SELECT tx_type, SUM(amount) AS total_amount, COUNT(*) AS transaction_count "
          + "FROM t_bank_journal "
          + "WHERE account_id = #{accountId} AND period = #{period} AND deleted = 0 "
          + "GROUP BY tx_type ORDER BY tx_type")
    List<Map<String, Object>> aggregateByAccountPeriod(@Param("accountId") Long accountId, @Param("period") String period);

    @Update("UPDATE t_bank_journal SET voucher_id = #{voucherId} WHERE id = #{id}")
    int updateVoucherId(@Param("id") Long id, @Param("voucherId") Long voucherId);

    @Update("UPDATE t_bank_journal SET is_reconciled = #{reconciled} WHERE id = #{id}")
    int updateReconciled(@Param("id") Long id, @Param("reconciled") Boolean reconciled);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_bank_journal WHERE account_id = #{accountId} AND deleted = 0")
    BigDecimal sumAmountByAccount(@Param("accountId") Long accountId);

    @Select("SELECT * FROM t_bank_journal WHERE account_id = #{accountId} AND (is_reconciled IS NULL OR is_reconciled = false) AND deleted = 0")
    List<BankJournalEntity> selectUnreconciled(@Param("accountId") Long accountId);

    @Update("UPDATE t_bank_journal SET business_doc_id = NULL WHERE business_doc_id IS NOT NULL")
    int nullOutBusinessDocId();
}
