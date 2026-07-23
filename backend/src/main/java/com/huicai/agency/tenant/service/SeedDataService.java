package com.huicai.agency.tenant.service;

public interface SeedDataService {

    /**
     * 为新企业克隆种子数据（科目/凭证类型/摘要库/期间模板）
     * @param enterpriseId 目标企业ID
     * @return 克隆成功返回 true，已初始化返回 false
     */
    boolean cloneSeedData(Long enterpriseId);
}
