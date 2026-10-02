package io.tiagovibeson.heroassociation.assets.application;

import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.tiagovibeson.heroassociation.assets.domain.*;
import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import io.tiagovibeson.heroassociation.assets.testsupport.CoreAuthorityStubResource;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.*;

@QuarkusTest @QuarkusTestResource(CoreAuthorityStubResource.class)
class GoldTransfersTest {
    static final UUID MANAGER=UUID.fromString(CoreAuthorityStubResource.MANAGER), AGENCY=UUID.fromString(CoreAuthorityStubResource.AGENCY);
    @Inject GoldTransfers transfers;
    @Inject EntityManager em;
    long managerGold,agencyGold;
    @BeforeEach @Transactional void capture() { CoreAuthorityStubResource.unavailable=false;managerGold=em.find(AssetWallet.class,MANAGER).gold;agencyGold=em.find(AssetWallet.class,AGENCY).gold; }
    @AfterEach @Transactional void restore() { CoreAuthorityStubResource.unavailable=false;em.find(AssetWallet.class,MANAGER).gold=managerGold;em.find(AssetWallet.class,AGENCY).gold=agencyGold; }
    private GoldTransfers.TransferRequest deposit(UUID key, BigDecimal amount) { return new GoldTransfers.TransferRequest(key,GoldTransferDirection.MANAGER_TO_AGENCY," Dawnwatch Agency ",null,amount); }
    private long gold(UUID id) { return QuarkusTransaction.requiringNew().call(()->em.find(AssetWallet.class,id).gold); }
    @Test void exactRetryAndNameNormalizationSurviveCoreOutageWithoutASecondDebit() {
        UUID key=UuidV7.next();var first=transfers.transfer("manager4","player-token",deposit(key,BigDecimal.valueOf(20)));
        CoreAuthorityStubResource.unavailable=true;
        assertEquals(first,transfers.transfer("manager4","player-token",new GoldTransfers.TransferRequest(key,GoldTransferDirection.MANAGER_TO_AGENCY,"dawnwatch agency",null,new BigDecimal("20.0"))));
        assertEquals(managerGold-20,gold(MANAGER));assertEquals(agencyGold+20,gold(AGENCY));
    }
    @Test void anotherSubjectOrDifferentPayloadCannotReplayTheKey() {
        UUID key=UuidV7.next();transfers.transfer("manager4","player-token",deposit(key,BigDecimal.TEN));
        assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("another","player-token",deposit(key,BigDecimal.TEN)));
        assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("manager4","player-token",deposit(key,BigDecimal.ONE)));
        assertEquals(managerGold-10,gold(MANAGER));
    }
    @Test void withdrawalRequiresCoreLeadershipAuthorization() {
        var request=new GoldTransfers.TransferRequest(UuidV7.next(),GoldTransferDirection.AGENCY_TO_MANAGER,"Dawnwatch Agency","Manager 4",BigDecimal.TEN);
        assertEquals(403,assertThrows(WebApplicationException.class,()->transfers.transfer("manager4","player-token",request)).getResponse().getStatus());
        transfers.transfer("leader","leader-token",request);assertEquals(managerGold+10,gold(MANAGER));assertEquals(agencyGold-10,gold(AGENCY));
    }
    @Test void unavailableAuthorityCreatesNoReceiptOrPosting() {
        UUID key=UuidV7.next();CoreAuthorityStubResource.unavailable=true;
        assertEquals(503,assertThrows(WebApplicationException.class,()->transfers.transfer("manager4","player-token",deposit(key,BigDecimal.TEN))).getResponse().getStatus());
        QuarkusTransaction.requiringNew().run(()->assertNull(em.find(AssetCommandReceipt.class,key)));assertEquals(managerGold,gold(MANAGER));
    }
    @Test void overspendingAndRecipientOverflowDoNotPartiallyDebit() {
        assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("manager4","player-token",deposit(UuidV7.next(),BigDecimal.valueOf(managerGold+1))));
        QuarkusTransaction.requiringNew().run(()->em.find(AssetWallet.class,AGENCY).gold=Long.MAX_VALUE);
        assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("manager4","player-token",deposit(UuidV7.next(),BigDecimal.ONE)));
        assertEquals(managerGold,gold(MANAGER));assertEquals(Long.MAX_VALUE,gold(AGENCY));
    }
    @Test void concurrentExactRetriesCommitOneTransfer() throws Exception {
        UUID key=UuidV7.next();CountDownLatch start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<GoldTransfers.TransferResponse> call=()->{start.await();return transfers.transfer("manager4","player-token",deposit(key,BigDecimal.TEN));};
            var a=workers.submit(call);var b=workers.submit(call);start.countDown();assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
        assertEquals(managerGold-10,gold(MANAGER));assertEquals(agencyGold+10,gold(AGENCY));
    }
    @Test void concurrentDifferentTransfersCannotOverspendTheWallet() throws Exception {
        CountDownLatch start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call=()->{start.await();try{transfers.transfer("manager4","player-token",deposit(UuidV7.next(),BigDecimal.valueOf(150)));return true;}catch(AssetOperationRejectedException expected){return false;}};
            var a=workers.submit(call);var b=workers.submit(call);start.countDown();assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));
        }
        assertEquals(managerGold-150,gold(MANAGER));
    }
    @Test void invalidAmountsKeysAndDepositRecipientsFailBeforeAuthorityCalls() {
        int calls=CoreAuthorityStubResource.calls;
        for(BigDecimal amount:List.of(BigDecimal.ZERO,BigDecimal.valueOf(-1),new BigDecimal("0.5"),new BigDecimal("9223372036854775808"))) assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("manager4","player-token",deposit(UuidV7.next(),amount)));
        assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("manager4","player-token",deposit(UUID.randomUUID(),BigDecimal.ONE)));
        assertThrows(AssetOperationRejectedException.class,()->transfers.transfer("manager4","player-token",new GoldTransfers.TransferRequest(UuidV7.next(),GoldTransferDirection.MANAGER_TO_AGENCY,"Dawnwatch Agency","Manager 4",BigDecimal.ONE)));
        assertEquals(calls,CoreAuthorityStubResource.calls);
    }
    @Test @TestSecurity(user="manager4") void privateCredentialsAreIsolatedFromPublicPlayerAccess() {
        String body="{\"owners\":[],\"heroes\":[]}";
        given().contentType("application/json").body(body).post("/internal/v1/assets/core/snapshots").then().statusCode(403);
        given().header("X-Hero-Association-Assets-Core-Service-Key",CoreAuthorityStubResource.MARKET_KEY).contentType("application/json").body(body).post("/internal/v1/assets/core/snapshots").then().statusCode(403);
        given().header("X-Hero-Association-Assets-Core-Service-Key",CoreAuthorityStubResource.CORE_KEY).contentType("application/json").body(body).post("/internal/v1/assets/core/snapshots").then().statusCode(200);
        given().header("X-Hero-Association-Market-Service-Key",CoreAuthorityStubResource.CORE_KEY).get("/internal/v1/assets/operations/"+UuidV7.next()).then().statusCode(403);
        given().contentType("application/json").body("{}").post("/api/v1/gold-transfers").then().statusCode(400);
    }
    @Test void playerTokenIsRequiredForPublicTransfersAndNewReservations() {
        given().contentType("application/json").body("{}").post("/api/v1/gold-transfers").then().statusCode(401);
        given().header("X-Hero-Association-Market-Service-Key",CoreAuthorityStubResource.MARKET_KEY).contentType("application/json").body("{}").post("/internal/v1/assets/reservations").then().statusCode(401);
    }
}
