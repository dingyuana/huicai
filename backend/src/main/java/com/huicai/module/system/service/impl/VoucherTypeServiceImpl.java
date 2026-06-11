package com.huicai.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.module.system.entity.VoucherTypeEntity;
import com.huicai.module.system.mapper.VoucherTypeMapper;
import com.huicai.module.system.service.VoucherTypeService;
import org.springframework.stereotype.Service;

/**
 * 凭证类型 Service 实现
 */
@Service
public class VoucherTypeServiceImpl extends ServiceImpl<VoucherTypeMapper, VoucherTypeEntity> implements VoucherTypeService {
}
