package com.huicai.module.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReconciliationLogMapper extends BaseMapper<ReconciliationLogEntity> {

    @Select("SELECT * FROM t_reconciliation_log WHERE source_doc_type = #{sourceDocType} AND source_doc_id = #{sourceDocId} ORDER BY created_at DESC")
    List<ReconciliationLogEntity> findBySource(@Param("sourceDocType") String sourceDocType, @Param("sourceDocId") Long sourceDocId);

    @Select("SELECT * FROM t_reconciliation_log WHERE target_doc_type = #{targetDocType} AND target_doc_id = #{targetDocId} ORDER BY created_at DESC")
    List<ReconciliationLogEntity> findByTarget(@Param("targetDocType") String targetDocType, @Param("targetDocId") Long targetDocId);

    @Delete("DELETE FROM t_reconciliation_log")
    int deleteAll();
}
