package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 凭证分录 Mapper
 */
@Mapper
public interface VoucherEntryMapper extends BaseMapper<VoucherEntryEntity> {

    /**
     * 根据凭证ID查询分录列表
     */
    List<VoucherEntryEntity> selectByVoucherId(@Param("voucherId") Long voucherId);

    /**
     * 批量插入分录
     */
    int batchInsert(@Param("list") List<VoucherEntryEntity> entries);

    /**
     * 删除凭证下所有分录
     */
    int deleteByVoucherId(@Param("voucherId") Long voucherId);
}
