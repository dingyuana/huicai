package com.huicai.module.finance.controller;

import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import org.junit.jupiter.api.Disabled;
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
class VoucherTemplateControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VoucherTemplateService templateService;

    @MockBean
    private SubjectMapper subjectMapper;

    @Test
    @DisplayName("模板列表_返回200和模板列表")
    void list_returnsTemplates() throws Exception {
        VoucherTemplateEntity tpl = new VoucherTemplateEntity();
        tpl.setId(1L);
        tpl.setName("银行手续费");
        tpl.setClassification("bank_fee");
        tpl.setIsActive(true);

        when(templateService.listAllActive()).thenReturn(List.of(tpl));
        when(templateService.getLines(eq(1L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/voucher-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("银行手续费"))
                .andExpect(jsonPath("$.data[0].lines").isArray());
    }

    @Test
    @DisplayName("按分类筛选模板_返回200")
    void list_filterByClassification_returnsOk() throws Exception {
        VoucherTemplateEntity tpl = new VoucherTemplateEntity();
        tpl.setId(1L);
        tpl.setName("银行手续费");
        tpl.setClassification("bank_fee");

        when(templateService.matchByClassification(eq("bank_fee"))).thenReturn(tpl);
        when(templateService.getLines(eq(1L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/voucher-templates")
                        .param("classification", "bank_fee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("银行手续费"));
    }

    @Test
    @DisplayName("获取模板详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        VoucherTemplateEntity tpl = new VoucherTemplateEntity();
        tpl.setId(1L);
        tpl.setName("银行手续费");

        when(templateService.getById(eq(1L))).thenReturn(tpl);
        when(templateService.getLines(eq(1L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/voucher-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("银行手续费"));
    }

    @Test
    @DisplayName("获取模板详情_不存在时返回400")
    void getById_notExists_returns400() throws Exception {
        when(templateService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/voucher-templates/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("模板不存在"));
    }

    @Test
    @Disabled("MinIO 依赖不在测试环境中")
    @DisplayName("创建模板_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        VoucherTemplateEntity created = new VoucherTemplateEntity();
        created.setId(1L);
        created.setName("银行手续费");
        created.setClassification("bank_fee");
        created.setNumberPrefix("JZ");
        created.setIsActive(true);

        when(templateService.create(any(), any())).thenReturn(created);
        when(templateService.getLines(eq(1L))).thenReturn(List.of());

        String json = """
                {"name":"银行手续费","classification":"bank_fee"}
                """;

        mvc.perform(post("/api/v1/voucher-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("银行手续费"));
    }

    @Test
    @DisplayName("更新模板_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        doNothing().when(templateService).update(any());

        String json = """
                {"name":"银行手续费-更新"}
                """;

        mvc.perform(put("/api/v1/voucher-templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).update(argThat(t -> t.getId() == 1L));
    }

    @Test
    @DisplayName("删除模板_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(templateService).delete(anyLong());

        mvc.perform(delete("/api/v1/voucher-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).delete(eq(1L));
    }
}