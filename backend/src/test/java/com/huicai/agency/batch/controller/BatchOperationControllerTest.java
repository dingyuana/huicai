package com.huicai.agency.batch.controller;

import com.huicai.agency.batch.dto.BatchResultVO;
import com.huicai.agency.batch.service.BatchAuditService;
import com.huicai.agency.batch.service.BatchCloseService;
import com.huicai.agency.batch.service.BatchImportService;
import com.huicai.agency.security.AgencyEnterpriseRoleGuard;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BatchOperationControllerTest {

    private final BatchImportService importService = mock(BatchImportService.class);
    private final BatchAuditService auditService = mock(BatchAuditService.class);
    private final BatchCloseService closeService = mock(BatchCloseService.class);
    private final AgencyEnterpriseRoleGuard roleGuard = mock(AgencyEnterpriseRoleGuard.class);
    private final BatchOperationController controller =
            new BatchOperationController(importService, auditService, closeService, roleGuard);

    @Test
    void importChecksRoleAndEnterpriseOwnershipBeforeDelegating() {
        var file = new MockMultipartFile("files", "invoice.xlsx", "application/octet-stream", new byte[]{1});
        BatchResultVO result = new BatchResultVO();
        when(importService.importInvoices(List.of(file), 8L, "output")).thenReturn(result);

        controller.importInvoices(List.of(file), 8L, "output");

        InOrder order = inOrder(roleGuard, importService);
        order.verify(roleGuard).requireAgencyAdmin("批量导入");
        order.verify(roleGuard).requireVisibleEnterprise(8L);
        order.verify(importService).importInvoices(List.of(file), 8L, "output");
    }

    @Test
    void deniedEnterpriseOwnershipPreventsServiceExecution() {
        doThrow(BusinessException.forbidden("客户不属于当前代理"))
                .when(roleGuard).requireVisibleEnterprise(99L);

        assertThrows(BusinessException.class, () -> controller.auditVouchers(List.of(1L), 99L));

        verify(roleGuard).requireAgencyAdmin("批量审核凭证");
        verifyNoInteractions(auditService);
    }

    @Test
    void invoiceAuditPassesTheAuthenticatedOperatorAndExplicitType() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(77L);

            controller.auditInvoices(List.of(11L), 8L, "input");

            InOrder order = inOrder(roleGuard, auditService);
            order.verify(roleGuard).requireAgencyAdmin("批量审核发票");
            order.verify(roleGuard).requireVisibleEnterprise(8L);
            order.verify(auditService).auditInvoices(List.of(11L), 8L, 77L, "input");
        }
    }

    @Test
    void closeChecksEveryTargetEnterpriseBeforeDelegating() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(77L);

            controller.closePeriods(List.of(8L, 9L), "202611");

            InOrder order = inOrder(roleGuard, closeService);
            order.verify(roleGuard).requireAgencyAdmin("批量结账");
            order.verify(roleGuard).requireVisibleEnterprise(8L);
            order.verify(roleGuard).requireVisibleEnterprise(9L);
            order.verify(closeService).closePeriods(List.of(8L, 9L), "202611", 77L);
        }
    }
}
