package com.huicai.common.config;

import com.huicai.common.security.PermissionEnforcementInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPEC-P133 —— 注册权限清单拦截器。
 *
 * <p>🔴 <b>本项目此前没有任何 {@code WebMvcConfigurer}</b>（实测 grep 零命中），
 * 故此处是首次引入 MVC 拦截器注册。<b>刻意只注册这一个</b>：
 * 每多一个 {@code WebMvcConfigurer}，请求链上就多一处「顺序不确定」的环节，
 * 而拦截器顺序错误的症状往往是「某个拦截器静默不生效」（§4.5 第 27 条同型）。</p>
 *
 * <p>⚠️ 与 {@code SecurityConfig} 的过滤器链<b>正交</b>：过滤器链管<b>认证</b>（你是谁），
 * 本拦截器管<b>动作授权</b>（你能做什么）。两者都要，不可互相替代。</p>
 *
 * <p>🔴 <b>踩坑留证：清单 bean 刻意「不」声明在本配置里。</b>
 * 曾尝试两条路，两条都失败：
 * <ol>
 *   <li>清单用普通 {@code @Component} ⇒ {@code @WebMvcTest} 切片不扫描它
 *       ⇒ L1 一次 <b>97 个用例</b>全报 {@code NoSuchBeanDefinitionException}。</li>
 *   <li>把清单声明成这里的 {@code @Bean} ⇒ 与本配置的字段注入构成
 *       {@code BeanCurrentlyInCreationException: unresolvable circular reference}
 *       （配置 bean 创建中 → 需要拦截器 → 需要清单 → 清单的 @Bean 方法在本配置里）。</li>
 * </ol>
 * <b>正解</b>：清单是普通 {@code @Component}，而拦截器用
 * {@link org.springframework.beans.factory.ObjectProvider} <b>可选</b>依赖它 ——
 * 切片里没有清单就不安装执行（切片本就无需鉴权），真实应用里清单必在。
 * <b>判据：被切片上下文间接依赖的协作对象，要么在切片白名单里，
 * 要么用可选依赖；塞进 WebMvcConfigurer 的 @Bean 通常是最糟的第三种选择。</b>
 */
@Configuration
public class PermissionWebMvcConfig implements WebMvcConfigurer {

    private final PermissionEnforcementInterceptor interceptor;

    public PermissionWebMvcConfig(PermissionEnforcementInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh");
    }
}