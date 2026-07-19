package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.mapper.VoucherTypeMapper;
import com.huicai.base.system.service.VoucherTypeService;
import org.springframework.stereotype.Service;

/**
 * 凭证类型 Service 实现
 */
@Service
public class VoucherTypeServiceImpl extends ServiceImpl<VoucherTypeMapper, VoucherTypeEntity> implements VoucherTypeService {
}
