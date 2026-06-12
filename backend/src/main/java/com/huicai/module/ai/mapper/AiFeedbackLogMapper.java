package com.huicai.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.ai.entity.AiFeedbackLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 分类反馈日志 Mapper
 */
@Mapper
public interface AiFeedbackLogMapper extends BaseMapper<AiFeedbackLogEntity> {
}
