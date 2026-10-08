package com.huicai.security;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * SPEC-P113 守卫测试的被切对象（顶层类才能被 component-scan 扫到）。
 *
 * <p>{@code doWrite} 标注 {@code @PostMapping} 以命中 {@code EnterpriseWriteGuard} 的切点；
 * {@code doRead} 无写注解 ⇒ 走读路径，守卫不拦。</p>
 */
@Component
public class P113WriteTarget {

    @PostMapping("/p113/probe")
    public String doWrite(String tag) {
        return "written";
    }

    public String doRead() {
        return "read";
    }
}