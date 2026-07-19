package com.huicai.base.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.storage.entity.AttachmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {

    @Select("SELECT * FROM t_attachment WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND deleted = 0 ORDER BY created_at DESC")
    List<AttachmentEntity> findByBiz(String bizType, Long bizId);
}
