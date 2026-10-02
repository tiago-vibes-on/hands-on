package io.tiagovibeson.heroassociation.market;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;

/** A transactional ledger double used to exercise the real HTTP client and recovery database. */
public class AssetsStubResource implements QuarkusTestResourceLifecycleManager {
    static final UUID BUYER = UUID.fromString("019c4c00-0000-7000-8000-000000000204");
    static final UUID SELLER = UUID.fromString("019c4c00-0000-7000-8000-000000000203");
    static final UUID AGENCY = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    static final UUID ITEM = UUID.fromString("019c4c00-0070-7000-8000-000000000001");
    static final Map<UUID, Long> gold = new HashMap<>();
    static final Map<UUID, Integer> items = new HashMap<>();
    static final Map<UUID, Reservation> reservations = new HashMap<>();
    static final Map<UUID, Receipt> receipts = new HashMap<>();
    static final Set<UUID> closed = new HashSet<>();
    static boolean reserveUnavailable;
    static boolean settlementUnavailable;
    static int reserveResponsesLost;
    static int settlementResponsesLost;
    static int closeResponsesLost;
    static int debits;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    static synchronized void reset() {
        gold.clear(); items.clear(); reservations.clear(); receipts.clear(); closed.clear();
        gold.put(BUYER, 200L); gold.put(SELLER, 0L); gold.put(AGENCY, 1000L);
        items.put(BUYER, 0); items.put(SELLER, 20); items.put(AGENCY, 20);
        reserveUnavailable = false; settlementUnavailable = false;
        reserveResponsesLost = 0; settlementResponsesLost = 0; closeResponsesLost = 0; debits = 0;
    }
    @Override public Map<String, String> start() {
        try {
            reset(); server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
            server.createContext("/internal/v1/assets", this::handle); server.start();
            return Map.of("hero-association.assets.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "hero-association.assets.market-service-key", "test-only-assets-market-service-key-0123456789");
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void stop() { if (server != null) server.stop(0); if (executor != null) executor.shutdownNow(); }
    private void handle(HttpExchange exchange) throws IOException {
        synchronized (AssetsStubResource.class) {
            try {
                if (!"test-only-assets-market-service-key-0123456789".equals(exchange.getRequestHeaders().getFirst("X-Hero-Association-Market-Service-Key"))) {
                    reply(exchange, 403, Map.of("message", "Invalid service credential.")); return;
                }
                String path = exchange.getRequestURI().getPath().substring("/internal/v1/assets".length());
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                UUID manager = token != null && token.contains("seller") ? SELLER : BUYER;
                if (path.equals("/context")) {
                    ContextRequest request = mapper.readValue(exchange.getRequestBody(), ContextRequest.class);
                    UUID owner = request.ownerType() == OwnerType.MANAGER ? manager : request.ownerId();
                    if (request.ownerType() == OwnerType.MANAGER && request.ownerId() != null && !manager.equals(request.ownerId())) {
                        reply(exchange, 403, Map.of("message", "Other Manager.")); return;
                    }
                    reply(exchange, 200, new OwnerContext(manager, request.ownerType(), owner, "Owner " + owner,
                            request.itemId() == null ? null : new ItemDefinition(ITEM, "magic-crystal", "Magic Crystal", "✦"))); return;
                }
                if (path.equals("/reservations")) {
                    if (reserveUnavailable) { reply(exchange, 503, Map.of("message", "Unavailable.")); return; }
                    ReserveRequest request = mapper.readValue(exchange.getRequestBody(), ReserveRequest.class);
                    if (closed.contains(request.reservationKey())) { reply(exchange, 409, Map.of("message", "Closed.")); return; }
                    Reservation existing = reservations.get(request.reservationKey());
                    if (existing != null) { reply(exchange, 200, existing); return; }
                    if (request.ownerType() == OwnerType.MANAGER && !manager.equals(request.ownerId())) { reply(exchange, 403, Map.of("message", "Owner mismatch.")); return; }
                    long amount = request.unitPriceGoldPerItem() * request.quantity();
                    if ("GOLD".equals(request.resourceType())) {
                        if (gold.getOrDefault(request.ownerId(), 0L) < amount) { reply(exchange, 409, Map.of("message", "Insufficient gold.")); return; }
                        gold.compute(request.ownerId(), (id, value) -> value - amount);
                    } else {
                        if (items.getOrDefault(request.ownerId(), 0) < request.quantity()) { reply(exchange, 409, Map.of("message", "Insufficient items.")); return; }
                        items.compute(request.ownerId(), (id, value) -> value - request.quantity());
                    }
                    Reservation reservation = new Reservation(request.reservationKey(), manager, request.ownerType(), request.ownerId(),
                            request.resourceType(), request.itemId(), request.quantity(), request.quantity(), request.unitPriceGoldPerItem(), false);
                    reservations.put(request.reservationKey(), reservation); debits++;
                    if (reserveResponsesLost > 0) { reserveResponsesLost--; reply(exchange, 503, Map.of("message", "Confirmation lost.")); return; }
                    reply(exchange, 200, reservation); return;
                }
                if (path.startsWith("/operations/")) {
                    Receipt receipt = receipts.get(UUID.fromString(path.substring("/operations/".length())));
                    reply(exchange, receipt == null ? 404 : 200, receipt == null ? Map.of("message", "Absent.") : receipt); return;
                }
                if (path.startsWith("/reservations/") && path.endsWith("/close")) {
                    UUID id = UUID.fromString(path.substring("/reservations/".length(), path.length() - "/close".length()));
                    UUID operation = mapper.readValue(exchange.getRequestBody(), CloseRequest.class).operationKey();
                    Receipt existing = receipts.get(operation);
                    if (existing != null) { reply(exchange, 200, existing); return; }
                    Reservation reservation = reservations.get(id);
                    int quantity = reservation == null ? 0 : reservation.remainingQuantity();
                    if (quantity > 0) {
                        if (reservation.resourceType().equals("GOLD")) gold.merge(reservation.ownerId(), quantity * reservation.unitPriceGoldPerItem(), Long::sum);
                        else items.merge(reservation.ownerId(), quantity, Integer::sum);
                        reservations.put(id, remaining(reservation, 0));
                    }
                    closed.add(id);
                    Receipt receipt = new Receipt(operation, "CLOSE", id, null, quantity, 0); receipts.put(operation, receipt);
                    if (closeResponsesLost > 0) { closeResponsesLost--; reply(exchange, 503, Map.of("message", "Confirmation lost.")); return; }
                    reply(exchange, 200, receipt); return;
                }
                if (path.startsWith("/reservations/")) {
                    UUID id = UUID.fromString(path.substring("/reservations/".length()));
                    Reservation reservation = reservations.get(id);
                    if (reservation == null && closed.contains(id)) reservation = new Reservation(id, null, null, null, null, null, 0, 0, 0, true);
                    reply(exchange, reservation == null ? 404 : 200, reservation == null ? Map.of("message", "Absent.") : reservation); return;
                }
                if (path.equals("/settlements")) {
                    if (settlementUnavailable) { reply(exchange, 503, Map.of("message", "Unavailable.")); return; }
                    SettlementRequest request = mapper.readValue(exchange.getRequestBody(), SettlementRequest.class);
                    Receipt existing = receipts.get(request.operationKey());
                    if (existing != null) { reply(exchange, 200, existing); return; }
                    Reservation buy = reservations.get(request.buyerReservationKey());
                    Reservation sell = reservations.get(request.sellerReservationKey());
                    if (buy == null || sell == null || buy.remainingQuantity() < request.quantity() || sell.remainingQuantity() < request.quantity()) {
                        reply(exchange, 409, Map.of("message", "Inconsistent quantity.")); return;
                    }
                    long gross = request.quantity() * request.executionPriceGoldPerItem();
                    gold.merge(buy.ownerId(), request.quantity() * (buy.unitPriceGoldPerItem() - request.executionPriceGoldPerItem()), Long::sum);
                    gold.merge(sell.ownerId(), gross - gross / 10, Long::sum); items.merge(buy.ownerId(), request.quantity(), Integer::sum);
                    reservations.put(buy.reservationKey(), remaining(buy, buy.remainingQuantity() - request.quantity()));
                    reservations.put(sell.reservationKey(), remaining(sell, sell.remainingQuantity() - request.quantity()));
                    Receipt receipt = new Receipt(request.operationKey(), "TRADE_SETTLEMENT", buy.reservationKey(), sell.reservationKey(), request.quantity(), request.executionPriceGoldPerItem());
                    receipts.put(request.operationKey(), receipt);
                    if (settlementResponsesLost > 0) { settlementResponsesLost--; reply(exchange, 503, Map.of("message", "Confirmation lost.")); return; }
                    reply(exchange, 200, receipt); return;
                }
                reply(exchange, 404, Map.of("message", "Unknown path."));
            } catch (RuntimeException failure) { reply(exchange, 500, Map.of("message", "Stub failure: " + failure.getClass().getSimpleName())); }
        }
    }
    private Reservation remaining(Reservation value, int remaining) {
        return new Reservation(value.reservationKey(), value.requesterManagerId(), value.ownerType(), value.ownerId(), value.resourceType(),
                value.itemId(), value.initialQuantity(), remaining, value.unitPriceGoldPerItem(), false);
    }
    private void reply(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = mapper.writeValueAsBytes(value); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
    }
}
