package com.huicai.base.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huicai.base.system.entity.VoucherTypeEntity;

/**
 * 凭证类型 Service
 */
public interface VoucherTypeService extends IService<VoucherTypeEntity> {

    /**
     * 取<b>当前企业</b>的指定业务编码凭证类型。
     *
     * <p>🔴 SPEC-P128：{@code t_voucher_type} 是租户表（ENABLE + FORCE RLS，谓词
     * {@code enterprise_id = app.enterprise_id}），其主键 id <b>只在企业 1 上恰好是 1~4</b>。
     * 其它企业用 {@code getById(1L)} 会被 RLS 过滤返回 null ⇒ 表现为「凭证类型不存在」；
     * 而 {@code setVoucherTypeId(1L)} 更是静默把凭证挂到<b>别的企业</b>的凭证类型上（外键仍成立）。
     * 故凭证类型必须按<b>业务编码</b>解析，而非按主键。
     *
     * <p>企业范围由 RLS + 应用层拦截器自动收敛，本方法不手工拼 enterprise_id ——
     * 手工拼会与 RLS 形成两个 AND，且一旦漏判即静默读 0 行（§4.5 第 40 条）。
     *
     * @param code 业务编码，取值 JZ / SK / FK / ZZ，非主键
     * @return 该企业的凭证类型实体
     * @throws com.huicai.common.exception.BusinessException 本企业未配置该 code 时抛出，
     *         且文案必须回答「去哪配」（§4.5 第 50 条），不得只说「不存在」
     */
    VoucherTypeEntity requireByCode(String code);
}
