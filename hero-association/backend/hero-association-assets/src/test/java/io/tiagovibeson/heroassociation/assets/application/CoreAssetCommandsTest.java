package io.tiagovibeson.heroassociation.assets.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.tiagovibeson.heroassociation.assets.domain.*;
import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoreAssetCommandsTest {
    static final UUID MANAGER = UUID.fromString("019c4c00-0000-7000-8000-000000000204");
    static final UUID AGENCY = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    static final UUID HERO = UUID.fromString("019c4c00-0010-7000-8000-000000000005");
    static final UUID ATTACK = UUID.fromString("019c4c00-0020-7000-8000-000000000001");
    static final UUID VITALITY = UUID.fromString("019c4c00-0020-7000-8000-000000000003");
    static final UUID ITEM = UUID.fromString("019c4c00-0070-7000-8000-000000000001");
    @Inject CoreAssetCommands commands;
    @Inject AssetBalances balances;
    @Inject AssetSnapshots snapshots;
    @Inject EntityManager em;
    private CoreAssetCommands.Command rune(UUID key, String kind, UUID rune, String source) {
        return new CoreAssetCommands.Command(key, kind, MANAGER, AGENCY, HERO, 0, rune, source, null, null, null, null, null);
    }
    private CoreAssetCommands.Command reward(UUID key, long gold, Map<UUID,Integer> items, Map<UUID,Integer> runes) {
        return new CoreAssetCommands.Command(key, "EXPEDITION_CREDIT", MANAGER, AGENCY, null, null, null, null, null, null, gold, items, runes);
    }
    private long gold(UUID owner) { return em.find(AssetWallet.class, owner).gold; }
    private long postings(UUID key) { return em.createQuery("select count(p) from AssetLedgerEntry p where p.operationKey = :key", Long.class).setParameter("key", key).getSingleResult(); }
    @Test @TestTransaction void equipmentReplacementAndUnequipConserveRunesAndReplayReceipts() {
        var equip = rune(UuidV7.next(), "RUNE_EQUIP", ATTACK, "AGENCY");
        var receipt = commands.execute(equip);
        assertEquals("APPLIED", receipt.status()); assertEquals(receipt, commands.execute(equip));
        assertEquals(0, balances.quantity(AGENCY, "RUNE", ATTACK)); assertEquals(2, postings(equip.operationKey()));
        var replace = rune(UuidV7.next(), "RUNE_EQUIP", VITALITY, "AGENCY");
        commands.execute(replace);
        assertEquals(1, balances.quantity(MANAGER, "RUNE", ATTACK));
        var unequip = rune(UuidV7.next(), "RUNE_UNEQUIP", null, null);
        assertEquals(commands.execute(unequip), commands.execute(unequip));
        assertEquals(1, balances.quantity(MANAGER, "RUNE", VITALITY));
        assertTrue(snapshots.loadouts(List.of(HERO)).get(HERO).isEmpty());
    }
    @Test @TestTransaction void rejectedEquipmentDoesNotMoveAnythingAndIsImmutable() {
        var command = rune(UuidV7.next(), "RUNE_EQUIP", ATTACK, "MANAGER");
        assertEquals("REJECTED", commands.execute(command).status()); assertEquals(0, postings(command.operationKey()));
        balances.stack(balances.wallet(AssetOwnerType.MANAGER, MANAGER), "RUNE", ATTACK, 1, UuidV7.next());
        assertEquals("REJECTED", commands.execute(command).status()); assertEquals(1, balances.quantity(MANAGER, "RUNE", ATTACK));
        assertTrue(snapshots.loadouts(List.of(HERO)).get(HERO).isEmpty());
    }
    @Test @TestTransaction void questRewardsCreditGoldAndItemsOnceAndBindTheAmount() {
        long before = gold(MANAGER); int items = balances.quantity(MANAGER, "ITEM", ITEM);
        var command = new CoreAssetCommands.Command(UuidV7.next(), "QUEST_REWARD", MANAGER, null, null, null, null, null, null, null, 160L, Map.of(ITEM, 1), Map.of());
        var receipt = commands.execute(command);
        assertEquals("APPLIED", receipt.status()); assertEquals(receipt, commands.execute(command));
        assertEquals(before + 160, gold(MANAGER)); assertEquals(items + 1, balances.quantity(MANAGER, "ITEM", ITEM));
        assertEquals(2, postings(command.operationKey()));
        var changed = new CoreAssetCommands.Command(command.operationKey(), "QUEST_REWARD", MANAGER, null, null, null, null, null, null, null, 161L, Map.of(ITEM, 1), Map.of());
        assertThrows(AssetOperationRejectedException.class, () -> commands.execute(changed));
    }
    @Test @TestTransaction void retiredQuestFeeCommandIsRejectedWithoutDebit() {
        long before = gold(MANAGER);
        var retired = new CoreAssetCommands.Command(UuidV7.next(), "QUEST_START", MANAGER, AGENCY, null, null, null, null, 25L, List.of(HERO), null, null, null);
        assertThrows(AssetOperationRejectedException.class, () -> commands.execute(retired));
        assertEquals(before, gold(MANAGER)); assertEquals(0, postings(retired.operationKey()));
    }
    @Test @TestTransaction void expeditionCreditsAllCarriedAssetsOnceWithAnAtomicReceipt() {
        long before = gold(MANAGER); int items = balances.quantity(MANAGER, "ITEM", ITEM);
        var command = reward(UuidV7.next(), 7, Map.of(ITEM,2), Map.of(ATTACK,1));
        assertEquals(commands.execute(command), commands.execute(command));
        assertEquals(before + 7, gold(MANAGER)); assertEquals(items + 2, balances.quantity(MANAGER,"ITEM",ITEM));
        assertEquals(1, balances.quantity(MANAGER,"RUNE",ATTACK)); assertEquals(3, postings(command.operationKey()));
    }
    @Test @TestTransaction void unknownCarriedCatalogCannotPartiallyCreditGold() {
        long before = gold(MANAGER);
        var command = reward(UuidV7.next(), 7, Map.of(UuidV7.next(),2), Map.of());
        assertEquals("REJECTED", commands.execute(command).status()); assertEquals(before, gold(MANAGER)); assertEquals(0, postings(command.operationKey()));
    }
    @Test void capacityOverflowCanRecoverWithTheSameKeyAfterSpaceIsFreed() {
        var command = reward(UuidV7.next(), 7, Map.of(), Map.of());
        long original = QuarkusTransaction.requiringNew().call(() -> { var wallet=em.find(AssetWallet.class,MANAGER); long value=wallet.gold; wallet.gold=Long.MAX_VALUE; return value; });
        try {
            assertEquals(503, assertThrows(AssetOperationRejectedException.class, () -> commands.execute(command)).status());
            QuarkusTransaction.requiringNew().run(() -> { assertNull(em.find(AssetCommandReceipt.class,command.operationKey())); em.find(AssetWallet.class,MANAGER).gold=original; });
            assertEquals("APPLIED", commands.execute(command).status());
            assertEquals(commands.execute(command), commands.receipt(command.operationKey()));
            QuarkusTransaction.requiringNew().run(() -> assertEquals(original+7,gold(MANAGER)));
        } finally { QuarkusTransaction.requiringNew().run(() -> em.find(AssetWallet.class,MANAGER).gold=original); }
    }
    @Test @TestTransaction void snapshotReceiptPinsLoadoutDespiteLaterEquipmentChanges() {
        var snapshot = new CoreAssetCommands.Command(UuidV7.next(),"HERO_LOADOUT_SNAPSHOT",MANAGER,null,null,null,null,null,null,List.of(HERO),null,null,null);
        var first=commands.execute(snapshot); commands.execute(rune(UuidV7.next(),"RUNE_EQUIP",ATTACK,"AGENCY"));
        assertEquals(first,commands.execute(snapshot)); assertTrue(first.heroes().get(HERO).isEmpty());
        assertFalse(snapshots.loadouts(List.of(HERO)).get(HERO).isEmpty());
    }
    @Test @TestTransaction void equipmentAndRewardCannotReuseEachOthersKeys() {
        UUID key=UuidV7.next();commands.execute(reward(key,0,Map.of(),Map.of()));assertThrows(AssetOperationRejectedException.class,()->commands.execute(rune(key,"RUNE_EQUIP",ATTACK,"AGENCY")));
    }
}
