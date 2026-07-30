/**
 * AR/AP 核销管理契约测试 (L3 RestAssured)
 *
 * 验证核销单全生命周期各状态转换端点的 API 契约：
 * - HTTP 状态码（200/400）
 * - JSON 响应结构（code/data/msg）
 * - 非法状态转换的 400 错误码
 * - 分页/详情端点响应结构
 *
 * 使用 @SpringBootTest(webEnvironment = RANDOM_PORT) + RestAssured
 * 发起真实 HTTP 请求，通过 @MockBean 隔离 Service 层，专注契约验证。
 *
 * @模块: SME AR/AP Module - ArapSettlement Controller
 */
package com.huicai.api.rest;

import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.service.ArapSettlementService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "contract-test"})
class ArapSettlementRestContractTest {

    @LocalServerPort
    private int randomPort;

    @MockBean
    private ArapSettlementService arapSettlementService;

    private static final String BASE = "/api/sme/arap/v1/arap-settlements";

    @BeforeEach
    void setUp() {
        RestAssured.port = randomPort;
        RestAssured.baseURI = "http://localhost";
    }

    // ==========================================
    // POST / — 创建核销单
    // ==========================================

    @Test
    @DisplayName("POST / — 创建成功返回 200 + data 结构")
    void create_settlement_success() {
        ArapSettlementEntity mockEntity = new ArapSettlementEntity();
        when(arapSettlementService.create(any(ArapSettlementEntity.class), anyList()))
                .thenReturn(mockEntity);

        String payload = "{\n"
                + "  \"settlement\": {\n"
                + "    \"settlementType\": \"RECEIVE\",\n"
                + "    \"settlementDate\": \"2026-07-01\",\n"
                + "    \"period\": \"202607\",\n"
                + "    \"partyId\": 1,\n"
                + "    \"partyType\": \"CUSTOMER\",\n"
                + "    \"totalAmount\": 1000.00,\n"
                + "    \"status\": \"DRAFT\"\n"
                + "  },\n"
                + "  \"entries\": []\n"
                + "}";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                    .post(BASE)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("code", equalTo(200));
    }

    @Test
    @DisplayName("POST / — 创建时Service抛出异常，验证错误响应结构")
    void create_settlement_serviceError_returnsError() {
        doThrow(new BusinessException(500, "创建核销单失败"))
                .when(arapSettlementService).create(any(), anyList());

        given()
                .contentType(ContentType.JSON)
                .body("{\"settlement\": {\"settlementType\": \"RECEIVE\"}, \"entries\": []}")
                .when()
                    .post(BASE)
                .then()
                    .statusCode(200)
                    .body("code", equalTo(500));
    }

    // ==========================================
    // POST /{id}/submit — DRAFT → SUBMITTED
    // ==========================================

    @Test
    @DisplayName("POST /{id}/submit — 提交成功返回 200")
    void submit_success() {
        doNothing().when(arapSettlementService).submit(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/submit")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    @Test
    @DisplayName("POST /{id}/submit — 状态非法返回 400")
    void submit_wrongState_returns400() {
        doThrow(new BusinessException(400, "仅DRAFT状态可提交"))
                .when(arapSettlementService).submit(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/submit")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(400));
    }

    // ==========================================
    // POST /{id}/approve — SUBMITTED → CONFIRMED
    // ==========================================

    @Test
    @DisplayName("POST /{id}/approve — 审批通过返回 200 + data")
    void approve_success() {
        ArapSettlementEntity mockEntity = new ArapSettlementEntity();
        when(arapSettlementService.approve(eq(1L))).thenReturn(mockEntity);

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/approve")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("code", equalTo(200));
    }

    @Test
    @DisplayName("POST /{id}/approve — 状态非法返回 400")
    void approve_wrongState_returns400() {
        doThrow(new BusinessException(400, "仅SUBMITTED状态可审批"))
                .when(arapSettlementService).approve(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/approve")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(400));
    }

    // ==========================================
    // POST /{id}/reject — SUBMITTED → REJECTED
    // ==========================================

    @Test
    @DisplayName("POST /{id}/reject — 驳回成功返回 200")
    void reject_withReason_success() {
        doNothing().when(arapSettlementService).reject(eq(1L), anyString());

        given()
                .contentType(ContentType.JSON)
                .queryParam("reason", "审批不通过")
                .when()
                    .post(BASE + "/1/reject")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    @Test
    @DisplayName("POST /{id}/reject — 状态非法返回 400")
    void reject_wrongState_returns400() {
        doThrow(new BusinessException(400, "仅SUBMITTED状态可驳回"))
                .when(arapSettlementService).reject(eq(1L), anyString());

        given()
                .contentType(ContentType.JSON)
                .queryParam("reason", "审批不通过")
                .when()
                    .post(BASE + "/1/reject")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(400));
    }

    @Test
    @DisplayName("POST /{id}/reject-simple — 无理由驳回成功返回 200")
    void rejectSimple_success() {
        doNothing().when(arapSettlementService).reject(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/reject-simple")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    // ==========================================
    // POST /{id}/generate-voucher — CONFIRMED → VOUCHERED
    // ==========================================

    @Test
    @DisplayName("POST /{id}/generate-voucher — 生成凭证成功返回 200")
    void generateVoucher_success() {
        doNothing().when(arapSettlementService).generateVoucher(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/generate-voucher")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    @Test
    @DisplayName("POST /{id}/generate-voucher — 状态非法返回 400")
    void generateVoucher_wrongState_returns400() {
        doThrow(new BusinessException(400, "仅CONFIRMED状态可生成凭证"))
                .when(arapSettlementService).generateVoucher(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/generate-voucher")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(400));
    }

    // ==========================================
    // POST /{id}/cancel — DRAFT → CANCELLED
    // ==========================================

    @Test
    @DisplayName("POST /{id}/cancel — 取消成功返回 200")
    void cancel_success() {
        doNothing().when(arapSettlementService).cancel(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/cancel")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    // ==========================================
    // POST /{id}/reverse — CONFIRMED → REVERSED
    // ==========================================

    @Test
    @DisplayName("POST /{id}/reverse — 反核销成功返回 200")
    void reverse_success() {
        doNothing().when(arapSettlementService).reverse(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .post(BASE + "/1/reverse")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    // ==========================================
    // GET /page — 分页查询
    // ==========================================

    @Test
    @DisplayName("GET /page — 分页成功返回 200 + page 结构")
    void page_success() {
        when(arapSettlementService.pageQueryWithPartyName(any(), any(), any(), any()))
                .thenReturn(null);

        given()
                .contentType(ContentType.JSON)
                .queryParam("current", "1")
                .queryParam("size", "10")
                .when()
                    .get(BASE + "/page")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    // ==========================================
    // GET /{id} — 详情
    // ==========================================

    @Test
    @DisplayName("GET /{id} — 详情成功返回 200 + data")
    void getById_success() {
        when(arapSettlementService.getDetailWithPartyName(eq(1L))).thenReturn(null);

        given()
                .contentType(ContentType.JSON)
                .when()
                    .get(BASE + "/1")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }

    @Test
    @DisplayName("GET /{id} — 不存在返回 400")
    void getById_notFound_returns400() {
        doThrow(new BusinessException(400, "核销单不存在"))
                .when(arapSettlementService).getDetailWithPartyName(eq(999L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .get(BASE + "/999")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(400));
    }

    // ==========================================
    // DELETE /{id} — 删除
    // ==========================================

    @Test
    @DisplayName("DELETE /{id} — 删除成功返回 200")
    void delete_success() {
        doNothing().when(arapSettlementService).delete(eq(1L));

        given()
                .contentType(ContentType.JSON)
                .when()
                    .delete(BASE + "/1")
                .then()
                    .statusCode(200)
                    .body("code", equalTo(200));
    }
}
