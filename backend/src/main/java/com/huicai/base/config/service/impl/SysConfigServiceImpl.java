package com.huicai.base.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.config.entity.SysConfigEntity;
import com.huicai.base.config.mapper.SysConfigMapper;
import com.huicai.base.config.service.SysConfigService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数 Service 实现
 */
@Service
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
