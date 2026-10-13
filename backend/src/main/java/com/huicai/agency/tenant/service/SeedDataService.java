package com.huicai.agency.tenant.service;

public interface SeedDataService {

    /**
     * 为新企业克隆种子数据（科目/凭证类型/摘要库/期间模板）
     * @param enterpriseId 目标企业ID
     * @return 克隆成功返回 true，已初始化返回 false
     */
    boolean cloneSeedData(Long enterpriseId);

    /**
     * SPEC-P126：强制重跑播种。
     *
     * <p>默认实现在 {@code seedDataDone == true} 时直接返回，导致「标记了成功但其实
     * 没复制到数据」的企业<b>永远无法自愈</b>。本重载允许跳过该短路。
     *
     * @param force true 时忽略已完成标记，强制重跑（克隆本身幂等，靠唯一键去重）
     */
    boolean cloneSeedData(Long enterpriseId, boolean force);
}
