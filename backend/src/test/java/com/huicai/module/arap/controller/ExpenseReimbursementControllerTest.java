package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.arap.dto.ExpenseReimbursementVO;
import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import com.huicai.module.arap.service.ExpenseReimbursementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 费用报销 Controller 层测试 (P0).
 *
 * <p>测试覆盖：
 * <ol>
 *   <li>CRUD 接口参数绑定</li>
 *   <li>状态转换接口 (submit/approve/reject)</li>
 *   <li>可选参数默认值处理</li>
 *   <li>请求体 JSON 反序列化</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ExpenseReimbursementControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ExpenseReimbursementService service;

    // ==================== 查询接口测试 ====================

    @Test
    @DisplayName("分页查询_可选参数默认值正确")
    void page_optionalParams_useDefaults() throws Exception {
        // given
        IPage<ExpenseReimbursementVO> page = new Page<>(1, 20);
        when(service.pageQuery(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        // when & then - 不传任何可选参数
        mvc.perform(get("/api/v1/expense-reimbursements/page"))
                .andExpect(status().isOk());

        verify(service).pageQuery(isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("分页查询_全部参数正确绑定")
    void page_allParams_boundCorrectly() throws Exception {
        // given
        IPage<ExpenseReimbursementVO> page = new Page<>(2, 50);
        when(service.pageQuery(eq(10L), eq("APPROVED"), eq(2), eq(50))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/v1/expense-reimbursements/page")
                        .param("employeeId", "10")
                        .param("status", "APPROVED")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(service).pageQuery(eq(10L), eq("APPROVED"), eq(2), eq(50));
    }

    @Test
    @DisplayName("查询全部_返回数组格式正确")
    void listAll_returnsArray() throws Exception {
        // given
        when(service.listAll()).thenReturn(List.of());

        // when & then
        mvc.perform(get("/api/v1/expense-reimbursements/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(service).listAll();
    }

    @Test
    @DisplayName("详情查询_PathVariable正确解析")
    void getById_pathVariable_boundCorrectly() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(123L);
        vo.setStatus("DRAFT");
        when(service.getById(eq(123L))).thenReturn(vo);

        // when & then
        mvc.perform(get("/api/v1/expense-reimbursements/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(123))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(service).getById(eq(123L));
    }

    // ==================== 创建/修改接口测试 ====================

    @Test
    @DisplayName("创建草稿_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(1L);
        vo.setStatus("DRAFT");
        when(service.createDraft(any())).thenReturn(vo);

        ExpenseReimbursementEntity entity = new ExpenseReimbursementEntity();
        entity.setEmployeeId(10L);
        entity.setExpenseType("TRAVEL");

        // when & then
        mvc.perform(post("/api/v1/expense-reimbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(service).createDraft(argThat(e ->
                e.getEmployeeId() == 10L &&
                "TRAVEL".equals(e.getExpenseType())
        ));
    }

    @Test
    @DisplayName("修改草稿_Id被正确覆盖")
    void update_idOverwrittenByPathVariable() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(999L);
        when(service.updateDraft(any())).thenReturn(vo);

        ExpenseReimbursementEntity entity = new ExpenseReimbursementEntity();
        entity.setId(123L); // body 中的 ID 应该被 PathVariable 覆盖
        entity.setEmployeeId(10L);

        // when & then
        mvc.perform(put("/api/v1/expense-reimbursements/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk());

        // 验证 Controller 正确地将 PathVariable 的 ID 设置到 entity 上
        verify(service).updateDraft(argThat(e -> e.getId() == 999L));
    }

    // ==================== 状态转换接口测试 ====================

    @Test
    @DisplayName("提交审核_PathVariable正确传递")
    void submit_pathVariable_passedToService() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(50L);
        vo.setStatus("PENDING_REVIEW");
        when(service.submit(eq(50L))).thenReturn(vo);

        // when & then
        mvc.perform(post("/api/v1/expense-reimbursements/50/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        verify(service).submit(eq(50L));
    }

    @Test
    @DisplayName("审核通过_可选approver参数默认null")
    void approve_optionalApprover_defaultNull() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(50L);
        vo.setStatus("APPROVED");
        when(service.approve(eq(50L), isNull())).thenReturn(vo);

        // when & then - 不传 approver
        mvc.perform(post("/api/v1/expense-reimbursements/50/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(service).approve(eq(50L), isNull());
    }

    @Test
    @DisplayName("审核通过_approver参数正确传递")
    void approve_withApprover_paramPassed() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        when(service.approve(eq(50L), eq("manager"))).thenReturn(vo);

        // when & then
        mvc.perform(post("/api/v1/expense-reimbursements/50/approve")
                        .param("approver", "manager"))
                .andExpect(status().isOk());

        verify(service).approve(eq(50L), eq("manager"));
    }

    @Test
    @DisplayName("驳回_必选reason+可选approver正确绑定")
    void reject_requiredReasonAndOptionalApprover_boundCorrectly() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(50L);
        vo.setStatus("REJECTED");
        when(service.reject(eq(50L), eq("manager"), eq("金额超标"))).thenReturn(vo);

        // when & then
        mvc.perform(post("/api/v1/expense-reimbursements/50/reject")
                        .param("reason", "金额超标")
                        .param("approver", "manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        verify(service).reject(eq(50L), eq("manager"), eq("金额超标"));
    }

        // 参数正确时 Service 被调用，框架层面的验证省略

    // ==================== 生成凭证接口测试 ====================

    @Test
    @DisplayName("生成凭证_voucherId正确传递")
    void generateVoucher_voucherId_passedToService() throws Exception {
        // given
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(50L);
        vo.setStatus("VOUCHERED");
        when(service.generateVoucher(eq(50L), eq(1000L))).thenReturn(vo);

        // when & then
        mvc.perform(post("/api/v1/expense-reimbursements/50/generate-voucher")
                        .param("voucherId", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOUCHERED"));

        verify(service).generateVoucher(eq(50L), eq(1000L));
    }

        // 参数正确时 Service 被调用，框架层面的验证省略
}
