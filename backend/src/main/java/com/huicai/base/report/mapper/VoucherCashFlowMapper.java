package com.huicai.base.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.report.entity.VoucherCashFlowEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VoucherCashFlowMapper extends BaseMapper<VoucherCashFlowEntity> {

    /** 清除某凭证的现金流分配（批量重建时先清后插） */
    @Delete("DELETE FROM t_voucher_cash_flow WHERE voucher_id = #{voucherId}")
    int deleteByVoucherId(@Param("voucherId") Long voucherId);
}
