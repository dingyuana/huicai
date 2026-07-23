package com.huicai.agency.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.agency.tenant.dto.AgencyCreateDTO;
import com.huicai.agency.tenant.dto.AgencyUpdateDTO;
import com.huicai.agency.tenant.dto.AgencyVO;
import com.huicai.agency.tenant.entity.AgencyEntity;
import com.huicai.agency.tenant.mapper.AgencyMapper;
import com.huicai.agency.tenant.service.AgencyService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgencyServiceImpl implements AgencyService {

    private final AgencyMapper agencyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgencyVO create(AgencyCreateDTO dto) {
        AgencyEntity exist = agencyMapper.selectOne(
                new LambdaQueryWrapper<AgencyEntity>()
                        .eq(AgencyEntity::getAgencyCode, dto.getAgencyCode()));
        if (exist != null) {
            throw BusinessException.conflict("代理公司编码已存在: " + dto.getAgencyCode());
        }
        AgencyEntity entity = new AgencyEntity();
        entity.setAgencyCode(dto.getAgencyCode());
        entity.setAgencyName(dto.getAgencyName());
        entity.setContactName(dto.getContactName());
        entity.setContactPhone(dto.getContactPhone());
        entity.setStatus("PENDING");
        agencyMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgencyVO update(Long id, AgencyUpdateDTO dto) {
        AgencyEntity entity = agencyMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("代理公司不存在");
        }
        if (dto.getAgencyName() != null) entity.setAgencyName(dto.getAgencyName());
        if (dto.getContactName() != null) entity.setContactName(dto.getContactName());
        if (dto.getContactPhone() != null) entity.setContactPhone(dto.getContactPhone());
        agencyMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    public AgencyVO getById(Long id) {
        AgencyEntity entity = agencyMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("代理公司不存在");
        }
        return toVO(entity);
    }

    @Override
    public IPage<AgencyVO> page(int page, int size) {
        Page<AgencyEntity> p = new Page<>(page, size);
        IPage<AgencyEntity> result = agencyMapper.selectPage(p,
                new LambdaQueryWrapper<AgencyEntity>().orderByDesc(AgencyEntity::getCreatedAt));
        return result.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AgencyEntity entity = agencyMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("代理公司不存在");
        }
        agencyMapper.deleteById(id);
    }

    private AgencyVO toVO(AgencyEntity e) {
        return new AgencyVO(e.getId(), e.getAgencyCode(), e.getAgencyName(),
                e.getContactName(), e.getContactPhone(), e.getStatus(), e.getCreatedAt());
    }
}
