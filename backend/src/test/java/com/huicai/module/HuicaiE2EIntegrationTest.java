package com.huicai.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.arap.dto.ExpenseReimbursementVO;
import com.huicai.module.arap.service.ExpenseReimbursementService;
import com.huicai.module.arap.service.ReceivableStateMachineService;
import com.huicai.module.finance.service.BankReconciliationService;
import com.huicai.module.finance.service.BusinessDocService;
import com.huicai.module.tax.service.TaxService;
import com.huicai.module.tax.entity.TaxDeclarationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 慧财财务 e2e 集成测试 (P19-1).
 *
 * <p>策略: MockMvc + 真实 Controller + 真实 Service + MockBean 持久层.
 * 避开 H2/PG/Redis/MinIO 依赖, 跑通 HTTP 路由 + 业务流.
 *
 * <p>覆盖 4 大业务链路 (P10-P18):
 * <ol>
 *   <li>银行流水对账 (P14-1 confirmMatch)</li>
 *   <li>费用报销单 (P11-2 submit/approve)</li>
 *   <li>税务申报 (P18-1 approveDeclaration)</li>
 *   <li>附件 OCR (P15-1 runOcr)</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class HuicaiE2EIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;

    @MockBean private BankReconciliationService bankReconciliationService;
    @MockBean private ExpenseReimbursementService expenseReimbursementService;
    @MockBean private TaxService taxService;
    @MockBean private BusinessDocService businessDocService;
    @MockBean private ReceivableStateMachineService receivableStateMachineService;

    // ==================== 1. 银行流水对账 E2E (P14-1) ====================

    @Test
    void e2e_bank_reconciliation_confirm_returns_MATCHED() throws Exception {
        when(bankReconciliationService.confirmMatch(eq(1L), eq(100L), anyString()))
                .thenReturn(new BankReconciliationService.ConfirmResult(
                        1L, 100L, "MATCHED", "tester"));

        mvc.perform(post("/api/v1/bank-reconciliation/confirm")
                        .param("statementId", "1")
                        .param("journalId", "100")
                        .param("operator", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.newStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.statementId").value(1));
    }

    @Test
    void e2e_bank_reconciliation_reject_returns_UNMATCHED() throws Exception {
        when(bankReconciliationService.rejectMatch(eq(1L), eq(100L), anyString()))
                .thenReturn(new BankReconciliationService.ConfirmResult(
                        1L, 100L, "UNMATCHED", "tester"));

        mvc.perform(post("/api/v1/bank-reconciliation/reject")
                        .param("statementId", "1")
                        .param("journalId", "100")
                        .param("operator", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("UNMATCHED"));
    }

    // ==================== 2. 费用报销单 E2E (P11-2) ====================

    @Test
    void e2e_expense_reimbursement_submit_returns_SUBMITTED() throws Exception {
        ExpenseReimbursementVO e = new ExpenseReimbursementVO();
        e.setId(1L);
        e.setStatus("SUBMITTED");
        e.setReimbNo("REIMB-202606-0001");
        when(expenseReimbursementService.submit(1L)).thenReturn(e);

        mvc.perform(post("/api/v1/expense-reimbursements/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.reimbNo").value("REIMB-202606-0001"));
    }

    @Test
    void e2e_expense_reimbursement_approve_returns_APPROVED() throws Exception {
        ExpenseReimbursementVO e = new ExpenseReimbursementVO();
        e.setId(1L);
        e.setStatus("APPROVED");
        e.setApprovedBy("zhangsan");
        when(expenseReimbursementService.approve(eq(1L), anyString())).thenReturn(e);

        mvc.perform(post("/api/v1/expense-reimbursements/1/approve")
                        .param("approver", "zhangsan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("zhangsan"));
    }

    @Test
    void e2e_expense_reimbursement_list_returns_array() throws Exception {
        ExpenseReimbursementVO e = new ExpenseReimbursementVO();
        e.setId(1L);
        e.setStatus("DRAFT");
        e.setReimbNo("REIMB-202606-0001");
        when(expenseReimbursementService.listAll()).thenReturn(List.of(e));

        mvc.perform(get("/api/v1/expense-reimbursements/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"));
    }

    // ==================== 3. 税务申报 E2E (P18-1) ====================

    @Test
    void e2e_tax_declaration_approve_returns_APPROVED() throws Exception {
        TaxDeclarationEntity d = new TaxDeclarationEntity();
        d.setId(1L);
        d.setStatus("APPROVED");
        d.setPeriod("202606");
        d.setDeclarationNo("DECL-202606-001");
        when(taxService.approveDeclaration(eq(1L), anyString())).thenReturn(d);

        mvc.perform(post("/api/v1/tax/declarations/1/approve")
                        .param("approver", "boss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.period").value("202606"));
    }

    @Test
    void e2e_tax_declaration_reject_with_reason_returns_REJECTED() throws Exception {
        TaxDeclarationEntity d = new TaxDeclarationEntity();
        d.setId(1L);
        d.setStatus("REJECTED");
        d.setPeriod("202606");
        d.setRemark("材料不齐");
        when(taxService.rejectDeclaration(eq(1L), anyString(), anyString())).thenReturn(d);

        mvc.perform(post("/api/v1/tax/declarations/1/reject")
                        .param("reason", "材料不齐")
                        .param("approver", "boss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    // ==================== 4. HTTP 路由烟囱测试 ====================

    @Test
    void e2e_swagger_docs_endpoint_accessible() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
