package io.tiagovibeson.heroassociation.quest;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import static io.tiagovibeson.heroassociation.contract.WorldContract.id;
import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

/** Short local transactions; remote identity and Assets calls never run under these locks. */
@ApplicationScoped
public class QuestTransactions {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @Transactional(REQUIRES_NEW)
    public List<Definition> definitions() {
        return em.createQuery("select q from QuestDefinition q where q.version = (select max(v.version) from QuestDefinition v where v.definitionId = q.definitionId) order by q.definitionId", QuestDefinition.class)
                .getResultList().stream().map(this::definition).toList();
    }

    @Transactional(REQUIRES_NEW)
    public Board board(UUID managerId, boolean atAgency) {
        QuestManager owner = owner(managerId);
        var recent = em.createQuery("select q from QuestAssignment q where q.managerId = :manager and q.status in ('COMPLETED', 'CANCELLED') order by q.acceptedAt desc, q.id desc", QuestAssignment.class)
                .setParameter("manager", managerId).setMaxResults(20).getResultList().stream().map(this::view).toList();
        return new Board(definitions(), owner.activeAssignment == null ? null : view(em.find(QuestAssignment.class, owner.activeAssignment)),
                recent, atAgency && owner.activeExpedition == null);
    }

    @Transactional(REQUIRES_NEW)
    public Assignment accept(UUID commandId, UUID managerId, UUID definitionId, boolean atAgency) {
        id(commandId); id(definitionId); lock(commandId);
        String intent = encode(Map.of("kind", "ACCEPT", "definitionId", definitionId));
        Assignment replay = replay(commandId, managerId, intent);
        if (replay != null) return replay;
        QuestManager owner = owner(managerId);
        if (!atAgency || owner.activeExpedition != null || owner.activeAssignment != null) throw new ClientErrorException("Accept one Quest while at the agency.", 409);
        var rows = em.createQuery("select q from QuestDefinition q where q.definitionId = :id order by q.version desc", QuestDefinition.class)
                .setParameter("id", definitionId).setMaxResults(1).getResultList();
        if (rows.isEmpty()) throw new NotFoundException("Quest definition is unavailable.");
        Definition definition = definition(rows.getFirst());
        var assignment = new QuestAssignment(); assignment.id = UuidV7.next(); assignment.managerId = managerId;
        assignment.definitionJson = encode(definition); assignment.progress = 0; assignment.status = "ACTIVE"; assignment.acceptedAt = Instant.now();
        em.persist(assignment); owner.activeAssignment = assignment.id;
        Assignment result = view(assignment); remember(commandId, managerId, intent, result); em.flush(); return result;
    }

    @Transactional(REQUIRES_NEW)
    public Assignment cancel(UUID commandId, UUID managerId, UUID assignmentId, boolean atAgency) {
        id(commandId); id(assignmentId); lock(commandId);
        String intent = encode(Map.of("kind", "CANCEL", "assignmentId", assignmentId));
        Assignment replay = replay(commandId, managerId, intent);
        if (replay != null) return replay;
        QuestManager owner = owner(managerId);
        QuestAssignment assignment = em.find(QuestAssignment.class, assignmentId);
        if (assignment == null || !managerId.equals(assignment.managerId)) throw new NotFoundException();
        if (!atAgency || owner.activeExpedition != null || !assignmentId.equals(owner.activeAssignment) || !"ACTIVE".equals(assignment.status))
            throw new ClientErrorException("Cancel an active Quest while at the agency.", 409);
        assignment.status = "CANCELLED"; assignment.finishedAt = Instant.now(); owner.activeAssignment = null;
        Assignment result = view(assignment); remember(commandId, managerId, intent, result); em.flush(); return result;
    }

    @Transactional(REQUIRES_NEW)
    public Pin pin(PinRequest request) {
        Objects.requireNonNull(request); lock(request.expeditionId());
        QuestManager owner = owner(request.ownerManagerId());
        QuestAdmission existing = em.find(QuestAdmission.class, request.expeditionId());
        if (existing != null) {
            if (!"ACTIVE".equals(existing.status)) throw new ClientErrorException("Expedition Quest admission is closed.", 409);
            Pin pin = decode(existing.pinJson, Pin.class);
            if (!same(pin, request) || !"ACTIVE".equals(existing.status)) throw new ClientErrorException("Expedition ID already has a different Quest admission.", 409);
            return pin;
        }
        if (owner.activeExpedition != null) throw new ClientErrorException("A Manager already has an active Expedition.", 409);
        Assignment assignment = owner.activeAssignment == null ? null : view(em.find(QuestAssignment.class, owner.activeAssignment));
        Pin pin = new Pin(request.expeditionId(), request.ownerManagerId(), request.agencyId(), request.mapId(), request.mapVersion(), assignment,
                assignment != null && assignment.definition().eligible(request.mapId()));
        var admission = new QuestAdmission(); admission.id = request.expeditionId(); admission.managerId = request.ownerManagerId();
        admission.pinJson = encode(pin); admission.status = "ACTIVE"; admission.createdAt = Instant.now(); em.persist(admission); owner.activeExpedition = admission.id;
        em.flush(); return pin;
    }

    @Transactional(REQUIRES_NEW)
    public boolean release(UUID expeditionId, UUID managerId) {
        id(expeditionId); id(managerId); lock(expeditionId);
        QuestManager owner = owner(managerId);
        QuestAdmission admission = em.find(QuestAdmission.class, expeditionId);
        if (admission == null) {
            admission = new QuestAdmission(); admission.id = expeditionId; admission.managerId = managerId;
            admission.pinJson = "{}"; admission.status = "RELEASED"; admission.createdAt = Instant.now(); em.persist(admission); em.flush(); return true;
        }
        if (!managerId.equals(admission.managerId)) throw new ClientErrorException("Quest admission belongs to another Manager.", 409);
        if ("RELEASED".equals(admission.status)) return false;
        if (!"ACTIVE".equals(admission.status) || admission.requestJson != null || !expeditionId.equals(owner.activeExpedition))
            throw new ClientErrorException("A returning Quest admission cannot be released.", 409);
        admission.status = "RELEASED"; owner.activeExpedition = null; em.flush(); return true;
    }

    @Transactional(REQUIRES_NEW)
    public ReturnReceipt stage(ReturnRequest request) {
        Objects.requireNonNull(request); lock(request.expeditionId());
        QuestManager owner = owner(request.ownerManagerId());
        QuestAdmission admission = em.find(QuestAdmission.class, request.expeditionId());
        if (admission == null || !request.ownerManagerId().equals(admission.managerId)) throw new ClientErrorException("Quest admission is missing.", 409);
        if ("RELEASED".equals(admission.status)) throw new ClientErrorException("Quest admission is closed.", 409);
        Pin pin = decode(admission.pinJson, Pin.class);
        if (!same(pin, new PinRequest(request.expeditionId(), request.ownerManagerId(), request.agencyId(), request.mapId(), request.mapVersion()))
                || (pin.assignment() == null) != (request.progress() == null)
                || request.progress() != null && !pin.equals(request.progress().pin()))
            throw new ClientErrorException("Quest return differs from admission.", 409);
        String input = encode(request);
        if (admission.requestJson != null) {
            if (!decode(admission.requestJson, ReturnRequest.class).equals(request)) throw new ClientErrorException("Expedition ID already returned a different Quest result.", 409);
            return receipt(admission);
        }
        if (!"ACTIVE".equals(admission.status) || !request.expeditionId().equals(owner.activeExpedition)) throw new ClientErrorException("Quest admission is no longer active.", 409);
        admission.requestJson = input;
        if (pin.assignment() != null) {
            QuestAssignment assignment = em.find(QuestAssignment.class, pin.assignment().assignmentId());
            if (assignment == null || !assignment.id.equals(owner.activeAssignment) || !"ACTIVE".equals(assignment.status)
                    || assignment.progress != pin.assignment().progress()) throw new ClientErrorException("Quest assignment changed while away.", 409);
            assignment.progress = request.progress().progress();
            if (request.progress().completed()) {
                assignment.status = "REWARD_PENDING"; admission.status = "REWARD_PENDING"; admission.nextAttemptAt = Instant.now();
                em.flush(); return receipt(admission);
            }
        }
        admission.status = "APPLIED"; owner.activeExpedition = null; em.flush(); return receipt(admission);
    }

    @Transactional(REQUIRES_NEW)
    public Claim claim(UUID expeditionId) {
        QuestAdmission admission = em.find(QuestAdmission.class, expeditionId, LockModeType.PESSIMISTIC_WRITE);
        if (admission == null || !"REWARD_PENDING".equals(admission.status) || admission.claimUntil != null && admission.claimUntil.isAfter(Instant.now())) return null;
        admission.claimToken = UuidV7.next(); admission.claimUntil = Instant.now().plusSeconds(60); admission.attempts++;
        Pin pin = decode(admission.pinJson, Pin.class);
        return new Claim(admission.id, admission.claimToken, pin.assignment());
    }

    @Transactional(REQUIRES_NEW)
    public void finish(Claim claim) {
        QuestManager owner = owner(claim.assignment().ownerManagerId());
        QuestAdmission admission = owned(claim);
        if (admission == null) return;
        var assignment = em.find(QuestAssignment.class, claim.assignment().assignmentId());
        if (!admission.id.equals(owner.activeExpedition) || assignment == null || !assignment.id.equals(owner.activeAssignment)
                || !"REWARD_PENDING".equals(assignment.status)) throw new IllegalStateException("Quest reward cursor changed.");
        assignment.status = "COMPLETED"; assignment.finishedAt = Instant.now(); owner.activeAssignment = null; owner.activeExpedition = null;
        admission.status = "APPLIED"; admission.claimToken = null; admission.claimUntil = null; admission.message = null; em.flush();
    }

    @Transactional(REQUIRES_NEW)
    public void retry(Claim claim, boolean conflict, String message) {
        QuestAdmission admission = owned(claim); if (admission == null) return;
        admission.claimToken = null; admission.claimUntil = null; admission.message = message;
        if (conflict) admission.status = "CONFLICT";
        else admission.nextAttemptAt = Instant.now().plusSeconds(Math.min(30, 1L << Math.min(5, admission.attempts - 1)));
    }

    @Transactional(REQUIRES_NEW) public ReturnReceipt receipt(UUID id) {
        var admission = em.find(QuestAdmission.class, id); if (admission == null) throw new NotFoundException(); return receipt(admission);
    }
    @Transactional(REQUIRES_NEW) public List<UUID> due() {
        return em.createQuery("select q.id from QuestAdmission q where q.status = 'REWARD_PENDING' and q.nextAttemptAt <= :now and (q.claimUntil is null or q.claimUntil <= :now) order by q.nextAttemptAt", UUID.class)
                .setParameter("now", Instant.now()).setMaxResults(10).getResultList();
    }
    @Transactional(REQUIRES_NEW) public List<Orphan> orphans() {
        return em.createQuery("select q from QuestAdmission q where q.status = 'ACTIVE' and q.createdAt < :older order by q.createdAt", QuestAdmission.class)
                .setParameter("older", Instant.now().minusSeconds(120)).setMaxResults(100).getResultList().stream()
                .map(row -> new Orphan(row.id, row.managerId)).toList();
    }
    private QuestAdmission owned(Claim claim) {
        var admission = em.find(QuestAdmission.class, claim.expeditionId(), LockModeType.PESSIMISTIC_WRITE);
        return admission != null && "REWARD_PENDING".equals(admission.status) && claim.token().equals(admission.claimToken) ? admission : null;
    }
    private ReturnReceipt receipt(QuestAdmission row) {
        Pin pin = decode(row.pinJson, Pin.class);
        return new ReturnReceipt(row.id, row.managerId, row.status, pin.assignment() == null ? null : pin.assignment().assignmentId());
    }
    private boolean same(Pin pin, PinRequest request) {
        return pin.expeditionId().equals(request.expeditionId()) && pin.ownerManagerId().equals(request.ownerManagerId())
                && pin.agencyId().equals(request.agencyId()) && pin.mapId().equals(request.mapId()) && pin.mapVersion() == request.mapVersion();
    }
    private QuestManager owner(UUID managerId) {
        id(managerId); lock(managerId);
        var owner = em.find(QuestManager.class, managerId);
        if (owner == null) { owner = new QuestManager(); owner.id = managerId; em.persist(owner); }
        return owner;
    }
    private void lock(UUID value) { em.createNativeQuery("select pg_advisory_xact_lock(?1)").setParameter(1, value.getMostSignificantBits() ^ value.getLeastSignificantBits()).getSingleResult(); }
    private Assignment replay(UUID commandId, UUID managerId, String intent) {
        var command = em.find(QuestCommand.class, commandId);
        if (command == null) return null;
        if (!managerId.equals(command.managerId) || !jsonEqual(intent, command.requestJson)) throw new ClientErrorException("Quest command ID was reused with a different request.", 409);
        return decode(command.responseJson, Assignment.class);
    }
    private void remember(UUID id, UUID manager, String request, Assignment response) {
        var command = new QuestCommand(); command.id = id; command.managerId = manager; command.requestJson = request; command.responseJson = encode(response); em.persist(command);
    }
    private Definition definition(QuestDefinition row) {
        Definition value = decode(row.payload, Definition.class);
        if (!row.definitionId.equals(value.definitionId()) || row.version != value.version()) throw new IllegalStateException("Quest definition identity differs from its payload.");
        return value;
    }
    private Assignment view(QuestAssignment row) { return new Assignment(row.id, row.managerId, decode(row.definitionJson, Definition.class), row.progress, row.status); }
    private boolean jsonEqual(String first, String second) {
        try { return mapper.readTree(first).equals(mapper.readTree(second)); } catch (java.io.IOException invalid) { throw new IllegalStateException("Stored Quest JSON is invalid.", invalid); }
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch (java.io.IOException invalid) { throw new IllegalStateException("Quest JSON could not be encoded.", invalid); }
    }
    private <T> T decode(String text, Class<T> type) {
        try { return mapper.readValue(text, type); } catch (java.io.IOException invalid) { throw new IllegalStateException("Stored Quest JSON is invalid.", invalid); }
    }
    public record Claim(UUID expeditionId, UUID token, Assignment assignment) { }
    public record Board(List<Definition> definitions, Assignment activeAssignment, List<Assignment> recentAssignments, boolean atAgency) { }
    public record Orphan(UUID expeditionId, UUID ownerManagerId) { }
}
