package com.huicai.base.config.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.config.entity.SummaryLibEntity;
import com.huicai.base.config.mapper.SummaryLibMapper;
import com.huicai.base.config.service.SummaryLibService;
import org.springframework.stereotype.Service;

/**
 * 常用摘要库 Service 实现
 */
@Service
public class SummaryLibServiceImpl extends ServiceImpl<SummaryLibMapper, SummaryLibEntity> implements SummaryLibService {
}
