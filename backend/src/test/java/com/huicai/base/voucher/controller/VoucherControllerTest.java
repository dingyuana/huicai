package com.huicai.base.voucher.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherQueryDTO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class VoucherControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private VoucherService voucherService;

    @BeforeEach
    void setUpSecurityContext() {
        com.huicai.base.system.entity.UserEntity user = new com.huicai.base.system.entity.UserEntity();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("test123");
        user.setEnterpriseId(1L);
        user.setUserType("ENTERPRISE");
        LoginUser loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @Test
    @DisplayName("分页查询凭证_RequestBody正确解析")
    void pageQuery_requestBody_parsedCorrectly() throws Exception {
        when(voucherService.pageQuery(any(VoucherQueryDTO.class))).thenReturn(new Page<>());

        VoucherQueryDTO dto = new VoucherQueryDTO();
        dto.setPeriod("202601");

        mvc.perform(post("/api/base/voucher/v1/vouchers/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询凭证详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        VoucherVO vo = new VoucherVO();
        vo.setId(1L);
        vo.setVoucherNo("PZ-2026-001");
        when(voucherService.getDetail(eq(1L))).thenReturn(vo);

        mvc.perform(get("/api/base/voucher/v1/vouchers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.voucherNo").value("PZ-2026-001"));
    }

    @Test
    @DisplayName("新增凭证_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202601");
        dto.setVoucherTypeId(1L);
        dto.setEntries(List.of(new VoucherCreateDTO.EntryDTO() {{
            setSubjectId(1001L);
            setDebit(BigDecimal.valueOf(1000));
            setCredit(BigDecimal.ZERO);
        }}));

        VoucherVO created = new VoucherVO();
        created.setId(1L);
        when(voucherService.create(any(VoucherCreateDTO.class), anyLong())).thenReturn(created);

        mvc.perform(post("/api/base/voucher/v1/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新凭证_RequestBody正确解析")
    void update_requestBody_parsedCorrectly() throws Exception {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod("202601");
        dto.setVoucherTypeId(1L);
        dto.setEntries(List.of(new VoucherCreateDTO.EntryDTO() {{
            setSubjectId(1001L);
            setDebit(BigDecimal.valueOf(1000));
            setCredit(BigDecimal.ZERO);
        }}));

        VoucherVO updated = new VoucherVO();
        updated.setId(1L);
        when(voucherService.update(any(VoucherCreateDTO.class), anyLong())).thenReturn(updated);

        mvc.perform(put("/api/base/voucher/v1/vouchers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("删除凭证_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(voucherService).delete(eq(1L));

        mvc.perform(delete("/api/base/voucher/v1/vouchers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("提交凭证_状态流转返回R.ok()")
    void submit_statusTransition() throws Exception {
        doNothing().when(voucherService).submit(eq(1L), anyLong());

        mvc.perform(post("/api/base/voucher/v1/vouchers/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("审核凭证_审核端点")
    void audit_auditEndpoint() throws Exception {
        doNothing().when(voucherService).audit(eq(1L), anyLong());

        mvc.perform(post("/api/base/voucher/v1/vouchers/1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("过账凭证_过账端点")
    void post_postEndpoint() throws Exception {
        doNothing().when(voucherService).post(eq(1L), anyLong());

        mvc.perform(post("/api/base/voucher/v1/vouchers/1/post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}