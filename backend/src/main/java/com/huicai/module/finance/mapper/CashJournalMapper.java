package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.CashJournalEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface CashJournalMapper extends BaseMapper<CashJournalEntity> {

    BigDecimal sumDebitByPeriod(@Param("period") String period);

    BigDecimal sumCreditByPeriod(@Param("period") String period);

    BigDecimal getLastBalance(@Param("period") String period);
}