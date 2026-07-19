package com.huicai.base.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerEntity> {
}
