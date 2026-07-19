package com.huicai.base.voucher.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.module.system.entity.VoucherTypeEntity;
import com.huicai.module.system.service.VoucherTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 凭证号生成服务实现
 * 使用 Redis INCR 生成原子自增序列号
 */
@Service
@RequiredArgsConstructor
public class VoucherNoServiceImpl implements VoucherNoService {

    private static final String REDIS_KEY_PREFIX = "voucher:no:";

    private final StringRedisTemplate redisTemplate;
    private final VoucherTypeService voucherTypeService;
    private final VoucherMapper voucherMapper;

    @Override
    public String generateNextNo(String period, Long voucherTypeId) {
        VoucherTypeEntity type = voucherTypeService.getById(voucherTypeId);
        if (type == null) {
            throw BusinessException.notFound("凭证类型不存在");
        }

        String redisKey = REDIS_KEY_PREFIX + period + ":" + voucherTypeId;
        Long serial = redisTemplate.opsForValue().increment(redisKey);
        if (serial == null) {
            throw new BusinessException("生成凭证号失败");
        }

        return formatVoucherNo(type.getCode(), period, serial);
    }

    @Override
    public String getCurrentNo(String period, Long voucherTypeId) {
        VoucherTypeEntity type = voucherTypeService.getById(voucherTypeId);
        if (type == null) {
            throw BusinessException.notFound("凭证类型不存在");
        }

        String redisKey = REDIS_KEY_PREFIX + period + ":" + voucherTypeId;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value != null) {
            long serial = Long.parseLong(value);
            return formatVoucherNo(type.getCode(), period, serial);
        }

        // Redis 无记录, 查询数据库中该期间该类型的最大凭证号
        String maxNo = voucherMapper.selectMaxVoucherNo(period, voucherTypeId);
        if (maxNo != null && maxNo.startsWith(type.getCode())) {
            // 提取流水号部分
            String serialStr = maxNo.substring(type.getCode().length() + period.length());
            try {
                long serial = Integer.parseInt(serialStr);
                return formatVoucherNo(type.getCode(), period, serial);
            } catch (NumberFormatException e) {
                // 凭证号格式异常, 从1开始
            }
        }

        // 无历史记录, 从0开始
        return formatVoucherNo(type.getCode(), period, 0L);
    }

    /**
     * 格式化凭证号: 类型代码+年份月份+流水号(4位补零)
     * 例: JZ2026010001
     */
    private String formatVoucherNo(String typeCode, String period, long serial) {
        return typeCode + period + String.format("%04d", serial);
    }
}
