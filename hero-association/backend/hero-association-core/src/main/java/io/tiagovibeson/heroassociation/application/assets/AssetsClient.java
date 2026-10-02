package io.tiagovibeson.heroassociation.application.assets;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.tiagovibeson.heroassociation.domain.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AssetsClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Inject ObjectMapper mapper;
    @Inject OpenTelemetry telemetry;
    @ConfigProperty(name = "hero-association.assets.base-url") String baseUrl;
    @ConfigProperty(name = "hero-association.assets.core-service-key") Optional<String> credential;
    public Snapshot snapshot(List<OwnerRequest> owners, List<UUID> heroes) {
        try {
            Snapshot snapshot = mapper.treeToValue(call("/snapshots", new SnapshotRequest(owners, heroes)), Snapshot.class);
            if (snapshot == null || snapshot.owners() == null || snapshot.heroes() == null
                    || !new HashSet<>(snapshot.owners().stream().map(owner -> new OwnerRequest(owner.ownerType(), owner.ownerId())).toList()).equals(new HashSet<>(owners))
                    || !snapshot.heroes().keySet().equals(new HashSet<>(heroes))) throw unavailable("Assets returned an incomplete snapshot.");
            for (var owner : snapshot.owners()) {
                if (owner.gold() < 0 || owner.items() == null || owner.runes() == null
                        || owner.items().stream().anyMatch(entry -> entry.quantity() < 0 || entry.item() == null)
                        || owner.runes().stream().anyMatch(entry -> entry.quantity() < 0 || entry.rune() == null)) throw unavailable("Assets returned an invalid owner snapshot.");
            }
            for (var slots : snapshot.heroes().values()) {
                if (slots == null || slots.size() > 5 || slots.stream().anyMatch(slot -> slot.slotIndex() < 0 || slot.slotIndex() >= 5 || slot.rune() == null)
                        || slots.stream().map(RuneSlot::slotIndex).distinct().count() != slots.size()) throw unavailable("Assets returned an invalid loadout snapshot.");
            }
            return snapshot;
        } catch (IOException invalid) { throw unavailable("Assets snapshot could not be decoded."); }
    }
    public JsonNode execute(JsonNode command) { return call("/commands", command); }
    public void decorate(List<Hero> heroes, Map<UUID, List<RuneSlot>> slots) {
        for (Hero hero : heroes) {
            List<RuneSlot> values = slots.get(hero.getId());
            if (values == null) throw unavailable("Assets omitted a Hero loadout.");
            hero.replaceRuneSnapshot(values.stream().map(slot -> new HeroRune(hero, slot.rune().value(), slot.slotIndex())).toList());
        }
    }
    private JsonNode call(String path, Object body) {
        String key = credential.filter(value -> value.length() >= 32).orElseThrow(() -> unavailable("Assets service credentials are not configured."));
        var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/internal/v1/assets/core" + path))
                .timeout(Duration.ofSeconds(5)).header("X-Hero-Association-Assets-Core-Service-Key", key).header("Content-Type", "application/json");
        var span = telemetry.getTracer(AssetsClient.class.getName()).spanBuilder("assets.core-request").setSpanKind(SpanKind.CLIENT).startSpan();
        try (var scope = span.makeCurrent()) {
            request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            telemetry.getPropagators().getTextMapPropagator().inject(Context.current(), request, (carrier, name, value) -> carrier.header(name, value));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            span.setAttribute("http.response.status_code", response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new AssetsFailure(response.statusCode());
            return mapper.readTree(response.body());
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw unavailable("Assets request was interrupted."); }
        catch (IOException unavailable) { throw unavailable("Assets is temporarily unavailable."); }
        finally { span.end(); }
    }
    public static WebApplicationException unavailable(String message) {
        return new WebApplicationException(Response.status(503).entity(Map.of("message", message)).build());
    }
    public static class AssetsFailure extends WebApplicationException {
        public final int status;
        AssetsFailure(int status) { super(Response.status(status >= 500 || status == 401 || status == 403 ? 503 : status)
                .entity(Map.of("message", "Assets returned HTTP " + status + ".")).build()); this.status = status; }
    }
    public record OwnerRequest(String ownerType, UUID ownerId) { }
    public record SnapshotRequest(List<OwnerRequest> owners, List<UUID> heroes) { }
    public record Snapshot(List<OwnerSnapshot> owners, Map<UUID, List<RuneSlot>> heroes) {
        public OwnerSnapshot owner(UUID id) { return owners.stream().filter(owner -> owner.ownerId().equals(id)).findFirst().orElseThrow(); }
    }
    public record OwnerSnapshot(String ownerType, UUID ownerId, long gold, List<ItemQuantity> items, List<RuneQuantity> runes) { }
    public record ItemQuantity(ItemDefinition item, int quantity) { }
    public record RuneQuantity(RuneDefinition rune, int quantity) { }
    public record RuneSlot(int slotIndex, RuneDefinition rune) { }
    public record ItemDefinition(UUID id, String code, String name, String symbol, String description) {
        public Item value() { return new Item(id, code, name, symbol, description); }
    }
    public record RuneDefinition(UUID id, String code, String name, String symbol, String stats, String description, String effect, double effectValue) {
        public Rune value() { return new Rune(id, code, name, symbol, stats, description, RuneEffect.valueOf(effect), effectValue); }
    }
}
