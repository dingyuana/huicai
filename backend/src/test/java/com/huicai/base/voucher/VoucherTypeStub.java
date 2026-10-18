package com.huicai.base.voucher;

import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * SPEC-P128 测试夹具：凭证类型按 code 解析后的 mock 桩。
 *
 * <p>⚠️ 之所以需要它：改造前制证代码写死主键 {@code 1L}，测试无需任何凭证类型依赖；
 * 改造后走 {@code requireByCode(code)}，若测试只提供 mock 而<b>不打桩</b>，
 * 返回 null ⇒ {@code .getId()} 抛 NPE，报错信息完全看不出是「凭证类型未打桩」——
 * 这正是 §4.5 第 52 条说的「症状与真实原因无关」。
 *
 * <p>桩返回 <b>固定 id=1</b>：这样绝大多数既有断言（如凭证号前缀、类型 id 校验）不必改，
 * 而「是否按 code 解析」由 {@code VoucherTypeHardcodeStructureTest} 结构守卫负责。
 */
public final class VoucherTypeStub {

    private VoucherTypeStub() {}

    /**
     * 业务编码 → 主键。取值与**企业 1 的真实种子数据**一致（JZ=1 / SK=2 / FK=3 / ZZ=4）。
     *
     * <p>⚠️ 为什么不一律返回 1L：既有测试里有断言「凭证类型 id == 2」这类
     * （如 {@code ArapSettlementServiceImplTest} 按 SK 期望 id=2）。
     * 桩若与真实映射不符，报错会变成 {@code PotentialStubbingProblem}
     * 「strict stubbing argument mismatch」—— 与「凭证类型解析错了」毫无关系，极难定位。
     */
    private static final java.util.Map<String, Long> CODE_IDS = java.util.Map.of(
            "JZ", 1L, "SK", 2L, "FK", 3L, "ZZ", 4L);

    /** 供 {@code @Mock} 字段配套调用：lenient 打桩，避免 strict stubbing 误报 */
    public static void install(VoucherTypeService service) {
        lenient().when(service.requireByCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            VoucherTypeEntity e = new VoucherTypeEntity();
            e.setId(CODE_IDS.getOrDefault(code, 1L));
            e.setCode(code);
            e.setName(code);
            e.setEnterpriseId(1L);
            return e;
        });
    }

    /** 不想声明字段的测试可直接用它 */
    public static VoucherTypeService mockWithStub() {
        VoucherTypeService s = mock(VoucherTypeService.class);
        install(s);
        return s;
    }
}
