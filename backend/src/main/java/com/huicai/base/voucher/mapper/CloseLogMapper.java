package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.voucher.entity.CloseLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 期末结账日志 Mapper — 基础 CRUD 即可（列表查询走 Service 的 lambdaQuery）
 */
@Mapper
public interface CloseLogMapper extends BaseMapper<CloseLogEntity> {
}
