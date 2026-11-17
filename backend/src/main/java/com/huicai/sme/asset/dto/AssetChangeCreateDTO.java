package com.huicai.sme.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SPEC-P144（REQ-2026-161 / roadmap P0-2 D-4）：资产变动（调整）入参。
 *
 * <p>{@code changeType} 取值集以真库约束 {@code chk_change_type} 为准：
 * {@code VALUE_ADJUST / DEPT_TRANSFER / STATUS_CHANGE / DEPRECIATION}。
 * 本入口只开放前三类 —— {@code DEPRECIATION} 是计提流程的专用类型
 * （{@code AssetCardServiceImpl#depreciatePeriod} 与
 * {@code DepreciationVoucherServiceImpl} 写入），从调整入口创建会
 * 绕过计提算法与防重闸门，直接 400。</p>
 *
 * <h3>各类型的必填维度（服务层校验）</h3>
 * <ul>
 *   <li>{@code VALUE_ADJUST}：{@code amount} 必填且非零 —— 正数调增
 *       （借固定资产/贷银行存款），负数调减（反向分录；调减后净值不得为负）；</li>
 *   <li>{@code DEPT_TRANSFER}：{@code deptId} 必填 —— 部门间转移，不涉金额、不制证；</li>
 *   <li>{@code STATUS_CHANGE}：暂不开放（400）—— 状态流转必须走卡片状态机
 *       （{@code AssetCardStateMachineService}），从调整入口改状态会绕过合法流转表。</li>
 * </ul>
 */
@Data
public class AssetChangeCreateDTO {

    @NotNull(message = "资产ID不能为空")
    private Long assetId;

    @NotNull(message = "变动类型不能为空")
    @Pattern(regexp = "VALUE_ADJUST|DEPT_TRANSFER|STATUS_CHANGE",
            message = "变动类型仅支持 VALUE_ADJUST / DEPT_TRANSFER / STATUS_CHANGE"
                    + "（DEPRECIATION 为计提流程专用，请走折旧计提入口）")
    private String changeType;

    @NotNull(message = "变动日期不能为空")
    private LocalDate changeDate;

    /** VALUE_ADJUST 专用：正数调增 / 负数调减。 */
    private BigDecimal amount;

    /** DEPT_TRANSFER 专用：目标部门。 */
    private Long deptId;

    private String remark;
}
