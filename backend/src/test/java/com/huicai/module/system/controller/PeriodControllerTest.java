package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.PeriodService;
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
class PeriodControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private PeriodService periodService;

    @Test
    @DisplayName("分页查询期间_默认参数正确生效")
    void list_defaultParams_applied() throws Exception {
        IPage<PeriodEntity> page = new Page<>(1, 20);
        when(periodService.page(any())).thenReturn(page);

        mvc.perform(get("/api/v1/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询期间_自定义参数正确绑定")
    void list_customParams_boundCorrectly() throws Exception {
        IPage<PeriodEntity> page = new Page<>(2, 10);
        when(periodService.page(any())).thenReturn(page);

        mvc.perform(get("/api/v1/periods")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("查询全部期间_返回200")
    void listAll_returnsOk() throws Exception {
        when(periodService.list()).thenReturn(List.of());

        mvc.perform(get("/api/v1/periods/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询全部期间_list路径_返回200")
    void listAllNamed_returnsOk() throws Exception {
        when(periodService.list()).thenReturn(List.of());

        mvc.perform(get("/api/v1/periods/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取期间详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        PeriodEntity period = new PeriodEntity();
        period.setId(1L);
        period.setPeriodCode("202401");
        when(periodService.getById(eq(1L))).thenReturn(period);

        mvc.perform(get("/api/v1/periods/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.periodCode").value("202401"));
    }

    @Test
    @DisplayName("获取期间详情_不存在时返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(periodService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/periods/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("新增期间_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        PeriodEntity period = new PeriodEntity();
        period.setYear(2024);
        period.setMonth(1);
        period.setPeriodCode("202401");

        when(periodService.save(any())).thenReturn(true);

        mvc.perform(post("/api/v1/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(period)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).save(argThat(p -> "202401".equals(p.getPeriodCode())));
    }

    @Test
    @DisplayName("修改期间_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        PeriodEntity period = new PeriodEntity();
        period.setYear(2024);
        period.setMonth(2);
        period.setPeriodCode("202402");

        when(periodService.updateById(any())).thenReturn(true);

        mvc.perform(put("/api/v1/periods/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(period)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).updateById(argThat(p -> p.getId() == 1L && "202402".equals(p.getPeriodCode())));
    }

    @Test
    @DisplayName("删除期间_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        when(periodService.removeById(anyLong())).thenReturn(true);

        mvc.perform(delete("/api/v1/periods/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).removeById(eq(1L));
    }

    @Test
    @DisplayName("启用期间_PathVariable正确解析_返回200")
    void openPeriod_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(periodService).openPeriod(anyLong());

        mvc.perform(post("/api/v1/periods/1/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).openPeriod(eq(1L));
    }

    @Test
    @DisplayName("关闭期间_PathVariable正确解析_返回200")
    void closePeriod_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(periodService).closePeriod(anyLong());

        mvc.perform(post("/api/v1/periods/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).closePeriod(eq(1L));
    }

    @Test
    @DisplayName("锁定期间_PathVariable正确解析_返回200")
    void lockPeriod_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(periodService).lockPeriod(anyLong());

        mvc.perform(post("/api/v1/periods/1/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).lockPeriod(eq(1L));
    }

    @Test
    @DisplayName("解锁期间_PathVariable正确解析_返回200")
    void unlockPeriod_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(periodService).unlockPeriod(anyLong());

        mvc.perform(post("/api/v1/periods/1/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(periodService).unlockPeriod(eq(1L));
    }
}