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

/**
 * XxxController 参数绑定测试模板
 * 
 * 说明：
 * 1. 重点验证 Controller 参数绑定正确性，不验证业务逻辑
 * 2. @MockBean Service 层，专注于 HTTP → Controller 链路验证
 * 3. addFilters = false 跳过权限验证，专注于参数绑定
 * 4. 可以发现的问题：
 *    - @RequestParam 参数名不匹配
 *    - @PathVariable 类型转换错误
 *    - @RequestBody JSON 反序列化错误
 *    - GET/POST/PUT/DELETE 方法不匹配
 *    - 参数默认值不正确
 * 
 * 使用方法：
 * 1. 替换 Xxx 为实际业务名
 * 2. 替换路径和参数为实际接口
 * 3. 每个 HTTP 方法至少一个测试用例
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class XxxControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private XxxService xxxService;

    /**
     * 场景 1：GET 查询 - @RequestParam 参数验证
     * 验证：分页参数、状态过滤参数正确传递
     */
    @Test
    void list_shouldPassPageParamsCorrectly() throws Exception {
        mvc.perform(get("/api/xxx/list")
                .param("pageNum", "1")
                .param("pageSize", "20")
                .param("status", "CONFIRMED"))
                .andExpect(status().isOk());

        // 验证参数正确传递到 Service
        verify(xxxService).list(eq(1), eq(20), eq("CONFIRMED"));
    }

    /**
     * 场景 2：GET 查询 - @PathVariable 参数验证
     * 验证：ID 参数正确解析和传递
     */
    @Test
    void getById_shouldPassIdCorrectly() throws Exception {
        // Mock Service 返回值
        when(xxxService.getById(1L)).thenReturn(null);

        mvc.perform(get("/api/xxx/{id}", 1L))
                .andExpect(status().isOk());

        // 验证参数正确传递到 Service
        verify(xxxService).getById(eq(1L));
    }

    /**
     * 场景 3：POST 创建 - @RequestBody JSON 反序列化验证
     * 验证：JSON 正确反序列化为 Java 对象
     */
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

        // 验证参数正确传递到 Service（需要根据实际方法签名调整）
        // verify(xxxService).create(any(XxxDTO.class));
    }

    /**
     * 场景 4：PUT 更新 - @RequestBody + @PathVariable 组合验证
     * 验证：路径参数和请求体都正确传递
     */
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

        // 验证参数正确传递到 Service
        // verify(xxxService).update(eq(1L), any(XxxDTO.class));
    }

    /**
     * 场景 5：DELETE 删除 - @PathVariable 参数验证
     * 验证：ID 参数正确解析和传递
     */
    @Test
    void delete_shouldPassIdCorrectly() throws Exception {
        mvc.perform(delete("/api/xxx/{id}", 1L))
                .andExpect(status().isOk());

        // 验证参数正确传递到 Service
        verify(xxxService).delete(eq(1L));
    }

    /**
     * 场景 6：可选参数默认值验证
     * 验证：不传参数时使用默认值
     */
    @Test
    void list_shouldUseDefaultPageParams() throws Exception {
        mvc.perform(get("/api/xxx/list"))  // 不传分页参数
                .andExpect(status().isOk());

        // 验证使用默认值（需要根据实际默认值调整）
        // verify(xxxService).list(eq(1), eq(10), isNull());
    }

    /**
     * 场景 7：状态转换接口验证
     * 验证：状态流转接口参数正确传递
     */
    @Test
    void confirm_shouldPassStatusCorrectly() throws Exception {
        mvc.perform(post("/api/xxx/{id}/confirm", 1L))
                .andExpect(status().isOk());

        // 验证状态变更方法被调用
        // verify(xxxService).confirm(eq(1L));
    }
}
