package com.huicai.agency.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.security.ReferenceGuard;
import com.huicai.common.security.Ref;
import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;
import com.huicai.agency.tenant.vo.CurrentPeriodVO;
import com.huicai.agency.tenant.entity.AgencyEnterpriseEntity;
import com.huicai.agency.tenant.util.ContactMasker;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
@Slf4j
public class EnterpriseServiceImpl implements EnterpriseService {

    private final EnterpriseMapper enterpriseMapper;
    private final AgencyEnterpriseMapper agencyEnterpriseMapper;
    private final PeriodMapper periodMapper;

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
        // SPEC-P129：联系信息（均可选；未填则落 NULL 而非空串）
        entity.setContactName(dto.getContactName());
        entity.setContactPhone(dto.getContactPhone());
        entity.setContactEmail(dto.getContactEmail());
        entity.setAddress(dto.getAddress());
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
        // SPEC-P129：联系信息同属可更新字段。
        // 注意：MP 的 updateById 是 NOT_NULL 策略，显式 set(null) 不会清列，
        // 「清空某字段」需 UpdateWrapper 显式置 null（AGENTS §4.4 第 11 条）。
        // 本次只支持填写/修改，不实现「清空」语义。
        if (dto.getContactName() != null) entity.setContactName(dto.getContactName());
        if (dto.getContactPhone() != null) entity.setContactPhone(dto.getContactPhone());
        if (dto.getContactEmail() != null) entity.setContactEmail(dto.getContactEmail());
        if (dto.getAddress() != null) entity.setAddress(dto.getAddress());
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
    public IPage<EnterpriseVO> pageByAgency(Long agencyId, int page, int size,
                                            java.util.List<Long> visibleIds) {
        // SPEC-P120：会计/助理的可见范围是「派工关系」，不是「本代理全部」。
        // ⚠️ 必须在**应用层**收口 —— 拦截器只注入 enterprise_id，对本查询无效
        //    （§4.5 第 40 条：无上下文/非多租户表时不能依赖 SQL 层兜底）。
        Page<EnterpriseEntity> p = new Page<>(page, size);
        IPage<EnterpriseEntity> result = enterpriseMapper.selectPage(p,
                new LambdaQueryWrapper<EnterpriseEntity>()
                        .in(!visibleIds.isEmpty(), EnterpriseEntity::getId, visibleIds)
                        .eq(visibleIds.isEmpty(), EnterpriseEntity::getId, -1L)
                        .orderByDesc(EnterpriseEntity::getCreatedAt));
        return result.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ReferenceGuard(
            parent = "t_enterprise",
            label = "客户企业",
            children = {
                    @Ref(table = "t_agency_enterprise", column = "enterprise_id", label = "代理客户关系"),
                    @Ref(table = "t_agency_user_enterprise", column = "enterprise_id", label = "代理用户企业"),
                    @Ref(table = "t_contract", column = "enterprise_id", label = "合同"),
            })
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

    /**
     * ✏️ SPEC-P129 D-1（老丁裁定）：手机号/邮箱属 <b>PII</b>，对非管理员<b>服务端脱敏</b>。
     *
     * <p>必须在服务端做：前端脱敏等于明文已经发到浏览器，脱敏形同虚设。
     * 判定按<b>角色</b>而非「是否有值」，复用 {@link SecurityUtils} 的登录态取值，
     * <b>不新增第二套角色判定</b>（与 P115「单一事实源」同源要求）。
     *
     * <p>可见全值：{@code SUPER_ADMIN}、{@code AGENCY + AGENCY_ADMIN}。
     * 其余（{@code ENTERPRISE} / 代理端会计·审核员·助理）一律脱敏。
     */
    private boolean canSeeFullContact() {
        // 🔴 判不出角色时一律<b>掩码</b>，绝不抛异常。
        // 理由有二：
        // ① 披露决策必须 fail-closed —— 判不出「你是谁」时，默认是「不给你看」；
        // ② toVO 被列表/详情/定时任务等<b>无登录态</b>路径复用，抛「未登录」会把
        //    这些路径整个打断（本类真库测试首轮即撞出：抛
        //    BusinessException.unauthorized）。这与「缺企业上下文必须报错」那类
        //    <b>授权闸门</b>相反：闸门要 fail-loud，脱敏只要 fail-closed。
        String userType;
        try {
            userType = SecurityUtils.getCurrentUserType();
        } catch (Exception e) {
            log.debug("无登录态上下文，按脱敏处理: {}", e.getMessage());
            return false;
        }
        if ("SUPER_ADMIN".equals(userType)) {
            return true;
        }
        if (!"AGENCY".equals(userType)) {
            return false;
        }
        try {
            return "AGENCY_ADMIN".equals(SecurityUtils.getCurrentAgencyRole());
        } catch (Exception e) {
            log.debug("取代理内角色失败，按脱敏处理: {}", e.getMessage());
            return false;
        }
    }

    private EnterpriseVO toVO(EnterpriseEntity e) {
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(e.getId());
        vo.setEnterpriseCode(e.getEnterpriseCode());
        vo.setEnterpriseName(e.getEnterpriseName());
        vo.setTaxId(e.getTaxId());
        vo.setMode(e.getMode());
        vo.setAgencyId(e.getAgencyId());
        vo.setStatus(e.getStatus());
        vo.setSeedDataDone(e.getSeedDataDone());
        vo.setCreatedAt(e.getCreatedAt());

        boolean full = canSeeFullContact();
        vo.setContactName(e.getContactName());
        vo.setContactPhone(full ? e.getContactPhone() : ContactMasker.maskPhone(e.getContactPhone()));
        vo.setContactEmail(full ? e.getContactEmail() : ContactMasker.maskEmail(e.getContactEmail()));
        vo.setAddress(e.getAddress());
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentPeriodVO getCurrentPeriodInfo(Long enterpriseId) {
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || enterprise.getDeleted() == 1) {
            throw BusinessException.notFound("企业不存在");
        }
        String startPeriod = enterprise.getStartPeriod();
        CurrentPeriodVO vo = new CurrentPeriodVO();
        vo.setStartPeriod(startPeriod);
        vo.setEarliestUnclosedPeriod(enterpriseMapper.selectEarliestUnclosedPeriod(enterpriseId));
        vo.setLatestClosedPeriod(enterpriseMapper.selectLatestClosedPeriod(enterpriseId));
        if (startPeriod != null) {
            String hasDataPeriod = enterpriseMapper.selectLatestPeriodWithData(enterpriseId);
            if (hasDataPeriod != null && hasDataPeriod.compareTo(startPeriod) >= 0) {
                vo.setHasDataPeriod(hasDataPeriod);
                vo.setCurrentPeriod(hasDataPeriod);
            } else {
                vo.setCurrentPeriod(startPeriod);
            }
            return vo;
        }
        PeriodEntity latest = periodMapper.selectOne(
                new LambdaQueryWrapper<PeriodEntity>()
                        .eq(PeriodEntity::getEnterpriseId, enterpriseId)
                        .orderByDesc(PeriodEntity::getPeriodCode)
                        .last("LIMIT 1"));
        vo.setCurrentPeriod(latest == null ? null : latest.getPeriodCode());
        return vo;
    }
}
