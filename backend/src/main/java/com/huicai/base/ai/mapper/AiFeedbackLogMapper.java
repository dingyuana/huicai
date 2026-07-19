package com.huicai.base.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.ai.entity.AiFeedbackLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 分类反馈日志 Mapper
 */
@Mapper
public interface AiFeedbackLogMapper extends BaseMapper<AiFeedbackLogEntity> {
}
