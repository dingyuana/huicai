package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.SummaryLibEntity;
import com.huicai.base.system.mapper.SummaryLibMapper;
import com.huicai.base.system.service.SummaryLibService;
import org.springframework.stereotype.Service;

/**
 * 常用摘要库 Service 实现
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class SummaryLibServiceImpl extends ServiceImpl<SummaryLibMapper, SummaryLibEntity> implements SummaryLibService {
}
