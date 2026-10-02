package io.tiagovibeson.heroassociation.application.assets;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import io.tiagovibeson.heroassociation.application.*;
import io.tiagovibeson.heroassociation.application.exception.*;
import io.tiagovibeson.heroassociation.domain.*;
import io.tiagovibeson.heroassociation.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;
import static io.tiagovibeson.heroassociation.application.assets.AssetCommands.*;

@ApplicationScoped
public class AssetWorkflowTransactions {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject AgencyAccessService access;
    @Inject AssetCommandLocks keys;
    @Inject HeroRepository heroRepository;
    @Inject PartyRepository parties;
    @Inject QuestRepository quests;
    @Inject CreatureDefinitionResolver creatures;
    @Inject AssetsClient assets;
    @Inject io.tiagovibeson.heroassociation.application.expedition.ExpeditionSettlementTransactions settlements;

    @Transactional(REQUIRES_NEW)
    public UUID rune(UUID key, UUID agency, UUID heroId, int slot, UUID rune, RuneInventoryOwnerType source, boolean equip) {
        requireId(key); requireId(agency); requireId(heroId);
        UUID manager = access.currentManager().getId();
        var request = mapper.createObjectNode().put("agencyId", agency.toString()).put("heroId", heroId.toString()).put("slotIndex", slot);
        request.put("runeId", rune == null ? null : rune.toString()).put("sourceOwnerType", source == null ? null : source.name());
        String kind = equip ? "RUNE_EQUIP" : "RUNE_UNEQUIP";
        keys.lock(key);
        if (replay(key, kind, manager, request) != null) return key;
        access.requireMembership(agency);
        Hero hero = heroRepository.findForUpdate(heroId).orElseThrow(() -> new HeroNotFoundException(heroId));
        boolean agencyHero = hero.getAgency() != null && agency.equals(hero.getAgency().getId());
        boolean personalHero = hero.getOwnerManager() != null && manager.equals(hero.getOwnerManager().getId());
        if (!agencyHero && !personalHero) throw new HeroNotFoundException(heroId);
        if (hero.getActivity() == HeroActivity.ON_QUEST || hero.getActivity() == HeroActivity.ON_EXPEDITION) throw new HeroOnQuestException(heroId);
        hero.requireNoPendingAssets();
        if (slot < 0 || slot > 4) throw new InvalidRuneSlotException(slot);
        if (equip) { requireId(rune); if (source == null) throw new BadRequestException("Rune source is required."); }
        var command = base(mapper, key, kind, manager);
        command.put("agencyId", agency.toString()).put("heroId", heroId.toString()).put("slotIndex", slot);
        if (equip) command.put("runeId", rune.toString()).put("sourceOwnerType", source.name());
        AssetWorkflow workflow = new AssetWorkflow(key, kind, manager, agency, request.toString(), command.toString());
        workflow.heroId = heroId; em.persist(workflow); hero.fenceAssets(key); em.flush(); return key;
    }
    @Transactional(REQUIRES_NEW)
    public UUID quest(UUID key, UUID agency, UUID questId, UUID partyId, long expectedFee) {
        requireId(key); requireId(agency); requireId(questId); requireId(partyId);
        UUID manager = access.currentManager().getId();
        var request = mapper.createObjectNode().put("agencyId", agency.toString()).put("questId", questId.toString()).put("partyId", partyId.toString()).put("expectedBorrowingFeeGold", expectedFee);
        keys.lock(key);
        if (replay(key, "QUEST_START", manager, request) != null) return key;
        access.requireMembership(agency);
        Quest quest = quests.findForStart(agency, questId).orElseThrow(() -> new QuestNotFoundException(questId));
        Party party = parties.findOwnedForUpdate(agency, partyId, manager).orElseThrow(() -> new PartyNotFoundException(partyId));
        party.requireNoPendingAssets();
        if (quest.getStatus() != QuestStatus.AVAILABLE || em.createQuery("select count(w) from AssetWorkflow w where w.questId = :quest and w.status in ('PENDING', 'CONFLICT')", Long.class)
                .setParameter("quest", questId).getSingleResult() != 0) throw new QuestNotAvailableException(questId);
        if (party.getQuest() != null && party.getQuest().getStatus() == QuestStatus.IN_PROGRESS) throw new PartyOnQuestException(partyId);
        List<Hero> heroes = heroRepository.listByPartyForUpdate(partyId);
        if (heroes.size() < quest.getMinimumHeroes() || heroes.size() > quest.getMaximumHeroes()) throw new InvalidQuestPartySizeException(quest.getMinimumHeroes(), quest.getMaximumHeroes());
        long fee = 0;
        for (Hero hero : heroes) {
            hero.requireNoPendingAssets();
            if (hero.getActivity() == HeroActivity.ON_QUEST || hero.getActivity() == HeroActivity.ON_EXPEDITION) throw new HeroOnQuestException(hero.getId());
            if (hero.getOwnerManager() != null && !manager.equals(hero.getOwnerManager().getId())) throw new HeroNotFoundException(hero.getId());
            if (hero.getOwnerManager() == null) {
                if (hero.getAgency() == null || !agency.equals(hero.getAgency().getId())) throw new BorrowingFeeRejectedException("A Party Hero is not owned by this agency.");
                try { fee = Math.addExact(fee, hero.getBorrowingFeeGold()); } catch (ArithmeticException overflow) { throw new BorrowingFeeRejectedException("Borrowing fee total is too large."); }
            }
        }
        if (fee != expectedFee) throw new BorrowingFeeRejectedException("Borrowing fee changed; refresh before starting the Quest.");
        var command = base(mapper, key, "QUEST_START", manager);
        command.put("agencyId", agency.toString()).put("feeGold", fee).set("heroIds", mapper.valueToTree(heroes.stream().map(Hero::getId).toList()));
        AssetWorkflow workflow = new AssetWorkflow(key, "QUEST_START", manager, agency, request.toString(), command.toString());
        workflow.questId = questId; workflow.partyId = partyId;
        workflow.contextJson = mapper.valueToTree(creatures.resolveLatest(quest.getCreatureName())).toString();
        em.persist(workflow); party.fenceAssets(key); heroes.forEach(hero -> hero.fenceAssets(key)); em.flush(); return key;
    }
    public AssetWorkflow replay(UUID key, String kind, UUID manager, JsonNode request) {
        AssetWorkflow existing = em.find(AssetWorkflow.class, key);
        if (existing != null && (!kind.equals(existing.kind) || !manager.equals(existing.managerId) || !json(existing.requestJson).equals(json(request.toString()))))
            throw new WebApplicationException(jakarta.ws.rs.core.Response.status(409).entity(Map.of("message", "Operation key was already used for a different request.")).build());
        return existing;
    }
    @Transactional(REQUIRES_NEW) public Claim claim(UUID id) {
        AssetWorkflow workflow = em.find(AssetWorkflow.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (workflow == null || !"PENDING".equals(workflow.status) || workflow.claimUntil != null && workflow.claimUntil.isAfter(Instant.now())) return null;
        workflow.claimToken = UuidV7.next(); workflow.claimUntil = Instant.now().plusSeconds(60); workflow.attempts++;
        return new Claim(id, workflow.claimToken, workflow.commandJson);
    }
    @Transactional(REQUIRES_NEW) public void finish(Claim claim, JsonNode receipt) {
        AssetWorkflow workflow = owned(claim);
        if (workflow == null) return;
        validateReceipt(json(workflow.commandJson), receipt);
        boolean rejected = "REJECTED".equals(receipt.path("status").asText());
        if ("EXPEDITION_CREDIT".equals(workflow.kind)) {
            if (rejected) throw new ProtocolConflict("Assets rejected the authoritative Expedition aggregate.");
            settlements.finish(workflow, receipt);
        } else {
            Quest quest = workflow.questId == null ? null : em.find(Quest.class, workflow.questId, LockModeType.PESSIMISTIC_WRITE);
            Party party = workflow.partyId == null ? null : em.find(Party.class, workflow.partyId, LockModeType.PESSIMISTIC_WRITE);
            List<UUID> ids = workflow.heroId == null ? ids(json(workflow.commandJson).path("heroIds")) : List.of(workflow.heroId);
            List<Hero> heroes = ids.stream().sorted().map(id -> em.find(Hero.class, id, LockModeType.PESSIMISTIC_WRITE)).toList();
            if (heroes.stream().anyMatch(hero -> hero == null || !workflow.id.equals(hero.getPendingAssetOperation()))
                    || party != null && !workflow.id.equals(party.getPendingAssetOperation())) throw new ProtocolConflict("Core eligibility fence differs from the staged operation.");
            Map<UUID, List<AssetsClient.RuneSlot>> loadouts = rejected ? Map.of() : loadouts(receipt.path("heroes"));
            if (!rejected && !loadouts.keySet().equals(new HashSet<>(ids))) throw new ProtocolConflict("Assets receipt omitted an affected Hero loadout.");
            if (!rejected) assets.decorate(heroes, loadouts);
            heroes.forEach(hero -> hero.finishAssets(workflow.id));
            if (party != null) party.finishAssets(workflow.id);
            if (!rejected && quest != null) {
                if (quest.getStatus() != QuestStatus.AVAILABLE || party.getQuest() != null && party.getQuest().getStatus() == QuestStatus.IN_PROGRESS)
                    throw new ProtocolConflict("Core Quest changed while its payment was pending.");
                quest.startWith(party);
                heroes.forEach(hero -> hero.changeActivity(HeroActivity.ON_QUEST, party.getAgency().getRestLevel()));
                CreatureCombatProfile profile;
                try { profile = mapper.readValue(workflow.contextJson, CreatureCombatProfile.class); }
                catch (java.io.IOException invalid) { throw new ProtocolConflict("Pinned creature profile is invalid."); }
                em.persist(QuestCombat.start(quest, heroes, profile));
            }
        }
        workflow.status = rejected ? "REJECTED" : "APPLIED";
        workflow.rejectionStatus = rejected ? receipt.path("rejectionStatus").asInt() : null;
        workflow.message = rejected ? receipt.path("message").asText("Assets rejected the operation.") : null;
        workflow.receiptJson = receipt.toString(); workflow.claimToken = null; workflow.claimUntil = null; em.flush();
    }
    @Transactional(REQUIRES_NEW) public void retry(Claim claim, boolean conflict, String message) {
        AssetWorkflow workflow = owned(claim); if (workflow == null) return;
        workflow.claimToken = null; workflow.claimUntil = null;
        workflow.message = message;
        if (conflict) { workflow.status = "CONFLICT"; workflow.rejectionStatus = 409; }
        else workflow.nextAttemptAt = Instant.now().plusSeconds(Math.min(30, 1L << Math.min(5, workflow.attempts - 1))).plusMillis(java.util.concurrent.ThreadLocalRandom.current().nextLong(250));
    }
    @Transactional(REQUIRES_NEW) public AssetWorkflow.View view(UUID id, UUID manager) {
        AssetWorkflow workflow = em.find(AssetWorkflow.class, id);
        if (workflow == null) throw new NotFoundException();
        if (manager != null && !manager.equals(workflow.managerId)) throw new ForbiddenException();
        return workflow.view();
    }
    @Transactional(REQUIRES_NEW) public List<UUID> due() {
        return em.createQuery("select w.id from AssetWorkflow w where w.status = 'PENDING' and w.nextAttemptAt <= :now and (w.claimUntil is null or w.claimUntil <= :now) order by w.createdAt", UUID.class)
                .setParameter("now", Instant.now()).setMaxResults(10).getResultList();
    }
    @Transactional(REQUIRES_NEW) public long pendingCount() { return em.createQuery("select count(w) from AssetWorkflow w where w.status in ('PENDING', 'CONFLICT')", Long.class).getSingleResult(); }
    private AssetWorkflow owned(Claim claim) {
        AssetWorkflow workflow = em.find(AssetWorkflow.class, claim.id(), LockModeType.PESSIMISTIC_WRITE);
        return workflow != null && "PENDING".equals(workflow.status) && claim.token().equals(workflow.claimToken) ? workflow : null;
    }
    private JsonNode json(String value) {
        try { return mapper.readTree(value); } catch (java.io.IOException invalid) { throw new ProtocolConflict("Stored workflow JSON is invalid."); }
    }
    public Map<UUID, List<AssetsClient.RuneSlot>> loadouts(JsonNode value) {
        try { return mapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<Map<UUID, List<AssetsClient.RuneSlot>>>() {}); }
        catch (IllegalArgumentException invalid) { throw new ProtocolConflict("Assets loadout receipt is invalid."); }
    }
    private List<UUID> ids(JsonNode value) {
        if (!value.isArray()) throw new ProtocolConflict("Stored Hero list is invalid.");
        List<UUID> ids = new ArrayList<>(); value.forEach(id -> ids.add(UUID.fromString(id.asText()))); return ids;
    }
    private void requireId(UUID id) { if (id == null || id.version() != 7 || id.variant() != 2) throw new BadRequestException("Operation and resource IDs must be RFC 9562 UUIDv7."); }
    public record Claim(UUID id, UUID token, String commandJson) { }
}
