package com.huicai.sme.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.arap.entity.BusinessDocEntryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BusinessDocEntryMapper extends BaseMapper<BusinessDocEntryEntity> {
    List<BusinessDocEntryEntity> selectByDocId(@Param("docId") Long docId);
    int deleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM t_business_doc_entry")
    int physicalDeleteAll();
}
