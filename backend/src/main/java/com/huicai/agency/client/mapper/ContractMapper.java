package com.huicai.agency.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.agency.client.entity.ContractEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContractMapper extends BaseMapper<ContractEntity> {

    /**
     * 30 天内到期且未发提醒的合同（续费提醒）。
     *
     * <p>🔴 <b>V177 起按 {@code agency_id} 收敛</b>：原实现<b>完全不带任何归属条件</b>，
     * 业务上就是「扫本代理全部客户」。V156 给本表开了 RLS
     * （{@code enterprise_id = app.enterprise_id}），而该表的 {@code enterprise_id}
     * 语义是「签约客户」⇒ 谓词把<b>本代理的其它客户</b>全挡掉，
     * 探针实测同代理 3 个客户的合同只返 1 条，续费提醒静默消失。
     *
     * <p>⚠️ <b>{@code agencyId} 为 null 时必须不加条件，而不是 {@code = NULL}</b> ——
     * {@code agency_id = NULL} 在 SQL 里恒不匹配，会让超管看到 <b>0 条</b>。
     * 这里用显式 {@code <if>} 判空，超管（可跨代理）才走全量分支。
     *
     * @param agencyId 代理公司 ID；{@code null} 表示不限（超管）
     */
    @Select("<script>"
            + "SELECT * FROM t_contract WHERE end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30 "
            + "AND status = 'ACTIVE' AND renewal_notice_sent = FALSE AND deleted = 0 "
            + "<if test='agencyId != null'> AND agency_id = #{agencyId} </if>"
            + "ORDER BY end_date ASC"
            + "</script>")
    List<ContractEntity> findRenewalReminders(@Param("agencyId") Long agencyId);
}