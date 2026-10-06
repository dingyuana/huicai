package com.huicai.base.system.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.system.entity.SysConfigEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SysConfigVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 系统参数出参。对应前端 `sysConfig.ts` 的 `interface SysConfigVO`。
 * 
 * ⚠️ `SysConfigEntity.version` 上标着 `exist=false`（`t_sys_config` 无 version 列），
 * 属幽灵字段，本 VO 不暴露。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class SysConfigVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `config_key` */
    private String configKey;
    /** DB 列 `config_value` */
    private String configValue;
    /** DB 列 `config_type` */
    private String configType;
    /** DB 列 `description` */
    private String description;
    /** DB 列 `is_active` */
    private Boolean isActive;
    /** DB 列 `created_at` */
    private LocalDateTime createdAt;

    public static SysConfigVO from(SysConfigEntity e) {
        if (e == null) {
            return null;
        }
        SysConfigVO vo = new SysConfigVO();
        vo.setId(e.getId());
        vo.setConfigKey(e.getConfigKey());
        vo.setConfigValue(e.getConfigValue());
        vo.setConfigType(e.getConfigType());
        vo.setDescription(e.getDescription());
        vo.setIsActive(e.getIsActive());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<SysConfigVO> from(List<SysConfigEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(SysConfigVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<SysConfigVO> from(IPage<SysConfigEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysConfigVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}