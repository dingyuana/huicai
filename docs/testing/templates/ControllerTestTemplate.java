package com.huicai.module.xxx.controller;

import com.huicai.module.xxx.service.XxxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class XxxControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private XxxService xxxService;

    @Test
    void list_shouldPassPageParamsCorrectly() throws Exception {
        mvc.perform(get("/api/xxx/list")
                .param("pageNum", "1")
                .param("pageSize", "20")
                .param("status", "CONFIRMED"))
                .andExpect(status().isOk());

        verify(xxxService).list(eq(1), eq(20), eq("CONFIRMED"));
    }

    @Test
    void getById_shouldPassIdCorrectly() throws Exception {
        when(xxxService.getById(1L)).thenReturn(null);

        mvc.perform(get("/api/xxx/{id}", 1L))
                .andExpect(status().isOk());

        verify(xxxService).getById(eq(1L));
    }

    @Test
    void create_shouldParseRequestBodyCorrectly() throws Exception {
        String requestBody = """
            {
                "code": "TEST-001",
                "name": "测试数据",
                "amount": 1000.00,
                "status": "PENDING_CONFIRM"
            }
            """;

        mvc.perform(post("/api/xxx/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void update_shouldPassIdAndBodyCorrectly() throws Exception {
        String requestBody = """
            {
                "name": "测试数据-已更新",
                "status": "CONFIRMED"
            }
            """;

        mvc.perform(put("/api/xxx/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void delete_shouldPassIdCorrectly() throws Exception {
        mvc.perform(delete("/api/xxx/{id}", 1L))
                .andExpect(status().isOk());

        verify(xxxService).delete(eq(1L));
    }

    @Test
    void list_shouldUseDefaultPageParams() throws Exception {
        mvc.perform(get("/api/xxx/list"))
                .andExpect(status().isOk());
    }

    @Test
    void confirm_shouldPassStatusCorrectly() throws Exception {
        mvc.perform(post("/api/xxx/{id}/confirm", 1L))
                .andExpect(status().isOk());
    }
}