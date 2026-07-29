package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.BusinessDocEntryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BusinessDocEntryMapper extends BaseMapper<BusinessDocEntryEntity> {

    @Select("SELECT * FROM t_business_doc_entry WHERE doc_id = #{docId} ORDER BY id")
    List<BusinessDocEntryEntity> selectByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM t_business_doc_entry WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM t_business_doc_entry")
    int physicalDeleteAll();
}
