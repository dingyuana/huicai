package com.huicai.sme.cash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.cash.entity.BankReconciliationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BankReconciliationLogMapper extends BaseMapper<BankReconciliationLogEntity> {

    /** 按流水查对账日志（时间倒序），供对账详情页展示人工确认轨迹 */
    @Select("SELECT * FROM t_bank_reconciliation_log "
            + "WHERE statement_id = #{statementId} AND deleted = 0 "
            + "ORDER BY created_at DESC, id DESC")
    List<BankReconciliationLogEntity> listByStatement(@Param("statementId") Long statementId);
}
