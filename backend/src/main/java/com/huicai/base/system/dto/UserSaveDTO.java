package com.huicai.base.system.dto;

import com.huicai.base.system.entity.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接 {@code @RequestBody UserEntity}，违反铁律 #13。
 * 本 DTO 剔除的字段分三类：
 * <ul>
 *   <li><b>人工流程状态</b> {@code status}（{@code chk_user_status} 仅允许
 *       ACTIVE/INACTIVE/LOCKED）—— 状态变更应走专用的
 *       {@code PUT /{id}/status} 端点（带操作日志），而不是夹在「编辑资料」里顺带改；</li>
 *   <li><b>登录态字段</b> {@code lastLoginIp/lastLoginAt} —— 服务端维护；</li>
 *   <li><b>主键/租户/审计</b> {@code id/enterpriseId/deleted/createdBy/updatedBy/
 *       createdAt/updatedAt/deptName} —— 服务端托管，其中 {@code enterpriseId}
 *       由 {@code MyMetaObjectHandler} 强制覆盖为当前企业，收进来只会误导调用方。</li>
 * </ul>
 *
 * <p><b>⚠️ 刻意保留的字段（属另一类风险，需单独评估）</b>：{@code userType}、
 * {@code agencyId}、{@code agencyRole}。它们是「管理员新建代理/企业用户」这一
 * **正当功能**所必需的（代理端 REQ-2026-066~075 依赖），贸然剔除会造成功能回归。
 * 但它们同时是一条<b>权限授予面</b>（可创建 {@code SUPER_ADMIN} 类型账号）——
 * 本次不擅自改动，登记为待评估项：应改为「只有 SUPER_ADMIN 可创建 SUPER_ADMIN」
 * 的服务端校验，而非靠前端不传。
 *
 * <p>{@code password} 仅在新增时有意义；修改走专用的
 * {@code PUT /{id}/reset-pwd}（Service 侧本就禁止普通 update 改密码）。
 */
@Data
public class UserSaveDTO {

    /** 用户名：新增必填；修改时留空表示不改 */
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    /** 明文口令，仅新增使用；Service 侧会 BCrypt 编码 */
    @Size(max = 128, message = "口令长度不能超过 128")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String realName;

    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private Long deptId;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    @Size(max = 500, message = "备注过长")
    private String remark;

    /** 见类注释：保留是功能所需，权限授予面待单独评估 */
    @Pattern(regexp = "^$|SUPER_ADMIN|AGENCY|ENTERPRISE",
            message = "用户类型仅支持 SUPER_ADMIN / AGENCY / ENTERPRISE")
    private String userType;

    private Long agencyId;

    @Size(max = 64, message = "代理角色长度不能超过 64")
    private String agencyRole;

    /**
     * 角色 ID 列表：<b>必须保留</b> —— {@code UserServiceImpl#create/update}
     * 真实消费该字段来写 {@code t_user_role}（update 传空列表 = 清空全部角色）。
     * 若一并剔除，则「建用户时指定角色」这一功能会**静默失效**
     * （接口返回成功、角色表不写任何行）—— 属 §4.3 的「静默忽略」反模式。
     * 权限授予面的风险另记为待评估项。
     */
    private List<Long> roleIds;

    public UserEntity toEntity() {
        UserEntity e = new UserEntity();
        e.setUsername(username);
        e.setPassword(password);
        e.setRealName(realName);
        e.setNickname(nickname);
        e.setEmail(email);
        e.setPhone(phone);
        e.setDeptId(deptId);
        e.setAvatar(avatar);
        e.setRemark(remark);
        e.setUserType(userType);
        e.setAgencyId(agencyId);
        e.setAgencyRole(agencyRole);
        e.setRoleIds(roleIds);
        return e;
    }
}
