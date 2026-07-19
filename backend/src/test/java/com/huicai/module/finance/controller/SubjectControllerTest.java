package com.huicai.base.system.controller;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.model.vo.SubjectTreeVO;
import com.huicai.base.system.service.SubjectService;
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
class SubjectControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private SubjectService subjectService;

    @Test
    @DisplayName("获取科目树_返回200和树形结构")
    void tree_returnsTree() throws Exception {
        SubjectTreeVO node = new SubjectTreeVO();
        node.setId(1L);
        node.setCode("1001");
        node.setName("现金");
        node.setLevel(1);
        node.setIsLeaf(true);
        node.setChildren(List.of());

        when(subjectService.getTree()).thenReturn(List.of(node));

        mvc.perform(get("/api/v1/subjects/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].code").value("1001"))
                .andExpect(jsonPath("$.data[0].name").value("现金"));
    }

    @Test
    @DisplayName("获取科目详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setCode("1001");
        subject.setName("现金");
        when(subjectService.getById(eq(1L))).thenReturn(subject);

        mvc.perform(get("/api/v1/subjects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("现金"));
    }

    @Test
    @DisplayName("获取科目详情_不存在时Service返回null_接口返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(subjectService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/subjects/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("新增科目_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        Subject created = new Subject();
        created.setId(1L);
        created.setCode("1001");
        created.setName("现金");

        when(subjectService.create(any())).thenReturn(created);

        String json = """
                {"code":"1001","name":"现金","direction":"debit"}
                """;

        mvc.perform(post("/api/v1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(subjectService).create(argThat(d -> "现金".equals(d.getName())));
    }

    @Test
    @DisplayName("新增科目_缺少必填字段_返回400")
    void create_missingRequiredField_returns400() throws Exception {
        String json = """
                {"name":"现金"}
                """;

        mvc.perform(post("/api/v1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}