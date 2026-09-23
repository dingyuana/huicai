package com.huicai.base.voucher.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 一键人工审核记账请求（P84 结账工作台第 2 步"批量审核记账"）。
 *
 * <p>用户在 Drawer 里勾选凭证（或全选）后主动点击触发，属<b>人工操作</b>——
 * 符合"所有单据审核必须人工完成"铁律，系统不会在生成后自动审核。
 *
 * @param voucherIds 待审核并记账的凭证 id 列表（不能为空）
 * @author Hermes
 */
public record BatchReviewPostRequest(
        @NotEmpty(message = "凭证ID列表不能为空") List<Long> voucherIds) {
}
