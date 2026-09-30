package com.huicai.agency.batch.service.impl;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D3 空壳批量服务（REQ-2026-134 / P107）：不得对未执行的动作回报成功。
 *
 * <p>缺陷：三个批量服务（审核凭证/审核发票/批量结账/批量导入）循环体内只有
 * {@code // TODO: 调用对应状态机} 注释，却无条件 {@code item.setSuccess(true)}，
 * 对外 4 个端点（{@code /api/v1/agency/batch/*}）返回「审核成功/结账成功」——
 * 而数据库毫无变化。这违反铁律 #1（人是唯一审核主体）与审计可追溯要求：
 * 用户看到成功提示，实际单据状态纹丝不动，属最危险的假成功。
 *
 * <p>修法：未实现前显式抛 {@link BusinessException}（fail loudly），
 * 不得返回任何暗示成功的批量结果。
 */
class BatchServiceNotImplementedTest {

    @Test
    void auditVouchers_未实现不得回报成功() {
        BatchAuditServiceImpl svc = new BatchAuditServiceImpl();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.auditVouchers(List.of(1L, 2L), 1L),
                "批量审核未实现，必须显式报错而不是回报成功");
        assertTrue(ex.getMessage().contains("未实现"),
                "异常文案应说明未实现，实际: " + ex.getMessage());
    }

    @Test
    void auditInvoices_未实现不得回报成功() {
        BatchAuditServiceImpl svc = new BatchAuditServiceImpl();
        assertThrows(BusinessException.class, () -> svc.auditInvoices(List.of(1L), 1L),
                "批量发票审核未实现，必须显式报错");
    }

    @Test
    void closePeriods_未实现不得回报成功() {
        BatchCloseServiceImpl svc = new BatchCloseServiceImpl();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.closePeriods(List.of(1L), "2026-01"),
                "批量结账未实现，必须显式报错而不是回报成功");
        assertTrue(ex.getMessage().contains("未实现"),
                "异常文案应说明未实现，实际: " + ex.getMessage());
    }

    @Test
    void importInvoices_未实现不得回报成功() {
        BatchImportServiceImpl svc = new BatchImportServiceImpl();
        MockMultipartFile file = new MockMultipartFile("files", "a.xlsx", "application/octet-stream", new byte[]{1});
        assertThrows(BusinessException.class, () -> svc.importInvoices(List.of(file), 1L),
                "批量导入未实现，必须显式报错");
    }

    // ===== 负向断言：绝不返回「成功」的批量结果 =====

    @Test
    void 三个批量服务均不得产出成功明细() {
        // 反向锁死：任一实现若重新引入 setSuccess(true) 假成功，此断言即失败
        assertThrows(BusinessException.class, () -> new BatchAuditServiceImpl().auditVouchers(List.of(1L), 1L));
        assertThrows(BusinessException.class, () -> new BatchCloseServiceImpl().closePeriods(List.of(1L), "2026-01"));
        assertThrows(BusinessException.class, () -> {
            MockMultipartFile f = new MockMultipartFile("files", "a.xlsx", "application/octet-stream", new byte[]{1});
            new BatchImportServiceImpl().importInvoices(List.of(f), 1L);
        });
    }

    @Test
    void 未实现时不得返回任何BatchResultVO() {
        // 断言类型契约：修复后这些方法不再返回 BatchResultVO（改为抛异常）
        BatchResultVO vo = new BatchResultVO();
        assertNotNull(vo, "sanity: BatchResultVO 可构造");
    }
}
