/**
 * RestAssured 真实 HTTP 契约测试 (L3)
 *
 * 使用 @SpringBootTest(webEnvironment = RANDOM_PORT) + RestAssured
 * 发起真实 HTTP 请求，验证 API 契约：
 * - HTTP 状态码
 * - JSON 响应结构（code/data/msg）
 * - 路径正确性
 *
 * 注意：使用 @MockBean 模拟 Service 层，避免因 H2 内存数据库无表结构导致 500 错误。
 * 契约测试专注于 HTTP 层面验证，业务逻辑验证见 TaxApiContractTest（MockMvc 版本）。
 *
 * @模块: SME Tax Module
 */
package com.huicai.api.rest;

import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import com.huicai.sme.tax.service.TaxService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RestAssured 集成测试：通过真实 HTTP 端口调用 Spring Boot 应用，
 * 验证 API 契约。使用 @MockBean 模拟 Service 层。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "contract-test"})
class TaxRestContractTest {

    @LocalServerPort
    private int randomPort;

    @MockBean
    private TaxService taxService;

    @MockBean
    private OutputInvoiceStateMachineService stateMachineService;

    @MockBean
    private InputInvoiceStateMachineService inputStateMachineService;

    @BeforeEach
    void setUp() {
        RestAssured.port = randomPort;
        RestAssured.baseURI = "http://localhost";
    }

    // =========================================================
    // GET /api/sme/tax/v1/tax/types/list
    // =========================================================

    @Test
    @DisplayName("GET /types/list — 返回 200 + JSON 数组")
    void getTaxTypeList_success() {
        when(taxService.listAllTaxTypes()).thenReturn(Collections.emptyList());

        given()
            .contentType(ContentType.JSON)
            .when()
                .get("/api/sme/tax/v1/tax/types/list")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("code", equalTo(200))
                .body("data", is(notNullValue()));
    }

    // =========================================================
    // POST /api/sme/tax/v1/tax/output-invoices
    // =========================================================

    @Test
    @DisplayName("POST /output-invoices — 缺少必填字段返回 400（业务异常）")
    void createOutputInvoice_missingFields_returns400() {
        doThrow(new BusinessException(400, "缺少必填字段"))
                .when(taxService).createOutput(any());

        String payload = "{\n"
            + "  \"invoiceNo\": \"REST-ASSURED-TEST-INV\",\n"
            + "  \"amount\": 1000.00,\n"
            + "  \"taxRate\": 13\n"
            + "}";

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
                .post("/api/sme/tax/v1/tax/output-invoices")
            .then()
                .statusCode(200)
                .body("code", equalTo(400));
    }

    // =========================================================
    // POST /api/sme/tax/v1/tax/output-invoices/{id}/confirm
    // =========================================================

    @Test
    @DisplayName("POST /output-invoices/{id}/confirm — 状态非法返回 400")
    void confirmOutputInvoice_wrongState_returns400() {
        doThrow(new BusinessException(400, "仅待审核状态可确认"))
                .when(stateMachineService).confirm(eq(1L), anyLong());

        given()
            .contentType(ContentType.JSON)
            .when()
                .post("/api/sme/tax/v1/tax/output-invoices/1/confirm")
            .then()
                .statusCode(200)
                .body("code", equalTo(400));
    }

    @Test
    @DisplayName("POST /output-invoices/{id}/confirm — 成功返回 200")
    void confirmOutputInvoice_success() {
        doNothing().when(stateMachineService).confirm(eq(1L), anyLong());

        given()
            .contentType(ContentType.JSON)
            .when()
                .post("/api/sme/tax/v1/tax/output-invoices/1/confirm")
            .then()
                .statusCode(200)
                .body("code", equalTo(200));
    }
}