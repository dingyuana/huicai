package com.huicai.security;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.impl.PeriodServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SPEC-P150 守卫：{@code PeriodService} 的<b>按 id 读/改/删</b>不得落在
 * MyBatis-Plus {@code IService} 的 default 方法上。
 *
 * <h2>缺陷机理（2026-11-02 生产实证）</h2>
 * <pre>
 *   GET  /api/v1/periods        -> 1 条   （走已覆盖的 page()）
 *   GET  /api/v1/periods/all    -> 1 条   （走已覆盖的 list()）
 *   GET  /api/v1/periods/2      -> null   ← getById 未覆盖
 *   POST /api/v1/periods/3/close-> HTTP 200，但 t_period.status 仍 open、
 *                                  t_close_log 无新增行 ⇒ closePeriod 未被调用
 *   PUT  /api/v1/periods/4      -> HTTP 200，updated_at 未变  （updateById 未覆盖）
 *   DELETE /api/v1/periods/{id} -> 同型                         （removeById 未覆盖）
 * </pre>
 *
 * <p>AOP 判定事务时看的是<b>被调用方法的声明类型</b>：{@code IService#getById} 等是
 * default 接口方法，尽管 {@code PeriodServiceImpl} 类上有 {@code @Transactional}，
 * 事务与 {@code TenantRlsInitializer} 切面<b>都不会织入</b> ⇒
 * {@code SET LOCAL app.enterprise_id} 不生效 ⇒ RLS 谓词取 NULL ⇒ 整表被过滤
 * （读返 null / 写影响 0 行），且<b>不报错</b>。
 *
 * <p><b>用户可见形态</b>：「会计期间」页点「结账」提示成功、状态仍「开启」
 * ⇒ 而「反结账」只在 {@code closed} 时渲染 ⇒ <b>反结账入口永不出现</b>。
 *
 * <p><b>为什么 L2 全绿也测不出来</b>：Testcontainers 的数据库用户是<b>超级用户</b>，
 * 超级用户绕过 RLS ⇒ 谓词根本不执行，"读到 null" 不可能在测试里发生
 * （AGENTS §4.5 第 33 条）。故本守卫改守<b>结构</b>：方法必须由本类显式声明且带事务注解。
 */
@DisplayName("SPEC-P150 守卫：期间按 id 的读改删必须由 ServiceImpl 显式声明并带事务")
class PeriodDefaultMethodTransactionGuardTest {

    /** 必须被显式覆盖的方法：名字 + 首个参数类型 */
    private static final Object[][] REQUIRED = {
            {"getById", Serializable.class},
            {"updateById", PeriodEntity.class},
            {"removeById", Serializable.class},
    };

    @Test
    @DisplayName("前提锁：IService 的 getById/updateById/removeById 仍是 default 接口方法")
    void premiseStillHolds() throws Exception {
        // 若 MyBatis-Plus 升级后把它们改成具体方法，本守卫的前提消失，需重新评估
        assertTrue(IService.class.getMethod("getById", Serializable.class).isDefault(),
                "前提失效：IService#getById 已不是 default 方法，请重新评估本守卫");
        assertTrue(IService.class.getMethod("updateById", Object.class).isDefault(),
                "前提失效：IService#updateById 已不是 default 方法，请重新评估本守卫");
        assertTrue(IService.class.getMethod("removeById", Serializable.class).isDefault(),
                "前提失效：IService#removeById 已不是 default 方法，请重新评估本守卫");
    }

    @Test
    @DisplayName("核心：三个方法必须由 PeriodServiceImpl 声明且带 @Transactional（回退即红）")
    void periodServiceImplMustDeclareTransactionalOverrides() {
        StringBuilder missing = new StringBuilder();

        for (Object[] entry : REQUIRED) {
            String name = (String) entry[0];
            Class<?> param = (Class<?>) entry[1];
            Method declared = declaredTxMethod(PeriodServiceImpl.class, name, param);
            if (declared == null) {
                missing.append("\n  · ").append(name).append('(').append(param.getSimpleName()).append(')')
                        .append(" 未由 PeriodServiceImpl 显式声明（或缺少 @Transactional）")
                        .append(" ⇒ AOP 不织入事务 ⇒ GUC 不设 ⇒ RLS 过滤 ⇒ 静默失效");
            }
        }

        if (missing.length() > 0) {
            fail("期间管理按 id 的读/改/删存在未收口的方法：" + missing);
        }
    }

    @Test
    @DisplayName("装置自证：判定器对未声明的类型必须返回 null（否则上面是橡皮章）")
    void detectorIsNotVacuous() {
        // IService 自身不声明这三个方法（它们是 default 方法），判定器必须判「没有」；
        // 若这里返回非 null，说明判定逻辑恒真，上面的绿灯全是假绿。
        for (Object[] entry : REQUIRED) {
            String name = (String) entry[0];
            Class<?> param = (Class<?>) entry[1];
            assertTrue(declaredTxMethod(IService.class, name, param) == null,
                    "判定器恒真：对未声明 " + name + " 的 IService 也判为「已声明」，本守卫无效");
        }
    }

    /**
     * 在 {@code type} <b>自己声明</b>的方法里找 {@code name}（跳过编译器生成的 bridge），
     * 且要求该方法带 {@code @Transactional}。找不到返回 {@code null}。
     */
    private static Method declaredTxMethod(Class<?> type, String name, Class<?> paramType) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.isBridge() || m.isSynthetic()) {
                continue;
            }
            if (!m.getName().equals(name)) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || !params[0].isAssignableFrom(paramType)) {
                continue;
            }
            if (m.isAnnotationPresent(Transactional.class)) {
                return m;
            }
        }
        return null;
    }
}
