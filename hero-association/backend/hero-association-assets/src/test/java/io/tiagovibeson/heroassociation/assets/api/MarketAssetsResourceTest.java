package io.tiagovibeson.heroassociation.assets.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.tiagovibeson.heroassociation.assets.application.AssetsService;
import io.tiagovibeson.heroassociation.assets.domain.AssetWallet;
import io.tiagovibeson.heroassociation.assets.testsupport.CoreAuthorityStubResource;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

@QuarkusTest @TestSecurity(user="manager4")
class MarketAssetsResourceTest {
    static final String BASE="/internal/v1/assets",HEADER="X-Hero-Association-Market-Service-Key",KEY=CoreAuthorityStubResource.MARKET_KEY,MANAGER=CoreAuthorityStubResource.MANAGER,AGENCY=CoreAuthorityStubResource.AGENCY,ITEM="019c4c00-0070-7000-8000-000000000001";
    @Inject AssetsService assets;
    @Inject EntityManager em;
    List<UUID> reservations=new ArrayList<>();
    @AfterEach void release() {CoreAuthorityStubResource.unavailable=false;for(UUID key:reservations)assets.close(UuidV7.next(),key);}
    private UUID next(){UUID key=UuidV7.next();reservations.add(key);return key;}
    private String body(UUID key,String type,String owner){return "{\"reservationKey\":\"%s\",\"ownerType\":\"%s\",\"ownerId\":\"%s\",\"resourceType\":\"GOLD\",\"itemId\":\"%s\",\"quantity\":1,\"unitPriceGoldPerItem\":10}".formatted(key,type,owner,ITEM);}
    private long gold(String id){return QuarkusTransaction.requiringNew().call(()->em.find(AssetWallet.class,UUID.fromString(id)).gold);}
    @Test void personalReservationReplaysOneDebitAndRecordsTheAuthorizedRequester(){
        long before=gold(MANAGER);UUID key=next();for(int retry=0;retry<2;retry++)given().header(HEADER,KEY).contentType("application/json").body(body(key,"MANAGER",MANAGER)).post(BASE+"/reservations").then().statusCode(200).body("requesterManagerId",equalTo(MANAGER));assertEquals(before-10,gold(MANAGER));
        given().header(HEADER,KEY).get(BASE+"/reservations/"+key).then().statusCode(200).body("remainingQuantity",equalTo(1));
    }
    @Test void unauthorizedOwnersAndWrongCredentialDoNotReachTheMutation(){
        long before=gold(MANAGER);
        given().header(HEADER,KEY).contentType("application/json").body(body(next(),"MANAGER","019c4c00-0000-7000-8000-000000000001")).post(BASE+"/reservations").then().statusCode(403);
        given().header(HEADER,KEY).contentType("application/json").body(body(next(),"AGENCY",AGENCY)).post(BASE+"/reservations").then().statusCode(403);
        given().header(HEADER,CoreAuthorityStubResource.CORE_KEY).contentType("application/json").body(body(next(),"MANAGER",MANAGER)).post(BASE+"/reservations").then().statusCode(403);assertEquals(before,gold(MANAGER));
    }
    @Test @TestSecurity(user="leader") void agencyReservationUsesCoreLeadershipBeforeDebitingAssets(){
        long before=gold(AGENCY);given().header(HEADER,KEY).contentType("application/json").body(body(next(),"AGENCY",AGENCY)).post(BASE+"/reservations").then().statusCode(200).body("ownerId",equalTo(AGENCY));assertEquals(before-10,gold(AGENCY));
    }
    @Test void contextAddsAssetsCatalogWithoutChangingBalances(){
        long before=gold(MANAGER);given().header(HEADER,KEY).contentType("application/json").body("{\"ownerType\":\"MANAGER\",\"itemId\":\""+ITEM+"\"}").post(BASE+"/context").then().statusCode(200).body("managerId",equalTo(MANAGER)).body("item.code",equalTo("magic-crystal"));assertEquals(before,gold(MANAGER));
    }
    @Test void authorityOutageDoesNotCreateAPartialReservation(){
        long before=gold(MANAGER);UUID key=next();CoreAuthorityStubResource.unavailable=true;
        given().header(HEADER,KEY).contentType("application/json").body(body(key,"MANAGER",MANAGER)).post(BASE+"/reservations").then().statusCode(503);
        given().header(HEADER,KEY).get(BASE+"/reservations/"+key).then().statusCode(404);assertEquals(before,gold(MANAGER));
    }
    @Test void invalidKeysAndMissingCommandFieldsAreRejected(){
        given().header(HEADER,KEY).contentType("application/json").body(body(UUID.randomUUID(),"MANAGER",MANAGER)).post(BASE+"/reservations").then().statusCode(400);
        given().header(HEADER,KEY).contentType("application/json").body("{}").post(BASE+"/reservations").then().statusCode(400);
        given().header(HEADER,KEY).contentType("application/json").body("null").post(BASE+"/settlements").then().statusCode(400);
    }
}
