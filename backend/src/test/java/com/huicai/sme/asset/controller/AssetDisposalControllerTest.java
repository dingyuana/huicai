package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.service.AssetDisposalService;
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
class AssetDisposalControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AssetDisposalService service;

    @Test
    @DisplayName("分页查询资产处置_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/asset/v1/asset-disposals/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询处置详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        AssetDisposalEntity entity = new AssetDisposalEntity();
        entity.setId(1L);
        when(service.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/asset/v1/asset-disposals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("新增资产处置_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        AssetDisposalEntity input = new AssetDisposalEntity();
        AssetDisposalEntity created = new AssetDisposalEntity();
        created.setId(1L);
        when(service.create(any(AssetDisposalEntity.class))).thenReturn(created);

        mvc.perform(post("/api/sme/asset/v1/asset-disposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("审批资产处置_审批端点")
    void approve_approveEndpoint() throws Exception {
        AssetDisposalEntity entity = new AssetDisposalEntity();
        entity.setId(1L);
        entity.setStatus("APPROVED");
        when(service.approve(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/asset/v1/asset-disposals/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("删除资产处置_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(eq(1L));

        mvc.perform(delete("/api/sme/asset/v1/asset-disposals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}