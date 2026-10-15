package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.mapper.VoucherTypeMapper;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 凭证类型 Service 实现
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class VoucherTypeServiceImpl extends ServiceImpl<VoucherTypeMapper, VoucherTypeEntity> implements VoucherTypeService {

    /**
     * 业务编码 → 中文名。
     *
     * <p>存在的理由：缺失时实体查不到，{@code getName()} 无值可用，
     * 而错误文案<b>必须同时给出 code 与中文名</b>（§4.5 第 50 条：
     * 「凭证类型不存在」既没说缺哪个、也没说去哪配）。
     */
    private static final Map<String, String> CODE_NAMES = Map.of(
            "JZ", "记账凭证",
            "SK", "收款凭证",
            "FK", "付款凭证",
            "ZZ", "转账凭证"
    );

    @Override
    public VoucherTypeEntity requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("凭证类型业务编码不能为空");
        }
        String normalized = code.trim().toUpperCase();

        // 不手工拼 enterprise_id：企业范围由 RLS + 应用层拦截器收敛
        VoucherTypeEntity type = getOne(Wrappers.<VoucherTypeEntity>lambdaQuery()
                .eq(VoucherTypeEntity::getCode, normalized)
                .eq(VoucherTypeEntity::getDeleted, 0)
                .last("LIMIT 1"));

        if (type == null) {
            String cn = CODE_NAMES.getOrDefault(normalized, normalized);
            throw new BusinessException(String.format(
                    "当前企业未配置「%s(%s)」，请先到【基础资料 → 凭证类型】新增后再制证",
                    cn, normalized));
        }
        return type;
    }
}
