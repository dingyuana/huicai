package com.huicai.sme.cash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.cash.entity.CashJournalEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface CashJournalMapper extends BaseMapper<CashJournalEntity> {

    @Select("SELECT COALESCE(SUM(debit), 0) FROM t_cash_journal WHERE period = #{period} AND deleted = 0")
    BigDecimal sumDebitByPeriod(@Param("period") String period);

    @Select("SELECT COALESCE(SUM(credit), 0) FROM t_cash_journal WHERE period = #{period} AND deleted = 0")
    BigDecimal sumCreditByPeriod(@Param("period") String period);

    @Select("SELECT balance FROM t_cash_journal WHERE period = #{period} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    BigDecimal getLastBalance(@Param("period") String period);
}