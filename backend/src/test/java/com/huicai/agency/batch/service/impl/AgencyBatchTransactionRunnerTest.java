package com.huicai.agency.batch.service.impl;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.transaction.RequiresNewRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgencyBatchTransactionRunnerTest {

    private final RequiresNewRunner requiresNewRunner = mock(RequiresNewRunner.class);
    private final AgencyBatchTransactionRunner runner = new AgencyBatchTransactionRunner(requiresNewRunner);

    @AfterEach
    void clearContext() {
        EnterpriseContextHolder.clear();
    }

    @Test
    void runsInRequestedEnterpriseAndRestoresPreviousContext() {
        EnterpriseContextHolder.set(12L);
        runInlineInNewTransaction();

        Long result = runner.run(45L, () -> EnterpriseContextHolder.get());

        assertEquals(45L, result);
        assertEquals(12L, EnterpriseContextHolder.get());
    }

    @Test
    void restoresPreviousContextWhenActionFails() {
        EnterpriseContextHolder.set(12L);
        runInlineInNewTransaction();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runner.run(45L, () -> {
                    assertEquals(45L, EnterpriseContextHolder.get());
                    throw new IllegalStateException("item failed");
                }));

        assertEquals("item failed", error.getMessage());
        assertEquals(12L, EnterpriseContextHolder.get());
    }

    @Test
    void invalidEnterpriseIsRejectedBeforeOpeningTransaction() {
        assertThrows(BusinessException.class,
                () -> runner.run(null, () -> "unreachable"));
    }

    @SuppressWarnings("unchecked")
    private void runInlineInNewTransaction() {
        when(requiresNewRunner.run(any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    }
}
