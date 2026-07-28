package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.business.dto.BusinessDocDTO;
import com.huicai.base.business.dto.BusinessDocQueryDTO;
import com.huicai.base.business.dto.BusinessDocVO;
import com.huicai.base.business.service.BusinessDocService;
import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BusinessDocControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BusinessDocService docService;

    @MockBean
    private VoucherTemplateMapper templateMapper;

    @Test
    @DisplayName("分页查询业务单据_RequestBody正确解析")
    void pageQuery_requestBody_parsedCorrectly() throws Exception {
        when(docService.pageQuery(any(BusinessDocQueryDTO.class))).thenReturn(new Page<>());

        BusinessDocQueryDTO dto = new BusinessDocQueryDTO();
        dto.setStatus("DRAFT");

        mvc.perform(post("/api/sme/arap/v1/business-docs/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询单据详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        BusinessDocVO vo = new BusinessDocVO();
        vo.setId(1L);
        vo.setDocNo("BD-2026-001");
        when(docService.getDetail(eq(1L))).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/business-docs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.docNo").value("BD-2026-001"));
    }

    @Test
    @DisplayName("新增业务单据_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        BusinessDocDTO dto = new BusinessDocDTO();
        dto.setDocType("INVOICE_IN");

        BusinessDocVO created = new BusinessDocVO();
        created.setId(1L);
        when(docService.create(any(BusinessDocDTO.class), anyLong())).thenReturn(created);

        mvc.perform(post("/api/sme/arap/v1/business-docs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新业务单据_RequestBody正确解析")
    void update_requestBody_parsedCorrectly() throws Exception {
        BusinessDocDTO dto = new BusinessDocDTO();
        dto.setDocType("INVOICE_IN");

        BusinessDocVO updated = new BusinessDocVO();
        updated.setId(1L);
        when(docService.update(any(BusinessDocDTO.class), anyLong())).thenReturn(updated);

        mvc.perform(put("/api/sme/arap/v1/business-docs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("删除业务单据_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(docService).delete(eq(1L));

        mvc.perform(delete("/api/sme/arap/v1/business-docs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("提交业务单据_状态流转")
    void submit_statusTransition() throws Exception {
        doNothing().when(docService).submit(eq(1L), anyLong());

        mvc.perform(post("/api/sme/arap/v1/business-docs/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("审批业务单据_审批端点")
    void approve_approveEndpoint() throws Exception {
        doNothing().when(docService).approve(eq(1L), anyLong());

        mvc.perform(post("/api/sme/arap/v1/business-docs/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("驳回业务单据_驳回端点")
    void reject_rejectEndpoint() throws Exception {
        doNothing().when(docService).reject(eq(1L), anyLong());

        mvc.perform(post("/api/sme/arap/v1/business-docs/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("生成凭证_凭证生成端点")
    void generateVoucher_voucherGenerationEndpoint() throws Exception {
        BusinessDocVO vo = new BusinessDocVO();
        vo.setId(1L);
        vo.setVoucherNo("PZ-2026-001");
        when(docService.generateVoucher(eq(1L), anyLong())).thenReturn(vo);

        mvc.perform(post("/api/sme/arap/v1/business-docs/1/generate-voucher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucherNo").value("PZ-2026-001"));
    }

    @Test
    @DisplayName("冲销业务单据_冲销端点")
    void reverse_reverseEndpoint() throws Exception {
        BusinessDocVO vo = new BusinessDocVO();
        vo.setId(1L);
        vo.setStatus("REVERSED");
        when(docService.reverse(eq(1L), anyLong())).thenReturn(vo);

        mvc.perform(post("/api/sme/arap/v1/business-docs/1/reverse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
    }

    @Test
    @DisplayName("查询模板列表_正确返回")
    void templates_listReturned() throws Exception {
        when(templateMapper.selectList(any())).thenReturn(List.of(new VoucherTemplateEntity()));

        mvc.perform(get("/api/sme/arap/v1/business-docs/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}