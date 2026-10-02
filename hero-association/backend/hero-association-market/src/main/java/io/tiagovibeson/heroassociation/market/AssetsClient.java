package io.tiagovibeson.heroassociation.market;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssetsClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Inject ObjectMapper mapper;
    @Inject OpenTelemetry telemetry;
    @ConfigProperty(name = "hero-association.assets.base-url") String baseUrl;
    @ConfigProperty(name = "hero-association.assets.market-service-key") Optional<String> serviceKey;

    public OwnerContext context(ContextRequest request, String token) {
        return call("/context", request, token, OwnerContext.class, false).orElseThrow();
    }
    public Reservation reserve(PlacementClaim placement, String token) {
        var body = new ReserveRequest(placement.reservationKey(), placement.ownerType(), placement.ownerId(),
                placement.side() == Side.BUY ? "GOLD" : "ITEM", placement.itemId(), placement.quantity(), placement.priceGoldPerItem());
        return call("/reservations", body, token, Reservation.class, false).orElseThrow();
    }
    public Optional<Reservation> reservation(UUID key) { return call("/reservations/" + key, null, null, Reservation.class, true); }
    public Optional<Receipt> receipt(UUID key) { return call("/operations/" + key, null, null, Receipt.class, true); }
    public Receipt settle(TradeClaim trade) {
        return call("/settlements", new SettlementRequest(trade.id(), trade.buyerReservationKey(),
                trade.sellerReservationKey(), trade.quantity(), trade.priceGoldPerItem()), null, Receipt.class, false).orElseThrow();
    }
    public Receipt close(UUID reservationKey, UUID operationKey) {
        return call("/reservations/" + reservationKey + "/close", new CloseRequest(operationKey), null, Receipt.class, false).orElseThrow();
    }
    private <T> Optional<T> call(String path, Object body, String token, Class<T> type, boolean allowAbsent) {
        String key = serviceKey.filter(value -> value.length() >= 32)
                .orElseThrow(() -> new AssetsFailure(503, "Assets service credentials are not configured."));
        String base = baseUrl.replaceAll("/+$", "");
        var builder = HttpRequest.newBuilder(URI.create(base + "/internal/v1/assets" + path))
                .timeout(Duration.ofSeconds(5)).header("X-Hero-Association-Market-Service-Key", key)
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var span = telemetry.getTracer(AssetsClient.class.getName()).spanBuilder("assets.request")
                .setSpanKind(SpanKind.CLIENT).startSpan();
        try (var scope = span.makeCurrent()) {
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            telemetry.getPropagators().getTextMapPropagator().inject(Context.current(), builder, (carrier, name, value) -> carrier.header(name, value));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            span.setAttribute("http.response.status_code", response.statusCode());
            if (allowAbsent && response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "Assets returned HTTP " + response.statusCode() + ".";
                try { message = mapper.readTree(response.body()).path("message").asText(message); } catch (IOException ignored) { }
                throw new AssetsFailure(response.statusCode(), message);
            }
            return Optional.of(mapper.readValue(response.body(), type));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new AssetsFailure(503, "Assets request interrupted.");
        } catch (IOException exception) {
            throw new AssetsFailure(503, "Assets are temporarily unavailable.");
        } finally { span.end(); }
    }
    public static class AssetsFailure extends RuntimeException {
        private final int status;
        public AssetsFailure(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
