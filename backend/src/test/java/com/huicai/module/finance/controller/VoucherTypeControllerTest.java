package com.huicai.base.voucher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;
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
class VoucherTypeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VoucherTypeService voucherTypeService;

    @Test
    @DisplayName("分页查询凭证类型_默认参数_返回200")
    void page_defaultParams_returnsOk() throws Exception {
        Page<VoucherTypeEntity> page = new Page<>(1, 20);
        VoucherTypeEntity vt = new VoucherTypeEntity();
        vt.setId(1L);
        vt.setCode("JZ");
        vt.setName("记账凭证");
        page.setRecords(List.of(vt));
        page.setTotal(1);

        when(voucherTypeService.page(any(Page.class))).thenReturn(page);

        mvc.perform(get("/api/v1/voucher-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("记账凭证"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(voucherTypeService).page(any(Page.class));
    }

    @Test
    @DisplayName("获取全量凭证类型列表_返回200")
    void listAll_returnsAll() throws Exception {
        VoucherTypeEntity vt = new VoucherTypeEntity();
        vt.setId(1L);
        vt.setCode("SK");
        vt.setName("收款凭证");

        when(voucherTypeService.list()).thenReturn(List.of(vt));

        mvc.perform(get("/api/v1/voucher-types/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("收款凭证"));
    }

    @Test
    @DisplayName("新增凭证类型_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        VoucherTypeEntity vt = new VoucherTypeEntity();
        vt.setId(1L);
        vt.setCode("ZZ");
        vt.setName("转账凭证");

        when(voucherTypeService.save(any())).thenReturn(true);

        String json = """
                {"code":"ZZ","name":"转账凭证"}
                """;

        mvc.perform(post("/api/v1/voucher-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(voucherTypeService).save(argThat(e -> "ZZ".equals(e.getCode())));
    }

    @Test
    @DisplayName("修改凭证类型_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        when(voucherTypeService.updateById(any())).thenReturn(true);

        String json = """
                {"name":"付款凭证"}
                """;

        mvc.perform(put("/api/v1/voucher-types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(voucherTypeService).updateById(argThat(e -> e.getId() == 1L));
    }

    @Test
    @DisplayName("删除凭证类型_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        when(voucherTypeService.removeById(anyLong())).thenReturn(true);

        mvc.perform(delete("/api/v1/voucher-types/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(voucherTypeService).removeById(eq(1L));
    }

    @Test
    @DisplayName("获取凭证类型详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        VoucherTypeEntity vt = new VoucherTypeEntity();
        vt.setId(1L);
        vt.setCode("JZ");
        vt.setName("记账凭证");

        when(voucherTypeService.getById(eq(1L))).thenReturn(vt);

        mvc.perform(get("/api/v1/voucher-types/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("记账凭证"));
    }
}