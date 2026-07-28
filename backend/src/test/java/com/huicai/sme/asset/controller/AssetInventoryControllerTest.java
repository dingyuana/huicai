package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import com.huicai.sme.asset.service.AssetInventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AssetInventoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AssetInventoryService service;

    @Test
    @DisplayName("分页查询资产盘点_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/asset/v1/asset-inventories/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询盘点详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        AssetInventoryEntity entity = new AssetInventoryEntity();
        entity.setId(1L);
        when(service.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/asset/v1/asset-inventories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("新增资产盘点_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        AssetInventoryEntity entity = new AssetInventoryEntity();
        AssetInventoryEntity created = new AssetInventoryEntity();
        created.setId(1L);
        when(service.create(any(AssetInventoryEntity.class), anyList())).thenReturn(created);

        // CreateRequest has public fields; Jackson serializes them directly
        mvc.perform(post("/api/sme/asset/v1/asset-inventories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                java.util.Map.of("inventory", entity, "entries", java.util.List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("完成盘点_完成端点")
    void complete_completeEndpoint() throws Exception {
        AssetInventoryEntity entity = new AssetInventoryEntity();
        entity.setId(1L);
        entity.setStatus("COMPLETED");
        when(service.complete(eq(1L), anyList())).thenReturn(entity);

        mvc.perform(post("/api/sme/asset/v1/asset-inventories/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("删除资产盘点_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(eq(1L));

        mvc.perform(delete("/api/sme/asset/v1/asset-inventories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}