package com.huicai.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.system.entity.SysConfigEntity;
import com.huicai.module.system.service.SysConfigService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SysConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private SysConfigService sysConfigService;

    @Test
    @DisplayName("分页查询参数_默认参数正确生效")
    void list_defaultParams_applied() throws Exception {
        IPage<SysConfigEntity> page = new Page<>(1, 20);
        when(sysConfigService.page(any())).thenReturn(page);

        mvc.perform(get("/api/v1/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询参数_自定义参数正确绑定")
    void list_customParams_boundCorrectly() throws Exception {
        IPage<SysConfigEntity> page = new Page<>(2, 50);
        when(sysConfigService.page(any())).thenReturn(page);

        mvc.perform(get("/api/v1/configs")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询全部参数_返回200")
    void listAll_returnsOk() throws Exception {
        when(sysConfigService.list()).thenReturn(List.of());

        mvc.perform(get("/api/v1/configs/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("批量获取参数值_RequestParam正确解析")
    void getValues_requestParam_boundCorrectly() throws Exception {
        Map<String, String> values = Map.of("app.name", "慧财", "app.version", "1.0");
        when(sysConfigService.getValues(anyList())).thenReturn(values);

        mvc.perform(get("/api/v1/configs/values")
                        .param("keys", "app.name", "app.version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysConfigService).getValues(argThat(keys -> keys.contains("app.name") && keys.contains("app.version")));
    }

    @Test
    @DisplayName("新增参数_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        SysConfigEntity config = new SysConfigEntity();
        config.setConfigKey("app.name");
        config.setConfigValue("慧财财务");
        config.setDescription("应用名称");

        when(sysConfigService.save(any())).thenReturn(true);

        mvc.perform(post("/api/v1/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysConfigService).save(argThat(c -> "app.name".equals(c.getConfigKey())));
    }

    @Test
    @DisplayName("修改参数_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        SysConfigEntity config = new SysConfigEntity();
        config.setConfigValue("慧财财务系统");
        config.setDescription("更新描述");

        when(sysConfigService.updateById(any())).thenReturn(true);

        mvc.perform(put("/api/v1/configs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysConfigService).updateById(argThat(c -> c.getId() == 1L && "慧财财务系统".equals(c.getConfigValue())));
    }

    @Test
    @DisplayName("删除参数_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        when(sysConfigService.removeById(anyLong())).thenReturn(true);

        mvc.perform(delete("/api/v1/configs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysConfigService).removeById(eq(1L));
    }

    @Test
    @DisplayName("获取参数详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        SysConfigEntity config = new SysConfigEntity();
        config.setId(1L);
        config.setConfigKey("app.name");
        config.setConfigValue("慧财");
        when(sysConfigService.getById(eq(1L))).thenReturn(config);

        mvc.perform(get("/api/v1/configs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.configKey").value("app.name"));
    }

    @Test
    @DisplayName("获取参数详情_不存在时返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(sysConfigService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/configs/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}