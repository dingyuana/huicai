package com.huicai.base.masterdata.service.impl;

import com.huicai.base.masterdata.dto.EmployeeSaveDTO;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.service.EmployeeService;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P102 出参面批次 3 —— 员工「职位」字段的反向缺口修复回归锁
 *
 * <p><b>缺陷（AGENTS §4.2 第 16 条「反向缺口」）</b>：
 * {@code t_employee.position VARCHAR(100)} <b>自 V1 baseline 建表起就存在</b>，
 * 但 {@code EmployeeEntity} 从未声明它、{@code EmployeeSaveDTO} 也没有 ⇒
 * 该列<b>永不写入、读回恒 null</b>。
 *
 * <p><b>可见症状</b>：前端 {@code EmployeeList.vue} 的「职位」输入框提交后
 * 被静默丢弃（Jackson 绑定不到字段），列表列 {@code prop="position"} 恒空 ——
 * <b>用户填了职位、转头就丢，且不报任何错</b>。
 *
 * <p><b>为什么这里必须是真库测试</b>：本缺陷的三个环节（列存在 / Entity 未声明 /
 * DTO 无字段）Mock 一个都看不见（AGENTS §4.3 第 7 条）。且
 * {@code EmployeeServiceImpl#update} 是「读-改-写」逐字段搬运 ——
 * 只在 Entity/DTO 侧补字段而漏了 update 那一行，<b>编辑一次就会丢</b>
 * （AGENTS §4.4 第 11 条同型）。本类把 insert 与 update <b>两条路径都锁住</b>。
 */
@DisplayName("P102 批次3：员工职位字段（position）真库往返")
class EmployeePositionRealDBTest extends AbstractMapperTest {

    @Autowired
    private EmployeeService employeeService;

    private EmployeeEntity dto(String code, String position) {
        EmployeeSaveDTO d = new EmployeeSaveDTO();
        d.setCode(code);
        d.setName("测试员工-" + code);
        d.setPosition(position);
        d.setPhone("13800000000");
        d.setEmail(code + "@test.local");
        d.setIsActive(Boolean.TRUE);
        return d.toEntity();
    }

    @Test
    @DisplayName("新增：职位真正落库（修复前 DTO 无该字段 ⇒ 静默丢弃）")
    void positionIsPersistedOnCreate() {
        String code = "P102.POS." + System.nanoTime();
        EmployeeEntity saved = employeeService.create(dto(code, "高级工程师"));

        assertNotNull(saved.getId(), "未落库");
        assertEquals("高级工程师", saved.getPosition(), "内存对象即未带上 position（DTO→Entity 漏映射）");

        // 关键：真库回读，而不是信内存对象
        String persisted = jdbcTemplate.queryForObject(
                "SELECT position FROM t_employee WHERE emp_code = ?", String.class, code);
        assertEquals("高级工程师", persisted,
                "position 未真正落库 ⇒ 前端填的职位转头就丢（AGENTS §4.3 静默忽略反模式）");
    }

    @Test
    @DisplayName("修改：职位在 update 路径也保住（读-改-写逐字段搬运，漏一行就丢）")
    void positionSurvivesUpdate() {
        String code = "P102.POS.UPD." + System.nanoTime();
        EmployeeEntity saved = employeeService.create(dto(code, "初级工程师"));

        EmployeeEntity update = dto(code, "资深工程师");
        update.setId(saved.getId());
        employeeService.update(update);

        String persisted = jdbcTemplate.queryForObject(
                "SELECT position FROM t_employee WHERE emp_code = ?", String.class, code);
        assertEquals("资深工程师", persisted,
                "update 后职位回退 —— EmployeeServiceImpl#update 是逐字段搬运，"
                        + "新增字段若不同步补进去，编辑一次就丢（AGENTS §4.4 第 11 条）");
        assertEquals(saved.getId(), jdbcTemplate.queryForObject(
                        "SELECT id FROM t_employee WHERE emp_code = ?", Long.class, code),
                "工号不该被 update 改动");
    }

    @Test
    @DisplayName("守卫：position 是真实列而非幽灵字段（反向缺口的判据）")
    void positionIsARealColumn() {
        String type = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 't_employee' AND column_name = 'position'",
                String.class);
        assertEquals("character varying", type,
                "t_employee.position 不是字符串列或不存在 ⇒ 本类的修复前提不成立，"
                        + "须重新核对 DB（AGENTS §4.2 第 16 条：先确认列真实存在再动手）");
    }

    @Test
    @DisplayName("守卫：3 个幽灵字段确已不参与 SQL（不存在对应列）")
    void ghostFieldsHaveNoColumns() {
        for (String col : new String[]{"bank_name", "bank_account", "id_card"}) {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_name = 't_employee' AND column_name = ?",
                    Integer.class, col);
            assertEquals(0, cnt, "t_employee." + col + " 竟有列 ⇒ EmployeeEntity 上的 "
                    + "exist=false 标注已过时，update 里删掉的三行搬运需要恢复");
        }
        assertNull(new EmployeeEntity().getBankName(),
                "幽灵字段仍应读回 null（exist=false 不参与 SQL）");
    }

    @Test
    @DisplayName("登记：subject_id 是同表另一处未修的反向缺口（本批刻意未扩范围）")
    void subjectIdIsAnotherUnmappedRealColumn() {
        // 有列 + 有 FK，但 EmployeeEntity 未声明 ⇒ 与 position 同型的反向缺口。
        // 本次只修 position（前端在用），subject_id 前端未使用，故单独登记而非顺手改。
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 't_employee' AND column_name = 'subject_id'",
                Integer.class);
        assertEquals(1, cnt,
                "t_employee.subject_id 不存在了 ⇒ 该登记项可关闭；"
                        + "若此断言转红说明 DDL 有变，请复核 EmployeeEntity 是否仍缺该字段");
    }
}