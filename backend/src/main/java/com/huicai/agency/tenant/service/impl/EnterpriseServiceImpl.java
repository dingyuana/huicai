package com.huicai.agency.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;
import com.huicai.agency.tenant.entity.AgencyEnterpriseEntity;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnterpriseServiceImpl implements EnterpriseService {

    private final EnterpriseMapper enterpriseMapper;
    private final AgencyEnterpriseMapper agencyEnterpriseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseVO create(EnterpriseCreateDTO dto) {
        EnterpriseEntity exist = enterpriseMapper.selectOne(
                new LambdaQueryWrapper<EnterpriseEntity>()
                        .eq(EnterpriseEntity::getEnterpriseCode, dto.getEnterpriseCode()));
        if (exist != null) {
            throw BusinessException.conflict("企业编码已存在: " + dto.getEnterpriseCode());
        }

        EnterpriseEntity entity = new EnterpriseEntity();
        entity.setEnterpriseCode(dto.getEnterpriseCode());
        entity.setEnterpriseName(dto.getEnterpriseName());
        entity.setTaxId(dto.getTaxId());
        entity.setMode("AGENCY_CLIENT");
        entity.setStatus("PENDING");
        entity.setSeedDataDone(false);
        if (dto.getAgencyId() != null) {
            entity.setAgencyId(dto.getAgencyId());
        }
        enterpriseMapper.insert(entity);

        // 自动绑定到代理
        if (dto.getAgencyId() != null) {
            bindInternal(entity.getId(), dto.getAgencyId());
        }

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseVO update(Long id, EnterpriseCreateDTO dto) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        if (dto.getEnterpriseName() != null) entity.setEnterpriseName(dto.getEnterpriseName());
        if (dto.getTaxId() != null) entity.setTaxId(dto.getTaxId());
        enterpriseMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    public EnterpriseVO getById(Long id) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        return toVO(entity);
    }

    @Override
    public IPage<EnterpriseVO> pageByAgency(Long agencyId, int page, int size) {
        Page<EnterpriseEntity> p = new Page<>(page, size);
        IPage<EnterpriseEntity> result = enterpriseMapper.selectPage(p,
                new LambdaQueryWrapper<EnterpriseEntity>()
                        .eq(EnterpriseEntity::getAgencyId, agencyId)
                        .orderByDesc(EnterpriseEntity::getCreatedAt));
        return result.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EnterpriseEntity entity = enterpriseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        enterpriseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long enterpriseId, Long agencyId) {
        EnterpriseEntity entity = enterpriseMapper.selectById(enterpriseId);
        if (entity == null) {
            throw BusinessException.notFound("企业不存在");
        }
        bindInternal(enterpriseId, agencyId);
        entity.setAgencyId(agencyId);
        enterpriseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long enterpriseId, Long agencyId) {
        agencyEnterpriseMapper.delete(
                new LambdaQueryWrapper<AgencyEnterpriseEntity>()
                        .eq(AgencyEnterpriseEntity::getEnterpriseId, enterpriseId)
                        .eq(AgencyEnterpriseEntity::getAgencyId, agencyId));
    }

    private void bindInternal(Long enterpriseId, Long agencyId) {
        AgencyEnterpriseEntity exist = agencyEnterpriseMapper.selectOne(
                new LambdaQueryWrapper<AgencyEnterpriseEntity>()
                        .eq(AgencyEnterpriseEntity::getEnterpriseId, enterpriseId)
                        .eq(AgencyEnterpriseEntity::getAgencyId, agencyId));
        if (exist != null) {
            return; // 已绑定
        }
        AgencyEnterpriseEntity ae = new AgencyEnterpriseEntity();
        ae.setEnterpriseId(enterpriseId);
        ae.setAgencyId(agencyId);
        ae.setStatus("ACTIVE");
        agencyEnterpriseMapper.insert(ae);
    }

    private EnterpriseVO toVO(EnterpriseEntity e) {
        return new EnterpriseVO(e.getId(), e.getEnterpriseCode(), e.getEnterpriseName(),
                e.getTaxId(), e.getMode(), e.getAgencyId(), e.getStatus(),
                e.getSeedDataDone(), e.getCreatedAt());
    }
}
