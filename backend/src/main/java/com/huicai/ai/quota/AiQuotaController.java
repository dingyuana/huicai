package com.huicai.ai.quota;

import com.huicai.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

/**
 * SPEC-P135 —— 配额查询端点 + 拦截器注册。
 *
 * <p>⚠️ <b>刻意不写成两个类</b>：配额只有一处生效点，拆开会让人误以为
 * 「AI 端点受配额保护」是全局事实，而它其实<b>只覆盖下面两条路径前缀</b>。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@EnableConfigurationProperties(AiQuotaProperties.class)
public class AiQuotaController implements WebMvcConfigurer {

    private final ObjectProvider<AiQuotaGuard> guardProvider;
    private final AiQuotaInterceptor interceptor;

    /**
     * 查询今日 AI 额度 —— <b>只读，不消耗额度</b>（SPEC AT-135-4）。
     */
    @GetMapping("/quota")
    public R<Map<String, Object>> quota() {
        AiQuotaGuard guard = guardProvider.getIfAvailable();
        if (guard == null) {
            // 切片上下文里配额守卫不存在（依赖 Redis），如实报告「不可用」而非假装正常
            return R.ok(Map.of("available", false, "used", -1, "limit", 0,
                    "resetAt", "", "enforcement", "unavailable"));
        }
        AiQuotaGuard.Quota q = guard.peek();
        return R.ok(Map.of(
                "available", true,
                "used", q.used(),
                "limit", q.limit(),
                "resetAt", q.resetAt().toString(),
                "enforcement", "quota"));
    }

    /**
     * 注册配额拦截器。
     *
     * <p>🔴 <b>只覆盖两条 AI 路径前缀</b>（实测 11 个写端点分布于此）：
     * {@code /api/ai/**}（NVIDIA 直连）与 {@code /api/v1/ai/**}（任务/反馈）。
     * 若将来新增 AI 端点落在别处，<b>不会被覆盖</b> ——
     * 故由 {@code AiQuotaCoverageStructureTest} 结构守卫盯住。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/ai/**", "/api/v1/ai/**");
    }
}