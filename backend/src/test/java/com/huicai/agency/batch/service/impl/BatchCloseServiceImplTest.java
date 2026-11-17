package com.huicai.agency.batch.service.impl;

import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BatchCloseServiceImplTest {

    private final PeriodCloseService periodCloseService = mock(PeriodCloseService.class);
    private final AgencyBatchTransactionRunner transactionRunner =
            mock(AgencyBatchTransactionRunner.class);
    private final BatchCloseServiceImpl service =
            new BatchCloseServiceImpl(periodCloseService, transactionRunner);

    @Test
    void closeContinuesPerEnterpriseAfterOneFailure() {
        doAnswer(invocation -> {
            Long enterpriseId = invocation.getArgument(0);
            if (enterpriseId == 2L) {
                throw BusinessException.badRequest("结账检查未通过");
            }
            return ((Supplier<?>) invocation.getArgument(1)).get();
        }).when(transactionRunner).run(anyLong(), any(Supplier.class));

        var result = service.closePeriods(List.of(1L, 2L, 3L), "202611", 99L);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertFalse(result.getDetails().get(1).isSuccess());
        assertEquals("结账检查未通过", result.getDetails().get(1).getMessage());
        verify(periodCloseService, times(2)).closePeriod("202611", 99L);
    }

    @Test
    void rejectsNonCanonicalPeriodBeforeProcessing() {
        assertThrows(BusinessException.class,
                () -> service.closePeriods(List.of(1L), "2026-11", 99L));

        verify(transactionRunner, never()).run(anyLong(), any(Supplier.class));
        verify(periodCloseService, never()).closePeriod(any(), anyLong());
    }

}
