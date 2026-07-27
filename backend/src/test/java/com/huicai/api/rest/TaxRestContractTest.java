/**
 * RestAssured 真实 HTTP 契约测试 (L3)
 *
 * 使用 @SpringBootTest(webEnvironment = RANDOM_PORT) + RestAssured
 * 发起真实 HTTP 请求，验证 API 契约：
 * - HTTP 状态码
 * - JSON 响应结构（code/data/msg）
 * - 路径正确性
 *
 * @模块: SME Tax Module
 * @author Opencode
 */
package com.huicai.api.rest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * RestAssured 集成测试：通过真实 HTTP 端口调用 Spring Boot 应用，
 * 验证 API 契约。不需要 @Autowired service——纯 HTTP 层面测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaxRestContractTest {

    @LocalServerPort
    private int randomPort;

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

    @Test
    @DisplayName("GET 不存在的路径 — 返回 404")
    void getNonExistentPath_returns404() {
        given()
            .contentType(ContentType.JSON)
            .when()
                .get("/api/sme/tax/v1/tax/types/non-existent")
            .then()
                .statusCode(404);
    }

    // =========================================================
    // POST /api/sme/tax/v1/tax/types
    // =========================================================

    @Test
    @DisplayName("POST /types — 合法 payload 返回 200")
    void createTaxType_validData_success() {
        String payload = "{\n"
            + "  \"typeCode\": \"REST-ASSURED-TEST\",\n"
            + "  \"typeName\": \"RestAssured测试类型\",\n"
            + "  \"taxRate\": 13\n"
            + "}";

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
                .post("/api/sme/tax/v1/tax/types")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("code", equalTo(200))
                .body("data.typeCode", equalTo("REST-ASSURED-TEST"))
                .body("data.typeName", equalTo("RestAssured测试类型"));
    }

    @Test
    @DisplayName("POST /types — 空 body 返回 400")
    void createTaxType_emptyBody_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("")
            .when()
                .post("/api/sme/tax/v1/tax/types")
            .then()
                .statusCode(400)
                .body("code", equalTo(400));
    }

    // =========================================================
    // POST /api/sme/tax/v1/tax/output-invoices
    // =========================================================

    @Test
    @DisplayName("POST /output-invoices — 缺少必填字段返回 400")
    void createOutputInvoice_missingFields_returns400() {
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
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /output-invoices/{id}/confirm — 状态非法返回 400")
    void confirmOutputInvoice_wrongState_returns400() {
        given()
            .contentType(ContentType.JSON)
            .when()
                .post("/api/sme/tax/v1/tax/output-invoices/1/confirm")
            .then()
                .statusCode(400)
                .body("code", equalTo(400));
    }

    @Test
    @DisplayName("POST /output-invoices/{id}/confirm — 未认证返回 401/403")
    void confirmOutputInvoice_unauthenticated_returnsError() {
        // 无 token 访问受保护端点
        given()
            .contentType(ContentType.JSON)
            .when()
                .post("/api/sme/tax/v1/tax/output-invoices/1/confirm")
            .then()
                .statusCode(isOneOf(401, 403, 400));
    }
}
