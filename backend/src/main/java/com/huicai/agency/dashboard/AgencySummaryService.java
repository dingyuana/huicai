package com.huicai.agency.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgencySummaryService {

    private final EnterpriseMapper enterpriseMapper;
    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final VoucherMapper voucherMapper;

    public Map<String, Object> getDashboard() {
        Long agencyId = SecurityUtils.getCurrentAgencyId();

        // 1. 企业统计
        LambdaQueryWrapper<EnterpriseEntity> eq = new LambdaQueryWrapper<>();
        eq.eq(EnterpriseEntity::getAgencyId, agencyId)
          .eq(EnterpriseEntity::getDeleted, 0);
        List<EnterpriseEntity> enterprises = enterpriseMapper.selectList(eq);

        int total = enterprises.size();
        int active = (int) enterprises.stream().filter(e -> "ACTIVE".equals(e.getStatus())).count();
        List<Long> enterpriseIds = enterprises.stream().map(EnterpriseEntity::getId).collect(Collectors.toList());

        // 2. 本月凭证统计（跨所有企业汇总）
        String currentPeriod = getCurrentPeriod();
        long totalVouchersThisMonth = enterpriseIds.isEmpty() ? 0
            : voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .in(VoucherEntity::getEnterpriseId, enterpriseIds)
                .eq(VoucherEntity::getPeriod, currentPeriod)
                .eq(VoucherEntity::getDeleted, 0));
        long pendingAuditVouchers = enterpriseIds.isEmpty() ? 0
            : voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .in(VoucherEntity::getEnterpriseId, enterpriseIds)
                .eq(VoucherEntity::getStatus, "SUBMITTED")
                .eq(VoucherEntity::getDeleted, 0));

        // 3. 会计工作量统计
        List<Map<String, Object>> accountants = getAccountantWorkload(agencyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEnterprises", total);
        result.put("activeEnterprises", active);
        result.put("totalVouchersThisMonth", totalVouchersThisMonth);
        result.put("pendingAuditVouchers", pendingAuditVouchers);
        result.put("enterprises", enterprises.stream().map(e -> {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", e.getId());
            card.put("enterpriseName", e.getEnterpriseName());
            card.put("status", e.getStatus());
            card.put("seedDataDone", e.getSeedDataDone());
            return card;
        }).collect(Collectors.toList()));
        result.put("accountants", accountants);
        return result;
    }

    private List<Map<String, Object>> getAccountantWorkload(Long agencyId) {
        LambdaQueryWrapper<AgencyUserEntity> q = new LambdaQueryWrapper<>();
        q.eq(AgencyUserEntity::getAgencyId, agencyId)
         .eq(AgencyUserEntity::getDeleted, 0)
         .in(AgencyUserEntity::getAgencyRole, List.of("ACCOUNTANT", "ASSISTANT"));
        List<AgencyUserEntity> users = agencyUserMapper.selectList(q);

        String currentPeriod = getCurrentPeriod();

        return users.stream().map(u -> {
            // 获取该会计分配的企业ID列表
            List<Long> assignedEnterpriseIds = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(u.getId());

            long voucherCount = assignedEnterpriseIds.isEmpty() ? 0
                : voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                    .in(VoucherEntity::getEnterpriseId, assignedEnterpriseIds)
                    .eq(VoucherEntity::getPeriod, currentPeriod)
                    .eq(VoucherEntity::getDeleted, 0));
            long pendingCount = assignedEnterpriseIds.isEmpty() ? 0
                : voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                    .in(VoucherEntity::getEnterpriseId, assignedEnterpriseIds)
                    .eq(VoucherEntity::getStatus, "SUBMITTED")
                    .eq(VoucherEntity::getDeleted, 0));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("userId", u.getUserId());
            m.put("agencyRole", u.getAgencyRole());
            m.put("enterpriseCount", agencyUserEnterpriseMapper.countByUserId(u.getId()));
            m.put("voucherCountThisMonth", voucherCount);
            m.put("pendingAuditCount", pendingCount);
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getAccountantDetail(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("enterprises", agencyUserEnterpriseMapper.selectByUserId(userId));
        return result;
    }

    private String getCurrentPeriod() {
        LocalDate now = LocalDate.now();
        return String.format("%04d%02d", now.getYear(), now.getMonthValue());
    }
}