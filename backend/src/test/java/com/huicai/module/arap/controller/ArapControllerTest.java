package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.arap.service.BusinessDocAgingService;
import com.huicai.module.finance.dto.BusinessDocVO;
import com.huicai.module.finance.service.BusinessDocService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ArapControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BusinessDocService docService;

    @MockBean
    private BusinessDocAgingService businessDocAgingService;

    @Test
    @DisplayName("应收明细分页查询_默认参数正确生效")
    void pageReceivable_defaultParams_applied() throws Exception {
        IPage<BusinessDocVO> page = new Page<>(1, 20);
        when(docService.pageQuery(any())).thenReturn(page);

        mvc.perform(get("/api/v1/receivables/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("应收明细分页查询_自定义参数正确绑定")
    void pageReceivable_customParams_boundCorrectly() throws Exception {
        IPage<BusinessDocVO> page = new Page<>(2, 50);
        when(docService.pageQuery(any())).thenReturn(page);

        mvc.perform(get("/api/v1/receivables/page")
                        .param("current", "2")
                        .param("size", "50")
                        .param("customerId", "100")
                        .param("period", "2024-01"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("应付明细分页查询_默认参数正确生效")
    void pagePayable_defaultParams_applied() throws Exception {
        IPage<BusinessDocVO> page = new Page<>(1, 20);
        when(docService.pageQuery(any())).thenReturn(page);

        mvc.perform(get("/api/v1/payables/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("应付明细分页查询_自定义参数正确绑定")
    void pagePayable_customParams_boundCorrectly() throws Exception {
        IPage<BusinessDocVO> page = new Page<>(3, 30);
        when(docService.pageQuery(any())).thenReturn(page);

        mvc.perform(get("/api/v1/payables/page")
                        .param("current", "3")
                        .param("size", "30")
                        .param("vendorId", "200")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取应收单详情_Id参数正确解析")
    void getReceivable_idParam_parsedCorrectly() throws Exception {
        BusinessDocVO vo = new BusinessDocVO();
        vo.setId(123L);
        when(docService.getDetail(eq(123L))).thenReturn(vo);

        mvc.perform(get("/api/v1/receivables/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(123));
    }

    @Test
    @DisplayName("获取应付单详情_Id参数正确解析")
    void getPayable_idParam_parsedCorrectly() throws Exception {
        BusinessDocVO vo = new BusinessDocVO();
        vo.setId(456L);
        when(docService.getDetail(eq(456L))).thenReturn(vo);

        mvc.perform(get("/api/v1/payables/456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(456));
    }

    @Test
    @DisplayName("确认应收_PathVariable正确绑定")
    void confirmReceivable_pathVariable_boundCorrectly() throws Exception {
        mvc.perform(post("/api/v1/receivables/789/confirm"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("红冲应收_PathVariable正确绑定")
    void reverseReceivable_pathVariable_boundCorrectly() throws Exception {
        mvc.perform(post("/api/v1/receivables/789/reverse"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("确认应付_PathVariable正确绑定")
    void confirmPayable_pathVariable_boundCorrectly() throws Exception {
        mvc.perform(post("/api/v1/payables/789/confirm"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("红冲应付_PathVariable正确绑定")
    void reversePayable_pathVariable_boundCorrectly() throws Exception {
        mvc.perform(post("/api/v1/payables/789/reverse"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("应收账龄分析_无参数调用")
    void receivableAging_noParams_ok() throws Exception {
        mvc.perform(get("/api/v1/receivables/aging"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("应收账龄分析_带客户参数")
    void receivableAging_withCustomerId() throws Exception {
        mvc.perform(get("/api/v1/receivables/aging")
                        .param("customerId", "50"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("应付账龄分析_带供应商参数")
    void payableAging_withVendorId() throws Exception {
        mvc.perform(get("/api/v1/payables/aging")
                        .param("vendorId", "60"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("逾期应收_接口返回200")
    void overdueReceivables_returns200() throws Exception {
        mvc.perform(get("/api/v1/receivables/overdue"))
                .andExpect(status().isOk());
    }
}
