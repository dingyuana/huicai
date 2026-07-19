package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.service.AssetCardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AssetCardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AssetCardService service;

    @Test
    @DisplayName("分页查询资产卡片_默认参数")
    void page_defaultParams() throws Exception {
        IPage<AssetCardEntity> page = new Page<>(1, 20);
        when(service.pageQuery(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/asset-cards/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询资产卡片_自定义参数")
    void page_customParams() throws Exception {
        IPage<AssetCardEntity> page = new Page<>(2, 10);
        when(service.pageQuery(eq("电脑"), eq("IN_USE"), eq(1L), eq(2), eq(10))).thenReturn(page);

        mvc.perform(get("/api/v1/asset-cards/page")
                        .param("keyword", "电脑")
                        .param("status", "IN_USE")
                        .param("categoryId", "1")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取资产卡片详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        AssetCardEntity card = new AssetCardEntity();
        card.setId(1L);
        card.setAssetName("服务器");
        card.setAssetCode("ZC-001");
        card.setOriginalValue(new BigDecimal("50000.00"));
        when(service.getById(eq(1L))).thenReturn(card);

        mvc.perform(get("/api/v1/asset-cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.assetName").value("服务器"))
                .andExpect(jsonPath("$.data.assetCode").value("ZC-001"));
    }

    @Test
    @DisplayName("获取资产卡片详情_不存在时返回null")
    void getById_notExists_returnsNull() throws Exception {
        when(service.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/asset-cards/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("创建资产卡片_成功返回200")
    void create_success() throws Exception {
        AssetCardEntity input = new AssetCardEntity();
        input.setAssetName("新服务器");
        input.setAssetCode("ZC-002");
        input.setOriginalValue(new BigDecimal("80000.00"));
        input.setCategoryId(1L);
        input.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        input.setUsefulLife(5);
        input.setDepreciationMethod("直线法");

        AssetCardEntity saved = new AssetCardEntity();
        saved.setId(2L);
        saved.setAssetName("新服务器");
        saved.setAssetCode("ZC-002");
        when(service.create(any(AssetCardEntity.class))).thenReturn(saved);

        mvc.perform(post("/api/v1/asset-cards")
                        .contentType("application/json")
                        .content(om.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.assetName").value("新服务器"));
    }

    @Test
    @DisplayName("删除资产卡片_成功返回200")
    void delete_success() throws Exception {
        doNothing().when(service).delete(eq(1L));

        mvc.perform(delete("/api/v1/asset-cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).delete(eq(1L));
    }
}