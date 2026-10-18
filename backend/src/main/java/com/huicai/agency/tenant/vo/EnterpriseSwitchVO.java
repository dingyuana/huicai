package com.huicai.agency.tenant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseSwitchVO {
    private Long enterpriseId;
    private String enterpriseName;
    private Boolean seedDataDone;
    private String startPeriod;

    /**
     * SPEC-P124 切片3：切换后**重签**的 access token（携带新的 enterpriseId）。
     *
     * <p>此前切换只改前端本地状态，token 里的 enterpriseId 永远是登录时的值。
     * 一旦「废止 X-Enterprise-Id 请求头」，切换就会随续期丢失。
     */
    private String token;

    /**
     * SPEC-P124 切片3：<b>必须与 access 一并重签</b>。
     *
     * <p>refresh token 是在<b>登录时</b>签发的，其 enterpriseId 同样是登录时的值。
     * 若只换 access 不换 refresh，则 access 过期后走 /auth/refresh 换回的仍是
     * <b>旧企业</b> —— 切换被悄悄回退。故二者必须同时签发。
     */
    private String refreshToken;
}
