package com.huicai.sme.status;

import com.huicai.base.masterdata.vo.CustomerVO;
import com.huicai.base.masterdata.vo.VendorVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 出参面 DTO 化 **批次 2**：主数据 VO（客户 / 供应商）与前端契约的强一致锁
 *
 * <p><b>为什么</b>：前端 TypeScript 接口与后端 VO 是两份<b>互不引用</b>的声明，
 * 没有编译器把它们绑在一起 ⇒ 后端删一个字段，前端只在运行时才发现 {@code undefined}，
 * 且往往表现为「某个页面偶发空白」这种极难定位的现象。
 * 本类把前端接口的字段集抄一份，后端 VO 每加/减字段都必须同步改这里，否则转红。
 *
 * <p><b>抄写纪律</b>：清单必须与前端文件<b>逐字一致</b>，否则本类自身失效 ——
 * {@link #frontendSourcesAreReadable} 会真去读前端文件核对，确保清单没有凭空漂移。
 *
 * <p><b>可扩展性</b>：后续批次每加一个 VO，就在 {@link #VO_CONTRACTS} 里加一行
 * （VO 类 + 前端接口名 + 前端文件）。本类因此成为出参面 VO 化的<b>统一收口闸门</b>。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@DisplayName("P102 出参面批次2：主数据 VO 与前端契约一致")
class MasterDataVoContractTest {

    /** 一条 VO 契约：VO 类 + 前端 interface 名 + 前端文件（相对 backend/ 的路径） */
    private record VoContract(Class<?> vo, String frontendInterface, String frontendFile) {
    }

    private static final java.util.List<VoContract> VO_CONTRACTS = java.util.List.of(
            new VoContract(com.huicai.base.system.vo.RoleVO.class, "RoleVO", "../frontend/src/api/modules/system.ts"),
            new VoContract(CustomerVO.class, "Customer", "../frontend/src/api/modules/arap.ts"),
            new VoContract(VendorVO.class, "Vendor", "../frontend/src/api/modules/arap.ts"),
            new VoContract(com.huicai.base.masterdata.vo.EmployeeVO.class, "Employee",
                    "../frontend/src/api/modules/employee.ts"),
            // ---- 批次 4：七个「字段形态干净」的控制器 ----
            new VoContract(com.huicai.base.system.vo.PeriodVO.class, "PeriodVO",
                    "../frontend/src/api/modules/period.ts"),
            new VoContract(com.huicai.base.system.vo.VoucherTypeVO.class, "VoucherTypeVO",
                    "../frontend/src/api/modules/voucherType.ts"),
            new VoContract(com.huicai.base.system.vo.SummaryLibVO.class, "SummaryLibVO",
                    "../frontend/src/api/modules/summaryLib.ts"),
            new VoContract(com.huicai.base.system.vo.SysConfigVO.class, "SysConfigVO",
                    "../frontend/src/api/modules/sysConfig.ts"),
            new VoContract(com.huicai.sme.asset.vo.AssetCategoryVO.class, "AssetCategory",
                    "../frontend/src/api/modules/asset.ts"),
            new VoContract(com.huicai.sme.cash.vo.BankJournalVO.class, "BankJournalVO",
                    "../frontend/src/api/modules/bankJournal.ts"),
            new VoContract(com.huicai.sme.cash.vo.CashJournalVO.class, "CashJournal",
                    "../frontend/src/api/modules/cashJournal.ts"),
            // ---- 批次 5：三方核对无反向缺口/无幽灵字段，且前端契约逐字段对齐 ----
            new VoContract(com.huicai.sme.cash.vo.BankAccountVO.class, "BankAccountVO",
                    "../frontend/src/api/modules/bankAccount.ts"),
            new VoContract(com.huicai.sme.asset.vo.AssetCardVO.class, "AssetCard",
                    "../frontend/src/api/modules/asset.ts"),
            new VoContract(com.huicai.sme.tax.vo.ClassificationRuleVO.class, "ClassificationRule",
                    "../frontend/src/api/modules/classificationRule.ts"),
            new VoContract(com.huicai.base.ai.vo.AiTaskVO.class, "AiTask",
                    "../frontend/src/api/modules/ai.ts"),
            new VoContract(com.huicai.base.ai.vo.AiAnomalyTagVO.class, "AiAnomalyTag",
                    "../frontend/src/api/modules/ai.ts"),
            // P102 批次 6：修掉 PrepaymentList.vue 的 4 个恒空白字段
            new VoContract(com.huicai.sme.arap.vo.PrepaymentVO.class, "Prepayment",
                    "../frontend/src/api/modules/prepayment.ts"));

    /**
     * 各 VO 刻意不外露的字段 —— 每个都有依据，见各 VO 注释。
     *
     * <p>⚠️ <b>createdAt 刻意不在此集合里</b>：它虽是审计列，但<b>有 5 个前端接口明确声明了它</b>
     * （PeriodVO / VoucherTypeVO / SummaryLibVO / SysConfigVO / BankJournalVO 都含
     * {@code createdAt: string}）⇒ 它是这些页面的<b>合法展示字段</b>，不是内部状态。
     * 本集合一度把它包含进来，当场被本守卫报红（PeriodVO 外露了内部字段 [createdAt]）
     * —— 这正是本守卫存在的意义：把「我以为的内部字段」与「契约真正要求的字段」强制分开。
     */
    private static final Set<String> NEVER_EXPOSE = new LinkedHashSet<>(Arrays.asList(
            "version", "deleted", "createdBy", "updatedBy", "updatedAt",
            "enterpriseId", "tenantId", "menuIds", "permissionCodes"));

    private static Set<String> fieldsOf(Class<?> c) {
        return Arrays.stream(c.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 从前端 .ts 里抽出 {@code interface X { ... }} 的字段名 */
    private static Set<String> frontendFields(String file, String interfaceName) {
        String src = readFrontend(file);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("export\\s+interface\\s+" + interfaceName + "\\s*\\{([^}]*)\\}", java.util.regex.Pattern.DOTALL)
                .matcher(src);
        if (!m.find()) {
            throw new IllegalStateException("前端 " + file + " 里找不到 interface " + interfaceName
                    + " ⇒ 契约清单已漂移（前端可能改名/删除），必须人工核对");
        }
        Set<String> out = new LinkedHashSet<>();
        java.util.regex.Matcher f = java.util.regex.Pattern
                .compile("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\??\\s*:", java.util.regex.Pattern.MULTILINE)
                .matcher(m.group(1));
        while (f.find()) {
            out.add(f.group(1));
        }
        return out;
    }

    @Test
    @DisplayName("每个 VO 的字段集与前端接口逐字段一致（不多不少）")
    void everyVoMatchesItsFrontendContract() {
        for (VoContract c : VO_CONTRACTS) {
            Set<String> expected = frontendFields(c.frontendFile(), c.frontendInterface());
            Set<String> actual = fieldsOf(c.vo());

            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new LinkedHashSet<>(actual);
            extra.removeAll(expected);

            assertTrue(missing.isEmpty(),
                    c.vo().getSimpleName() + " 缺少前端已声明的字段 " + missing + " ⇒ 前端读到 undefined");
            assertTrue(extra.isEmpty(),
                    c.vo().getSimpleName() + " 多暴露了前端接口没有的字段 " + extra
                            + " ⇒ 要么前端类型该补，要么 VO 该收窄（铁律 #13）");
        }
    }

    @Test
    @DisplayName("服务端内部/权限字段不得出现在任何 VO 里")
    void internalFieldsNeverLeak() {
        for (VoContract c : VO_CONTRACTS) {
            Set<String> leaked = new LinkedHashSet<>(fieldsOf(c.vo()));
            leaked.retainAll(NEVER_EXPOSE);
            assertTrue(leaked.isEmpty(),
                    c.vo().getSimpleName() + " 外露了内部字段 " + leaked
                            + " —— 审计/租户/逻辑删除是内部状态，menuIds/permissionCodes 是权限数据");
        }
    }

    @Test
    @DisplayName("守卫自身有效：前端源文件真的可读且清单非空（防清单凭空漂移）")
    void frontendSourcesAreReadable() {
        for (VoContract c : VO_CONTRACTS) {
            Set<String> f = frontendFields(c.frontendFile(), c.frontendInterface());
            assertTrue(f.size() >= 5,
                    c.frontendInterface() + " 从前端只解析出 " + f.size() + " 个字段（" + f
                            + "）⇒ 正则没匹配上或前端接口已空，本守卫会失去意义");
            assertTrue(!fieldsOf(c.vo()).isEmpty(), c.vo().getSimpleName() + " 一个字段都没有，VO 空转");
        }
    }

    private static String readFrontend(String relPath) {
        Path p = Paths.get(relPath);
        assertTrue(Files.exists(p),
                "找不到前端源文件 " + p.toAbsolutePath() + "（测试以 backend/ 为工作目录运行）");
        try {
            return Files.readString(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}