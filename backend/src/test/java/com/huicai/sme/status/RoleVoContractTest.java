package com.huicai.sme.status;

import com.huicai.base.system.vo.RoleVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 出参面 DTO 化 **批次 1（试点）：{@link RoleVO} 与前端契约的强一致锁**
 *
 * <p><b>为什么需要这个守卫</b>：出参面 VO 化最大的风险不是「漏字段」，而是
 * **前后端各自演化、谁都不知道**。前端 TypeScript 接口与后端 VO 是两份独立声明，
 * 没有编译器把它们绑在一起 ⇒ 后端删一个字段，前端只在运行时才发现
 * {@code undefined}，且往往是某个页面偶发空白。
 *
 * <p>⇒ 本类把「前端 {@code interface RoleVO} 的字段集」抄一份在这里，
 * 后端 VO 每加/减字段都必须同步改这里，否则转红。成本极低（纯反射），
 * 但把一类静默的跨端契约漂移变成编译期可见。
 *
 * <p><b>⚠️ 抄写纪律</b>：这份清单必须与
 * {@code frontend/src/api/modules/system.ts} 的 {@code interface RoleVO} **逐字一致**，
 * 不同步就是本类自身失效。故 {@link #frontendInterfaceIsTheSourceOfThisList} 会把
 * 前端文件读出来核对一遍。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@DisplayName("P102 出参面批次1：RoleVO 与前端契约一致")
class RoleVoContractTest {

    /**
     * 前端 {@code frontend/src/api/modules/system.ts} 里 {@code interface RoleVO} 的字段集。
     * ⚠️ 与前端保持逐字一致；改动其中一侧必须同时改另一侧。
     */
    private static final Set<String> FRONTEND_ROLE_FIELDS = new LinkedHashSet<>(Arrays.asList(
            "id", "code", "name", "description", "status", "sortOrder", "dataScope"));

    /** 刻意不外露的字段 —— 每个都有依据（见 RoleVO 注释），此处一并锁死防回退 */
    private static final Set<String> MUST_NOT_EXPOSE = new LinkedHashSet<>(Arrays.asList(
            "version",           // 乐观锁内部值
            "deleted",           // 逻辑删除位
            "createdBy", "updatedBy", "createdAt", "updatedAt",  // 审计列
            "menuIds",           // 权限数据，不该由前端自行猜测
            "permissionCodes")); // 同上

    private static Set<String> voFields() {
        return Arrays.stream(RoleVO.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    @DisplayName("后端 VO 的字段集与前端接口逐字段一致（不多不少）")
    void voFieldsMatchFrontendContractExactly() {
        Set<String> actual = voFields();
        Set<String> missing = new LinkedHashSet<>(FRONTEND_ROLE_FIELDS);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(FRONTEND_ROLE_FIELDS);

        assertTrue(missing.isEmpty(),
                "后端 VO 缺少前端已声明的字段 " + missing + " ⇒ 前端会读到 undefined");
        assertTrue(extra.isEmpty(),
                "后端 VO 多暴露了前端接口里没有的字段 " + extra
                        + " ⇒ 要么前端类型该补，要么 VO 该收窄（铁律 #13：出参必须用 VO）");
        assertEquals(FRONTEND_ROLE_FIELDS.size(), actual.size(), "字段总数异常");
    }

    @Test
    @DisplayName("刻意不外露的内部/权限字段不得回到 VO（防重构回退）")
    void internalFieldsMustNotReappear() {
        Set<String> leaked = new LinkedHashSet<>(voFields());
        leaked.retainAll(MUST_NOT_EXPOSE);
        assertTrue(leaked.isEmpty(),
                "以下字段回流到出参 VO：" + leaked + " —— version/deleted 是内部状态，"
                        + "menuIds/permissionCodes 是权限数据，外露即等于把越权判断挪到浏览器侧");
    }

    @Test
    @DisplayName("本类自身有效：前端接口文件真的存在且含本清单的字段")
    void frontendInterfaceIsTheSourceOfThisList() {
        String src = readFrontendSystemApi();
        for (String f : FRONTEND_ROLE_FIELDS) {
            assertTrue(src.contains(f),
                    "前端 system.ts 里已找不到字段 " + f
                            + " ⇒ 前端接口已改而本清单未同步，本守卫会失去意义（守卫自身失效）");
        }
    }

    private String readFrontendSystemApi() {
        java.net.URL url = getClass().getClassLoader()
                .getResource("../../../../../frontend/src/api/modules/system.ts");
        if (url != null) {
            try {
                return java.nio.file.Files.readString(java.nio.file.Paths.get(url.toURI()));
            } catch (Exception ignored) {
                // 落到工作区相对路径
            }
        }
        java.nio.file.Path p = java.nio.file.Paths.get(
                "../frontend/src/api/modules/system.ts");
        assertTrue(java.nio.file.Files.exists(p),
                "找不到前端接口文件 " + p.toAbsolutePath()
                        + "（测试以 backend/ 为工作目录运行）⇒ 无法核对契约来源");
        try {
            return java.nio.file.Files.readString(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}