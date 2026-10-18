package com.huicai.agency.tenant;

import com.huicai.agency.tenant.dto.EnterpriseCreateDTO;
import com.huicai.agency.tenant.dto.EnterpriseVO;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.agency.tenant.util.ContactMasker;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P129 真实 DB 测试：企业联系信息落库 + PII 服务端脱敏（D-1 裁定）。
 *
 * <p>🔴 <b>为什么必须断言「查库」而不是只看 VO</b>：AGENTS §4.2 第 16 条 ——
 * 「前端/VO 里有值」不等于「落库了」。本次改造若漏了 create/update 的赋值，
 * 页面照常显示而库里是 NULL，且所有 VO 层断言照样全绿。
 *
 * <p>🔴 <b>为什么脱敏必须在此层测</b>：前端遮挡等于明文已到浏览器（SPEC-P129 §2.2）。
 * 只有接口层断言「非管理员拿到的确实是脱敏值」才算数。
 */
@DisplayName("SPEC-P129：企业联系信息落库与 PII 脱敏（真实 DB）")
class EnterpriseContactMaskRealDBTest extends AbstractMapperTest {

    @Autowired
    private EnterpriseService enterpriseService;

    @Autowired
    private EnterpriseMapper enterpriseMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private EnterpriseVO createWithContact() {
        EnterpriseCreateDTO dto = new EnterpriseCreateDTO();
        dto.setEnterpriseCode("P129-" + System.nanoTime() % 1000000);
        dto.setEnterpriseName("P129 联系信息测试企业");
        dto.setTaxId("91370200TEST");
        dto.setContactName("张三");
        dto.setContactPhone("13812345678");
        dto.setContactEmail("zhangsan@example.com");
        dto.setAddress("山东省青岛市市南区测试路 1 号");
        return enterpriseService.create(dto);
    }

    @Test
    @DisplayName("AT-129-1 四个联系字段必须真正落库（不得只在前端/VO 显示）")
    void contactFieldsArePersisted() {
        EnterpriseVO vo = createWithContact();
        assertNotNull(vo.getId());

        // ⚠️ 走 JDBC 直查：MP 读回与 JDBC 读回若有分歧，本身就是线索
        EnterpriseEntity loaded = jdbcTemplate.queryForObject(
                "SELECT contact_name, contact_phone, contact_email, address "
                        + "FROM t_enterprise WHERE id = ?",
                (rs, i) -> {
                    EnterpriseEntity e = new EnterpriseEntity();
                    e.setContactName(rs.getString("contact_name"));
                    e.setContactPhone(rs.getString("contact_phone"));
                    e.setContactEmail(rs.getString("contact_email"));
                    e.setAddress(rs.getString("address"));
                    return e;
                }, vo.getId());

        assertEquals("张三", loaded.getContactName(), "联系人未落库");
        assertEquals("13812345678", loaded.getContactPhone(), "电话未落库");
        assertEquals("zhangsan@example.com", loaded.getContactEmail(), "邮箱未落库");
        assertEquals("山东省青岛市市南区测试路 1 号", loaded.getAddress(), "地址未落库");

        // 🔴 必须再断言<b>出参</b>：库里存明文 ≠ 接口返回明文。
        // 首版只断言了 JDBC 读库与 ContactMasker 静态方法，两者都不经过 toVO ——
        // 于是「脱敏在出参层是否生效」根本没被验到。注入「恒返回全值」的反证
        // 竟然 4/4 全绿（假绿），补上本断言后立刻转红。
        // 本用例无登录态 ⇒ 按 fail-closed 应返回脱敏值。
        EnterpriseVO out = enterpriseService.getById(vo.getId());
        assertEquals("138****5678", out.getContactPhone(),
                "无登录态（判不出角色）时必须脱敏返回，而不是明文");
        assertEquals("z***@example.com", out.getContactEmail(),
                "无登录态时邮箱必须脱敏返回");
        assertEquals("张三", out.getContactName(), "联系人非 PII，不应被脱敏");
        assertEquals("山东省青岛市市南区测试路 1 号", out.getAddress(), "地址非 PII，不应被脱敏");
    }

    @Test
    @DisplayName("AT-129-2 四个字段全部不填也必须创建成功（可空是硬要求）")
    void contactFieldsAreOptional() {
        EnterpriseCreateDTO dto = new EnterpriseCreateDTO();
        dto.setEnterpriseCode("P129-NULL-" + System.nanoTime() % 1000000);
        dto.setEnterpriseName("P129 无联系信息企业");

        EnterpriseVO vo = enterpriseService.create(dto);
        assertNotNull(vo.getId(), "未填联系信息时创建失败 —— 可空被当成了必填");
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_enterprise WHERE id = ? AND contact_name IS NULL "
                        + "AND contact_phone IS NULL AND contact_email IS NULL AND address IS NULL",
                Integer.class, vo.getId());
        assertEquals(1, n, "未填时应落 NULL 而非空串（空串会与「已填但为空」混淆）");
    }

    @Test
    @DisplayName("脱敏规则：电话前3后4、邮箱首字符+完整域名")
    void maskRules() {
        assertEquals("138****5678", ContactMasker.maskPhone("13812345678"));
        // SPEC 规则是「前 3 后 4」，不因含区号而改变位数
        assertEquals("053****8888", ContactMasker.maskPhone("0532-88888888"));
        assertEquals("z***@example.com", ContactMasker.maskEmail("zhangsan@example.com"));
        assertNull(ContactMasker.maskPhone(null), "空值不得被改写成 ***");
        assertEquals("", ContactMasker.maskEmail(""));
    }

    @Test
    @DisplayName("🔴 反证自证：脱敏规则必须真的改变值（否则本类会假绿）")
    void maskActuallyChangesValue() {
        String raw = "13812345678";
        String masked = ContactMasker.maskPhone(raw);
        assertTrue(!raw.equals(masked), "脱敏后与原值相同 ⇒ 掩码没生效，本类断言将全部恒真");
        assertTrue(!masked.contains("1234"), "掩码后仍残留中间 4 位");
    }
}
