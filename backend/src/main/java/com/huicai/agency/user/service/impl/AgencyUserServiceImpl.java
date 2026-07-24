package com.huicai.agency.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.agency.user.dto.AgencyUserCreateDTO;
import com.huicai.agency.user.dto.AgencyUserVO;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.agency.user.service.AgencyUserService;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyUserServiceImpl implements AgencyUserService {

    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<AgencyUserVO> page(int page, int size, Long agencyId) {
        LambdaQueryWrapper<AgencyUserEntity> wrapper = new LambdaQueryWrapper<>();
        if (agencyId != null) {
            wrapper.eq(AgencyUserEntity::getAgencyId, agencyId);
        }
        wrapper.orderByDesc(AgencyUserEntity::getCreatedAt);

        Page<AgencyUserEntity> entityPage = agencyUserMapper.selectPage(
                new Page<>(page, size), wrapper);

        return entityPage.convert(entity -> {
            AgencyUserVO vo = toVO(entity);
            return vo;
        });
    }

    @Override
    public List<AgencyUserVO> list(Long agencyId) {
        LambdaQueryWrapper<AgencyUserEntity> wrapper = new LambdaQueryWrapper<>();
        if (agencyId != null) {
            wrapper.eq(AgencyUserEntity::getAgencyId, agencyId);
        }
        wrapper.orderByDesc(AgencyUserEntity::getCreatedAt);
        List<AgencyUserEntity> entities = agencyUserMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).toList();
    }

    private AgencyUserVO toVO(AgencyUserEntity entity) {
        AgencyUserVO vo = new AgencyUserVO();
        vo.setId(entity.getId());
        vo.setAgencyId(entity.getAgencyId());
        vo.setUserId(entity.getUserId());
        vo.setAgencyRole(entity.getAgencyRole());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());

        // 查询用户名和真实姓名
        UserEntity user = userMapper.selectById(entity.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
        }

        // 查询负责的客户数
        List<Long> enterpriseIds = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(entity.getId());
        vo.setEnterpriseCount(enterpriseIds.size());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgencyUserVO create(AgencyUserCreateDTO dto) {
        // 校验操作者权限
        String currentRole = SecurityUtils.getCurrentAgencyRole();
        if (!"AGENCY_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            throw BusinessException.forbidden("无权管理代理用户");
        }

        // 校验用户名唯一
        UserEntity existing = userMapper.selectByUsername(dto.getUsername());
        if (existing != null) {
            throw BusinessException.badRequest("用户名已存在");
        }

        // 创建 t_user
        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setUserType("AGENCY");
        user.setAgencyId(dto.getAgencyId());
        user.setAgencyRole(dto.getAgencyRole());
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        // 创建 t_agency_user
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setAgencyId(dto.getAgencyId());
        agencyUser.setUserId(user.getId());
        agencyUser.setAgencyRole(dto.getAgencyRole());
        agencyUser.setStatus("ACTIVE");
        agencyUserMapper.insert(agencyUser);

        log.info("Agency user created: userId={}, agencyRole={}, agencyId={}",
                user.getId(), dto.getAgencyRole(), dto.getAgencyId());

        AgencyUserVO vo = new AgencyUserVO();
        vo.setId(agencyUser.getId());
        vo.setAgencyId(agencyUser.getAgencyId());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setAgencyRole(agencyUser.getAgencyRole());
        vo.setStatus(agencyUser.getStatus());
        vo.setEnterpriseCount(0);
        return vo;
    }

    @Override
    public AgencyUserVO getById(Long id) {
        AgencyUserEntity entity = agencyUserMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw BusinessException.notFound("代理用户不存在");
        }
        AgencyUserVO vo = new AgencyUserVO();
        vo.setId(entity.getId());
        vo.setAgencyId(entity.getAgencyId());
        vo.setUserId(entity.getUserId());
        vo.setAgencyRole(entity.getAgencyRole());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());

        UserEntity user = userMapper.selectById(entity.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
        }

        List<Long> enterpriseIds = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(entity.getId());
        vo.setEnterpriseCount(enterpriseIds.size());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspend(Long id) {
        String currentRole = SecurityUtils.getCurrentAgencyRole();
        if (!"AGENCY_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            throw BusinessException.forbidden("无权管理代理用户");
        }

        AgencyUserEntity entity = agencyUserMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw BusinessException.notFound("代理用户不存在");
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有活跃状态的用户才能暂停，当前状态：" + entity.getStatus());
        }

        entity.setStatus("SUSPENDED");
        agencyUserMapper.updateById(entity);

        // 同步更新 t_user 状态
        UserEntity user = userMapper.selectById(entity.getUserId());
        if (user != null) {
            user.setStatus("INACTIVE");
            userMapper.updateById(user);
        }

        log.info("Agency user suspended: agencyUserId={}, userId={}", id, entity.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reactivate(Long id) {
        String currentRole = SecurityUtils.getCurrentAgencyRole();
        if (!"AGENCY_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            throw BusinessException.forbidden("无权管理代理用户");
        }

        AgencyUserEntity entity = agencyUserMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw BusinessException.notFound("代理用户不存在");
        }
        if (!"SUSPENDED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有暂停状态的用户才能恢复，当前状态：" + entity.getStatus());
        }

        entity.setStatus("ACTIVE");
        agencyUserMapper.updateById(entity);

        UserEntity user = userMapper.selectById(entity.getUserId());
        if (user != null) {
            user.setStatus("ACTIVE");
            userMapper.updateById(user);
        }

        log.info("Agency user reactivated: agencyUserId={}, userId={}", id, entity.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id) {
        String currentRole = SecurityUtils.getCurrentAgencyRole();
        if (!"AGENCY_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            throw BusinessException.forbidden("无权管理代理用户");
        }

        AgencyUserEntity entity = agencyUserMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw BusinessException.notFound("代理用户不存在");
        }
        if (!"SUSPENDED".equals(entity.getStatus())) {
            throw BusinessException.badRequest("只有暂停状态的用户才能终止，当前状态：" + entity.getStatus());
        }

        entity.setStatus("TERMINATED");
        agencyUserMapper.updateById(entity);

        log.info("Agency user terminated: agencyUserId={}, userId={}", id, entity.getUserId());
    }
}
