package com.huicai.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 企业成员校验（P102 / REQ-2026-129，AT-102-1 / 1b / 1c）
 *
 * <p>修复的缺陷：{@code JwtAuthenticationFilter} 原先无条件信任
 * {@code X-Enterprise-Id} 请求头并用它覆盖 JWT 中的 enterpriseId，
 * 且<b>零成员校验</b> ⇒ 任意登录用户改一个头即可切换到任意企业。
 *
 * <p>允许切换到企业 E 的条件（任一成立，即 SPEC §1.1「三源并集」）：
 * <ol>
 *   <li>源1 直属：{@code t_user.enterprise_id = E}</li>
 *   <li>源2 代理授权：{@code t_user → t_agency_user.user_id} →
 *       {@code t_agency_user_enterprise.agency_user_id} → E</li>
 *   <li>源3 超管：{@code t_user.user_type = 'SUPER_ADMIN'}</li>
 * </ol>
 *
 * <p><b>源2 必须两跳</b>：{@code t_agency_user_enterprise.agency_user_id} 指向的是
 * {@code t_agency_user.id}，不是 {@code t_user.id}。
 * 该表上现成的 {@code countByUserId} / {@code selectByUserId} 直接拿
 * {@code #{userId}} 去比 {@code agency_user_id}，语义是错的，本类不予使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnterpriseMembershipChecker {

    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;

    /**
     * @param userId             当前登录用户 id
     * @param homeEnterpriseId   JWT 中的企业（{@code t_user.enterprise_id}），可为 null
     * @param userType           {@code t_user.user_type}
     * @param targetEnterpriseId 请求头请求切换到的企业
     * @return 是否允许切换
     */
    public boolean isMember(Long userId, Long homeEnterpriseId, String userType,
                            Long targetEnterpriseId) {
        if (targetEnterpriseId == null) {
            return true;
        }
        // 源3：超级管理员可全局切换
        if ("SUPER_ADMIN".equals(userType)) {
            return true;
        }
        // 源1：直属企业
        if (homeEnterpriseId != null && homeEnterpriseId.equals(targetEnterpriseId)) {
            return true;
        }
        // 源2：代理端多企业授权（两跳）
        if (userId != null && isGrantedThroughAgency(userId, targetEnterpriseId)) {
            return true;
        }
        log.warn("企业成员校验失败: userId={}, home={}, userType={}, target={}",
                userId, homeEnterpriseId, userType, targetEnterpriseId);
        return false;
    }

    private boolean isGrantedThroughAgency(Long userId, Long targetEnterpriseId) {
        List<AgencyUserEntity> agencyUsers = agencyUserMapper.selectList(
                Wrappers.<AgencyUserEntity>lambdaQuery()
                        .eq(AgencyUserEntity::getUserId, userId)
                        .eq(AgencyUserEntity::getDeleted, 0));
        if (agencyUsers == null || agencyUsers.isEmpty()) {
            return false;
        }
        for (AgencyUserEntity agencyUser : agencyUsers) {
            List<Long> enterpriseIds =
                    agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(agencyUser.getId());
            if (enterpriseIds != null && enterpriseIds.contains(targetEnterpriseId)) {
                return true;
            }
        }
        return false;
    }
}
