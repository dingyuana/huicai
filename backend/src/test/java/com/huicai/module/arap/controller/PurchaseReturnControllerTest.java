package com.huicai.sme.arap.controller;

import com.huicai.sme.arap.service.PurchaseReturnService;
import com.huicai.sme.arap.service.PurchaseReturnService.PurchaseReturnRequest;
import com.huicai.sme.arap.service.PurchaseReturnService.PurchaseReturnVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PurchaseReturnControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PurchaseReturnService service;

    @Test
    @DisplayName("创建采购退货_返回正确的VO")
    void createReturn_returnsVO() throws Exception {
        var vo = new PurchaseReturnVO(
            1L, "TH20261201000001", 1L, "供应商A",
            "PO2026120001", BigDecimal.valueOf(10000),
            BigDecimal.valueOf(1300), "质量不合格", "VOUCHERED",
            10L, "ZZ-202612-001"
        );
        when(service.createReturn(any(PurchaseReturnRequest.class))).thenReturn(vo);

        String json = """
                {
                    "originalDocNo": "PO2026120001",
                    "vendorId": 1,
                    "returnAmount": 10000.00,
                    "taxAmount": 1300.00,
                    "reason": "质量不合格"
                }
                """;

        mvc.perform(post("/api/sme/arap/v1/purchase-returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.voucherNo").value("ZZ-202612-001"))
                .andExpect(jsonPath("$.data.status").value("VOUCHERED"));
    }

    @Test
    @DisplayName("查询采购退货详情_返回正确的VO")
    void getById_returnsVO() throws Exception {
        var vo = new PurchaseReturnVO(
            1L, "TH20261201000001", 1L, "供应商A",
            "PO2026120001", BigDecimal.valueOf(10000),
            BigDecimal.valueOf(1300), "质量不合格", "VOUCHERED",
            10L, "ZZ-202612-001"
        );
        when(service.getById(1L)).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/purchase-returns/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.returnAmount").value(10000.00));
    }
}