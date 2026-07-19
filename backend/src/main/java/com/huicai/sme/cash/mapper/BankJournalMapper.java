package com.huicai.sme.cash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.cash.entity.BankJournalEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BankJournalMapper extends BaseMapper<BankJournalEntity> {
    List<Map<String, Object>> aggregateByAccountPeriod(@Param("accountId") Long accountId, @Param("period") String period);
    int updateVoucherId(@Param("id") Long id, @Param("voucherId") Long voucherId);
    int updateReconciled(@Param("id") Long id, @Param("reconciled") Boolean reconciled);
    BigDecimal sumAmountByAccount(@Param("accountId") Long accountId);
    List<BankJournalEntity> selectUnreconciled(@Param("accountId") Long accountId);
    int nullOutBusinessDocId();
}
