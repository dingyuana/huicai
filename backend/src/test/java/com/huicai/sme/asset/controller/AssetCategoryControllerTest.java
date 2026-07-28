package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.service.AssetCategoryService;
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
class AssetCategoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AssetCategoryService service;

    @Test
    @DisplayName("分页查询资产分类_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/asset/v1/asset-categories/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询全部列表_回调端点")
    void listAll_listEndpoint() throws Exception {
        when(service.listAll()).thenReturn(List.of());

        mvc.perform(get("/api/sme/asset/v1/asset-categories/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询分类详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        AssetCategoryEntity entity = new AssetCategoryEntity();
        entity.setId(1L);
        entity.setName("电子设备");
        when(service.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/asset/v1/asset-categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.categoryName").value("电子设备"));
    }

    @Test
    @DisplayName("新增资产分类_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        AssetCategoryEntity input = new AssetCategoryEntity();
        input.setName("办公设备");

        AssetCategoryEntity created = new AssetCategoryEntity();
        created.setId(1L);
        when(service.create(any(AssetCategoryEntity.class))).thenReturn(created);

        mvc.perform(post("/api/sme/asset/v1/asset-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新资产分类_RequestBody正确解析")
    void update_requestBody_parsedCorrectly() throws Exception {
        AssetCategoryEntity input = new AssetCategoryEntity();
        input.setId(1L);
        input.setName("更新分类");

        AssetCategoryEntity updated = new AssetCategoryEntity();
        updated.setId(1L);
        when(service.update(any(AssetCategoryEntity.class))).thenReturn(updated);

        mvc.perform(put("/api/sme/asset/v1/asset-categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("删除资产分类_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(eq(1L));

        mvc.perform(delete("/api/sme/asset/v1/asset-categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}