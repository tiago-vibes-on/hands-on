package io.tiagovibeson.heroassociation.api.v1.market;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItems;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class MarketOrderControllerTest {

    private static final String DAWNWATCH_AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String IRONRIDGE_AGENCY_ID = "019c4c00-0001-7000-8000-000000000002";
    private static final String MAGIC_CRYSTAL_ID = "019c4c00-0070-7000-8000-000000000001";
    private static final String IRONRIDGE_BUY_ORDER_ID = "019c4c00-0090-7000-8000-000000000001";
    private static final String IRONRIDGE_SELL_ORDER_ID = "019c4c00-0090-7000-8000-000000000002";

    @Test
    void shouldMatchASellOrderAndDeductTheFeeFromSellerProceeds() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "side": "SELL",
                          "itemId": "%s",
                          "quantity": 1,
                          "priceGoldPerItem": 100
                        }
                        """.formatted(MAGIC_CRYSTAL_ID))
                .when().post("/api/v1/agencies/%s/market-orders".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(200)
                .body("agency.gold", is(2570))
                .body("itemInventory.find { it.item.code == 'magic-crystal' }.quantity", is(2));

        given()
                .when().get("/api/v1/market/orders")
                .then()
                .statusCode(200)
                .body("id", hasItems(IRONRIDGE_BUY_ORDER_ID))
                .body("find { it.id == '%s' }.quantityRemaining".formatted(IRONRIDGE_BUY_ORDER_ID), is(1));
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000002")
    void shouldReturnReservedItemsWhenCancellingAnOpenSellOrder() {
        given()
                .when().delete("/api/v1/agencies/%s/market-orders/%s".formatted(IRONRIDGE_AGENCY_ID, IRONRIDGE_SELL_ORDER_ID))
                .then()
                .statusCode(200)
                .body("itemInventory.find { it.item.code == 'iron-ingot' }.quantity", is(60));
    }
}
