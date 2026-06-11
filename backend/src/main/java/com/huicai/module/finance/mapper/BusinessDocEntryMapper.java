package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BusinessDocEntryMapper extends BaseMapper<BusinessDocEntryEntity> {
    List<BusinessDocEntryEntity> selectByDocId(@Param("docId") Long docId);
    int deleteByDocId(@Param("docId") Long docId);
}
