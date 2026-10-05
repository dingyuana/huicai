package com.huicai.base.system.dto;

import com.huicai.base.system.entity.SysConfigEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统参数新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>剔除 {@code createdBy/createdAt/updatedBy/updatedAt/deleted}，
 * 以及 {@code version}（Entity 上是 {@code exist = false} 的幽灵字段 ——
 * {@code SysConfigEntity} 未声明该字段，DB 侧 {@code t_sys_config} 的乐观锁列
 * 从未被读写，属 §4.2 第 16 条「反向缺口」）。
 *
 * <p>{@code configType} 的允许集以 {@code chk_config_type} 为准：
 * {@code system / business / accounting}（实测查证，非推测）。
 */
@Data
public class SysConfigSaveDTO {

    @NotBlank(message = "参数键不能为空")
    @Size(max = 128, message = "参数键长度不能超过 128")
    private String configKey;

    @Size(max = 2000, message = "参数值过长")
    private String configValue;

    @Pattern(regexp = "^$|system|business|accounting",
            message = "参数类型仅支持 system / business / accounting")
    private String configType;

    @Size(max = 255, message = "描述过长")
    private String description;

    private Boolean isActive;

    public SysConfigEntity toEntity() {
        SysConfigEntity e = new SysConfigEntity();
        e.setConfigKey(configKey);
        e.setConfigValue(configValue);
        e.setConfigType(configType);
        e.setDescription(description);
        e.setIsActive(isActive);
        return e;
    }
}
