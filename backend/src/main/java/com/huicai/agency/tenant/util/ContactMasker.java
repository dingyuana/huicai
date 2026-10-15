package com.huicai.agency.tenant.util;

/**
 * SPEC-P129 D-1（老丁裁定）：联系方式 PII 脱敏。
 *
 * <p>🔴 <b>必须在服务端做</b>：前端脱敏等于明文已经发到浏览器，脱敏形同虚设。
 *
 * <p>规则：
 * <ul>
 *   <li>电话：保留前 3 后 4，如 {@code 13812345678 -> 138****5678}</li>
 *   <li>邮箱：保留首字符 + 完整域名，如 {@code zhangsan@a.com -> z***@a.com}</li>
 * </ul>
 *
 * <p>⚠️ 抽成独立类而非塞进 Service：本类是<b>纯函数</b>，抽出后才能被单测直接覆盖；
 * 放在 Service 里既是包私有（测试跨包取不到），也让「掩码规则」这一安全要点
 * 淹没在业务逻辑中。
 */
public final class ContactMasker {

    private ContactMasker() {}

    /** 长度不足以安全掩码时的占位：避免「位数规则」本身被推断 */
    private static final String SHORT_MASK = "***";

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String t = phone.trim();
        if (t.length() <= 7) {
            return t.charAt(0) + SHORT_MASK;
        }
        return t.substring(0, 3) + "****" + t.substring(t.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return SHORT_MASK;
        }
        return email.charAt(0) + SHORT_MASK + email.substring(at);
    }
}
