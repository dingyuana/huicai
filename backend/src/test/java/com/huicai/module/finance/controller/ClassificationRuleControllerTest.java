package com.huicai.module.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.service.ClassificationRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ClassificationRuleControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ClassificationRuleService classificationRuleService;

    @Test
    @DisplayName("分页查询_默认参数返回200")
    void page_defaultParams_returnsOk() throws Exception {
        IPage<ClassificationRuleEntity> page = new Page<>(1, 20);
        when(classificationRuleService.page(any(), anyInt(), anyInt())).thenReturn(page);

        mvc.perform(get("/api/v1/classification-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<ClassificationRuleEntity> page = new Page<>(2, 10);
        when(classificationRuleService.page(eq(1001L), eq(2), eq(10))).thenReturn(page);

        mvc.perform(get("/api/v1/classification-rules")
                        .param("tenantId", "1001")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(classificationRuleService).page(eq(1001L), eq(2), eq(10));
    }

    @Test
    @DisplayName("创建规则_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        ClassificationRuleEntity input = new ClassificationRuleEntity();
        input.setName("银行手续费");
        input.setPattern("手续费|服务费");
        input.setClassification("bank_fee");
        input.setRouteType("A");

        ClassificationRuleEntity created = new ClassificationRuleEntity();
        created.setId(1L);
        created.setName("银行手续费");
        created.setPattern("手续费|服务费");
        created.setClassification("bank_fee");
        created.setRouteType("A");

        when(classificationRuleService.create(any())).thenReturn(created);

        mvc.perform(post("/api/v1/classification-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("银行手续费"));

        verify(classificationRuleService).create(argThat(e -> "银行手续费".equals(e.getName())));
    }

    @Test
    @DisplayName("获取规则详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        ClassificationRuleEntity entity = new ClassificationRuleEntity();
        entity.setId(1L);
        entity.setName("银行手续费");
        entity.setClassification("bank_fee");

        when(classificationRuleService.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/v1/classification-rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("银行手续费"));
    }

    @Test
    @DisplayName("获取规则详情_不存在时返回400")
    void getById_notExists_returns400() throws Exception {
        when(classificationRuleService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/classification-rules/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("规则不存在"));
    }
}