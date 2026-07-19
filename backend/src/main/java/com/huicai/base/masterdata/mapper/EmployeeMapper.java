package com.huicai.base.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {
}
