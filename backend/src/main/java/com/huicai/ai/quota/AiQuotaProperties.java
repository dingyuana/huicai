package com.huicai.ai.quota;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * SPEC-P135 —— AI 配额配置。
 *
 * <p>🔴 {@code daily} <b>刻意不给默认值</b>（{@code @NotNull}）：配额是<b>花钱的数字</b>，
 * 写死在代码里会让人误以为「200 是有依据的」。老丁 2026-10-17 裁定
 * <b>200 次 / 账套 / 日</b>，该值必须落在配置文件里、可被审计与调整。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.quota")
public class AiQuotaProperties {

    /** 总开关。关闭时全部放行（仅用于应急止血）。 */
    private boolean enabled = true;

    /** 每账套每日 AI 调用上限 —— <b>无默认值，缺失即拒绝启动</b>。 */
    @NotNull
    private Integer daily;

    /**
     * 豁免权限码（持有者不计入配额）。
     *
     * <p>🔴 <b>这是「ADMIN 豁免」的代理实现，不是真的角色检查</b>：
     * {@code LoginUser} 只有 {@code userType}（SUPER_ADMIN/AGENCY/ENTERPRISE）
     * 与 {@code agencyRole}，<b>不含 ACCOUNTANT/ADMIN/FINANCE_MGR 这类平台角色</b>
     * （实测确认），而 authorities 里放的是<b>权限码</b>而非角色码（P131 实测）。
     * 故只能挑一个「<b>仅 ADMIN 持有</b>」的码做代理 ——
     * 默认取 {@code system:user:list}（实测持有角色数 = 1，即 ADMIN）。</p>
     *
     * <p>⚠️ <b>代价（已知局限）</b>：若将来该码被授予更多角色，豁免范围会随之扩大。
     * 真要按角色豁免，需先给 {@code LoginUser} 加角色信息 —— 那是独立的改动。</p>
     */
    private List<String> exemptPermissions = List.of("system:user:list");
}