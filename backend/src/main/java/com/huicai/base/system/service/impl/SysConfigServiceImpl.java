package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.SysConfigEntity;
import com.huicai.base.system.mapper.SysConfigMapper;
import com.huicai.base.system.service.SysConfigService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数 Service 实现
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfigEntity> implements SysConfigService {

    @Override
    public Map<String, String> getValues(List<String> keys) {
        Map<String, String> result = new HashMap<>();
        List<SysConfigEntity> list = this.list(
                new LambdaQueryWrapper<SysConfigEntity>()
                        .in(SysConfigEntity::getConfigKey, keys));
        for (SysConfigEntity config : list) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    @Override
    public String getValue(String key) {
        SysConfigEntity config = this.getOne(
                new LambdaQueryWrapper<SysConfigEntity>()
                        .eq(SysConfigEntity::getConfigKey, key));
        return config != null ? config.getConfigValue() : null;
    }
}
