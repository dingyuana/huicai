package com.huicai.agency.client.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.agency.client.dto.ContractCreateDTO;
import com.huicai.agency.client.dto.ContractVO;
import com.huicai.agency.client.dto.RenewalReminderVO;
import com.huicai.agency.client.entity.ContractEntity;
import com.huicai.agency.client.mapper.ContractMapper;
import com.huicai.agency.client.service.ContractService;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class ContractServiceImpl implements ContractService {

    private final ContractMapper contractMapper;
    private final EnterpriseMapper enterpriseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO create(ContractCreateDTO dto) {
        ContractEntity exist = contractMapper.selectOne(
                new LambdaQueryWrapper<ContractEntity>()
                        .eq(ContractEntity::getContractNo, dto.getContractNo()));
        if (exist != null) {
            throw BusinessException.conflict("合同编号已存在: " + dto.getContractNo());
        }

        ContractEntity entity = new ContractEntity();
        // V177：撤 RLS 后，创建路径必须自己校验「只能为本代理的客户建合同」，
        // 否则可给别家代理的企业建合同（且 dto.agencyId 由客户端任意填）。
        Long currentAgencyId = currentAgencyIdOrThrow();
        if (!"SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())
                && !currentAgencyId.equals(dto.getAgencyId())) {
            throw BusinessException.forbidden("不能为其他代理公司的客户创建合同");
        }
        entity.setEnterpriseId(dto.getEnterpriseId());
        entity.setAgencyId(dto.getAgencyId());
        entity.setContractNo(dto.getContractNo());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setContractType(dto.getContractType());
        entity.setAmount(dto.getAmount());
        entity.setStatus("ACTIVE");
        entity.setRenewalNoticeSent(false);
        contractMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public ContractVO getById(Long id) {
        return toVO(loadOwned(id));
    }

    @Override
    public IPage<ContractVO> page(int page, int size) {
        // V177：撤 RLS 后必须自己按 agency_id 收敛，否则任何登录用户都能
        // 遍历本页读出**全部客户**的合同金额与到期日（跨代理泄漏）。
        Page<ContractEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractEntity> w =
                new LambdaQueryWrapper<ContractEntity>().orderByDesc(ContractEntity::getCreatedAt);
        Long agencyId = currentAgencyIdOrThrow();
        // ⚠️ 必须用 if 包裹，不能写 .eq(col, agencyId)：超管时 agencyId 为 null，
        // 而 MyBatis-Plus 的 eq(col, null) 会生成 `agency_id = NULL`,
        // 在 SQL 里恒不匹配 ⇒ 超管会看到**0 条**合同（比丢行更糟）。
        if (agencyId != null) {
            w.eq(ContractEntity::getAgencyId, agencyId);
        }
        return contractMapper.selectPage(p, w).convert(this::toVO);
    }

    @Override
    public List<RenewalReminderVO> getRenewalReminders() {
        // V177：撤 RLS 后按 agency_id 收敛。原先该查询**完全不带 enterprise_id 条件**，
        // 是「扫本代理全部客户」的跨客户查询 —— RLS 挡它就会让代理看不到
        // 自己名下其它客户的续费提醒（探针实测 3 条只返 1 条）。
        List<ContractEntity> contracts = contractMapper.findRenewalReminders(
                currentAgencyIdOrThrow());
        List<RenewalReminderVO> reminders = new ArrayList<>();
        for (ContractEntity c : contracts) {
            EnterpriseEntity ent = enterpriseMapper.selectById(c.getEnterpriseId());
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
            reminders.add(new RenewalReminderVO(
                    c.getId(), c.getContractNo(), c.getEnterpriseId(),
                    ent != null ? ent.getEnterpriseName() : "未知",
                    c.getEndDate(), c.getAmount(), daysUntil));
        }
        return reminders;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO renew(Long id) {
        ContractEntity entity = loadOwned(id);
        // 续约：延长一年
        entity.setEndDate(entity.getEndDate().plusYears(1));
        entity.setRenewalNoticeSent(false);
        entity.setStatus("ACTIVE");
        contractMapper.updateById(entity);
        return toVO(entity);
    }

    private ContractVO toVO(ContractEntity e) {
        return new ContractVO(e.getId(), e.getEnterpriseId(), e.getAgencyId(),
                e.getContractNo(), e.getStartDate(), e.getEndDate(),
                e.getContractType(), e.getAmount(), e.getStatus(),
                e.getRenewalNoticeSent(), e.getCreatedAt());
    }

    // ==================== V177：agency_id 归属校验 ====================

    /**
     * 断言目标合同属于当前操作者所在的代理公司。
     *
     * <p><b>为什么必须有</b>：V177 撤掉了本表的 RLS —— 其 {@code enterprise_id}
     * 语义是「签约客户」而非「归属租户」（该表与 {@code agency_id} 并存，
     * 续费提醒按设计就是跨客户扫描），用 {@code enterprise_id = app.enterprise_id}
     * 过滤会让代理**看不到自己名下其它客户的合同**（探针实测 3 条只返 1 条）。
     *
     * <p>撤掉 DB 层兜底后，隔离责任落到应用层。若此处不校验，
     * 任何登录用户都能按猜测 id 调 {@code GET /contracts/{id}} 或遍历
     * {@code GET /contracts/page}，读出**别家客户的合同金额与到期日** ——
     * 这比「丢行」严重得多，属跨代理数据泄漏。
     *
     * <p><b>超管豁免</b>：{@code SUPER_ADMIN} 本就跨代理，否则超管无法运维
     * （与 {@code AgencyUserEnterpriseServiceImpl#requireSameAgency} 同口径）。
     *
     * <p><b>fail-loud</b>：{@code agencyId} 缺失时抛 500 并指明字段名，
     * <b>不</b>按「无权」处理 —— 否则会把「上下文构造有问题」伪装成
     * 「权限不足」，且全体代理管理员被锁在门外却无人知道根因。
     */
    private void requireSameAgency(ContractEntity contract) {
        if ("SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            return;
        }
        Long currentAgencyId = SecurityUtils.getCurrentAgencyId();
        if (currentAgencyId == null) {
            throw new BusinessException(500,
                    "登录上下文缺少代理公司信息(agencyId)，无法校验合同归属；"
                            + "请检查 LoginUser 构造是否传入 t_user.agency_id");
        }
        if (!currentAgencyId.equals(contract.getAgencyId())) {
            throw BusinessException.forbidden("无权访问其他代理公司的合同");
        }
    }

    /** 按 id 取合同并校验归属 —— 所有按 id 操作的入口统一走这里。 */
    private ContractEntity loadOwned(Long id) {
        ContractEntity entity = contractMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("合同不存在");
        }
        requireSameAgency(entity);
        return entity;
    }

    /**
     * 取当前操作者的 agencyId；缺失则 fail-loud。
     *
     * <p><b>为什么不给超管兜底</b>：超管在 {@code requireSameAgency} 里已提前返回，
     * 但 {@code page} / {@code getRenewalReminders} 是<b>按 agency_id 过滤</b>而非校验，
     * 超管同样需要一个值 —— 故超管回落到「不加 agency_id 条件」（返回全部），
     * 与其「可跨代理」的定位一致，且仍有端点角色校验兜着。
     */
    private Long currentAgencyIdOrThrow() {
        if ("SUPER_ADMIN".equals(SecurityUtils.getCurrentUserType())) {
            return null;
        }
        Long currentAgencyId = SecurityUtils.getCurrentAgencyId();
        if (currentAgencyId == null) {
            throw new BusinessException(500,
                    "登录上下文缺少代理公司信息(agencyId)，无法校验合同归属；"
                            + "请检查 LoginUser 构造是否传入 t_user.agency_id");
        }
        return currentAgencyId;
    }
}
