package com.huicai.agency.batch.service.impl;

import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BatchAuditServiceImplTest {

    private final VoucherService voucherService = mock(VoucherService.class);
    private final OutputInvoiceStateMachineService outputInvoiceStateMachine =
            mock(OutputInvoiceStateMachineService.class);
    private final InputInvoiceStateMachineService inputInvoiceStateMachine =
            mock(InputInvoiceStateMachineService.class);
    private final AgencyBatchTransactionRunner transactionRunner =
            mock(AgencyBatchTransactionRunner.class);
    private final BatchAuditServiceImpl service = new BatchAuditServiceImpl(
            voucherService, outputInvoiceStateMachine, inputInvoiceStateMachine, transactionRunner);

    @Test
    void voucherAuditContinuesAfterAnItemFails() {
        runInline();
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id == 2L) {
                throw BusinessException.badRequest("凭证状态不允许审核");
            }
            return null;
        }).when(voucherService).audit(anyLong(), anyLong());

        var result = service.auditVouchers(List.of(1L, 2L, 3L), 8L, 99L);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertTrue(result.getDetails().get(0).isSuccess());
        assertFalse(result.getDetails().get(1).isSuccess());
        assertEquals("凭证状态不允许审核", result.getDetails().get(1).getMessage());
        verify(voucherService).audit(1L, 99L);
        verify(voucherService).audit(2L, 99L);
        verify(voucherService).audit(3L, 99L);
    }

    @Test
    void invoiceAuditUsesExplicitInvoiceType() {
        runInline();

        var inputResult = service.auditInvoices(List.of(11L), 8L, 99L, "input");
        var outputResult = service.auditInvoices(List.of(12L), 8L, 99L, "output");

        assertEquals(1, inputResult.getSuccess());
        assertEquals(1, outputResult.getSuccess());
        verify(inputInvoiceStateMachine).confirm(11L, 99L);
        verify(outputInvoiceStateMachine).confirm(12L, 99L);
    }

    @Test
    void invalidInvoiceTypeIsRejectedWithoutCallingStateMachines() {
        assertThrows(BusinessException.class,
                () -> service.auditInvoices(List.of(11L), 8L, 99L, "unknown"));

        verify(inputInvoiceStateMachine, never()).confirm(anyLong(), anyLong());
        verify(outputInvoiceStateMachine, never()).confirm(anyLong(), anyLong());
    }

    private void runInline() {
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(transactionRunner).run(anyLong(), any(Supplier.class));
    }
}
