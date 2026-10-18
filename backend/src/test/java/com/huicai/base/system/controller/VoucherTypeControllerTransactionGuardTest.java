package com.huicai.base.system.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 🔴 演示阻塞级缺陷的防回归守卫：走 RLS 表的 Controller 读方法<b>必须有事务</b>。
 *
 * <p><b>缺陷本身</b>：{@code VoucherTypeController} 的两个读方法没有事务，
 * 而 {@code TenantGucSetter#apply} 开头就是
 * {@code if (!TransactionSynchronizationManager.isActualTransactionActive()) return;}
 * ⇒ GUC {@code app.enterprise_id} 从不设置 ⇒ RLS 谓词
 * {@code enterprise_id = NULLIF(current_setting(...),'')::bigint} 取到 NULL
 * ⇒ <b>该表所有行被过滤，凭证类型列表恒空</b>。
 *
 * <p><b>为什么 L2 全绿测不出来</b>：{@code AbstractMapperTest} 连的是
 * Testcontainers 的<b>超级用户</b>，而超级用户<b>绕过 RLS</b>
 * （AGENTS §4.5 第 33 条）⇒ 谓词根本执行不到，"0 行" 不可能发生。
 * 实测症状：凭证类型下拉为空 ⇒ 无法制证，而所有单测、L1/L2 全绿。
 *
 * <p><b>为什么服务端类级 {@code @Transactional} 不算数</b>：
 * {@code VoucherTypeServiceImpl} 类上<b>本来就挂着</b> {@code @Transactional}，
 * 但读路径实测仍是 {@code Closing non transactional SqlSession} ⇒ 该注解
 * 在这条调用链上<b>没有生效</b>。因此守卫直接要求<b> Controller 方法上</b>有事务，
 * 不依赖「Service 那边应该兜住」这种未经证实的推断。
 *
 * <p>🔴 本守卫只覆盖 {@code VoucherTypeController}：它是<b>已实证出问题</b>的那个。
 * 全仓扫描需要先区分「读方法」与「写方法」（写方法本就有事务），
 * 且要处理分页插件自执行 COUNT 的路径 —— 属更大范围的机械改造，另行立项。
 */
@DisplayName("演示阻塞守卫：凭证类型读端点必须有事务，否则 RLS 过滤致列表恒空")
@SpringBootTest
@AutoConfigureMockMvc
class VoucherTypeControllerTransactionGuardTest {

    @Autowired
    private org.springframework.context.ApplicationContext ctx;

    private static final String[] READ_METHODS = {"list", "listAll"};

    @Test
    @DisplayName("list / listAll 必须带 @Transactional（无事务 ⇒ GUC 不设 ⇒ RLS 返 0 行）")
    void readEndpointsMustRunInTransaction() {
        Object controller = ctx.getBean(VoucherTypeController.class);
        boolean allOk = true;
        StringBuilder missing = new StringBuilder();

        for (String name : READ_METHODS) {
            boolean found = false;
            for (Method m : VoucherTypeController.class.getDeclaredMethods()) {
                if (!m.getName().equals(name)) {
                    continue;
                }
                found = true;
                boolean tx = m.isAnnotationPresent(Transactional.class)
                        || m.getDeclaringClass().isAnnotationPresent(Transactional.class);
                if (!tx) {
                    allOk = false;
                    missing.append("\n  · ").append(name).append("() 缺少 @Transactional")
                            .append(" ⇒ TenantGucSetter 提前 return ⇒ app.enterprise_id 恒 NULL")
                            .append(" ⇒ t_voucher_type 被 RLS 过滤为 0 行（凭证类型下拉为空，无法制证）");
                }
            }
            if (!found) {
                fail("未找到读方法 " + name + "() —— 守卫与代码脱节，"
                        + "若该方法已重命名请同步更新本守卫，否则它会静默失效");
            }
        }

        assertTrue(allOk, "以下读端点缺少事务：" + missing);
    }

    @Test
    @DisplayName("🔴 守卫自身有效：构造无事务的 HandlerMethod，判定逻辑必须判红")
    void guardDetectsMissingTransaction() throws Exception {
        // 用一个确定没有 @Transactional 的方法做对照，证明判定逻辑不是恒真
        Method control = GuardControl.class.getDeclaredMethod("noTx");
        boolean tx = control.isAnnotationPresent(Transactional.class)
                || control.getDeclaringClass().isAnnotationPresent(Transactional.class);
        assertTrue(!tx, "对照方法本应无事务 —— 若它被判为有事务，说明判定逻辑恒真，守卫是橡皮章");

        // 再确认一个带事务的对照组判为「有」
        Method positive = GuardControl.class.getDeclaredMethod("withTx");
        boolean tx2 = positive.isAnnotationPresent(Transactional.class)
                || positive.getDeclaringClass().isAnnotationPresent(Transactional.class);
        assertTrue(tx2, "带 @Transactional 的方法必须被判为有事务");
    }

    /** 仅供守卫自证用的对照类型 */
    static class GuardControl {
        void noTx() {
        }

        @Transactional(rollbackFor = Exception.class)
        void withTx() {
        }
    }
}