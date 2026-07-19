package com.huicai.base.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huicai.base.config.entity.SysConfigEntity;

import java.util.List;
import java.util.Map;

/**
 * 系统参数 Service
 */
public interface SysConfigService extends IService<SysConfigEntity> {

    /**
     * 批量获取参数值
     */
    Map<String, String> getValues(List<String> keys);

    /**
     * 获取单个参数值
     */
    String getValue(String key);
}
