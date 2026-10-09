package net.senbler.sentils.client.lab;

import static net.senbler.sentils.client.lab.Fixtures.HOUR;
import static net.senbler.sentils.client.lab.Fixtures.MINUTE;
import static net.senbler.sentils.client.lab.Fixtures.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.senbler.sentils.client.lab.price.PriceParser;
import net.senbler.sentils.client.lab.price.PriceService;
import net.senbler.sentils.client.lab.price.PriceService.Outcome;
import net.senbler.sentils.client.lab.price.PriceService.Response;
import net.senbler.sentils.client.lab.price.PriceSnapshot;

/** Price fetching, against canned responses. Nothing here goes near the network. */
class PriceTest {

    // The shape of the API's own documented example, with the two sides of the order book.
    private static final String BAZAAR = """
            {"success":true,"lastUpdated":1590854517479,"products":{
              "WHEAT":{"product_id":"WHEAT",
                "sell_summary":[{"amount":20569,"pricePerUnit":4.2,"orders":1},{"amount":140326,"pricePerUnit":3.8,"orders":2}],
                "buy_summary":[{"amount":640,"pricePerUnit":4.8,"orders":1},{"amount":640,"pricePerUnit":4.9,"orders":1}],
                "quick_status":{"productId":"WHEAT","sellPrice":4.1,"buyPrice":4.85}},
              "NO_ORDERS":{"product_id":"NO_ORDERS","sell_summary":[],"buy_summary":[]}
            }}""";
    private static final String ITEMS = """
            {"success":true,"items":[
              {"id":"WHEAT","name":"Wheat","npc_sell_price":6},
              {"id":"HOE","name":"Hoe","npc_sell_price":10},
              {"id":"NO_PRICE","name":"Thing"},
              {"name":"no id"}, 5
            ]}""";

    /** Answers requests from a script and keeps a list of what was asked for. */
    private static final class FakeTransport implements PriceService.Transport {
        final List<String> requests = new ArrayList<>();
        Response bazaar = new Response(200, BAZAAR, Map.of());
        Response items = new Response(200, ITEMS, Map.of());
        IOException failure;

        @Override
        public Response get(String url) throws IOException {
            this.requests.add(url);
            if (this.failure != null) throw this.failure;
            return url.equals(PriceService.BAZAAR_URL) ? this.bazaar : this.items;
        }
    }

    private AtomicLong clock;
    private FakeTransport transport;
    private PriceService service;

    @BeforeEach
    void setUp() {
        this.clock = new AtomicLong(START);
        this.transport = new FakeTransport();
        this.service = new PriceService(this.transport, this.clock::get, null);
    }

    // --- Reading the lists ---

    @Test
    void theTwoSidesOfTheOrderBookAreReadTheRightWayRound() throws PriceParser.BadResponse {
        PriceSnapshot prices = new PriceSnapshot();
        PriceParser.readBazaar(BAZAAR, prices);

        PriceSnapshot.Entry wheat = prices.get("WHEAT");
        assertEquals(4.2, wheat.instantSell, "selling instantly fills the highest buy order, in sell_summary");
        assertEquals(4.8, wheat.sellOrder, "a sell order competes with the lowest sell offer, in buy_summary");
        assertTrue(wheat.instantSell < wheat.sellOrder);
        assertNull(prices.get("NO_ORDERS").instantSell, "no orders means no price, not a price of 0");
        assertNull(prices.get("NO_ORDERS").sellOrder);
    }

    @Test
    void theItemListGivesNamesAndNpcPrices() throws PriceParser.BadResponse {
        PriceSnapshot prices = new PriceSnapshot();
        PriceParser.readItems(ITEMS, prices);

        assertEquals(3, prices.items.size(), "entries without an id are skipped");
        assertEquals("Wheat", prices.get("WHEAT").name);
        assertEquals(6.0, prices.get("WHEAT").npc);
        assertNull(prices.get("NO_PRICE").npc);
        assertEquals("HOE", prices.idForName("hoe"));
        assertNull(prices.idForName("nothing"));
    }

    @Test
    void listsThatAreNotWhatWasExpectedAreRefused() {
        PriceSnapshot prices = new PriceSnapshot();
        assertThrows(PriceParser.BadResponse.class, () -> PriceParser.readBazaar("<html>busy</html>", prices));
        assertThrows(PriceParser.BadResponse.class, () -> PriceParser.readBazaar("{\"success\":false}", prices));
        assertThrows(PriceParser.BadResponse.class, () -> PriceParser.readBazaar("{\"success\":true}", prices));
        assertThrows(PriceParser.BadResponse.class, () -> PriceParser.readItems("[]", prices));
    }

    @Test
    void aNameSharedByTwoItemsDoesNotPickOne() {
        PriceSnapshot prices = Fixtures.prices();
        prices.items.put("WHEAT_2", Fixtures.entry("WHEAT_2", "Wheat", 1.0, 1.0, 1.0));
        assertNull(prices.idForName("Wheat"));
    }

    // --- Fetching ---

    @Test
    void aRefreshFetchesTheTwoPublicListsAndNothingElse() {
        PriceService.Result result = this.service.refresh();

        assertEquals(Outcome.UPDATED, result.outcome());
        assertEquals(List.of(PriceService.BAZAAR_URL, PriceService.ITEMS_URL), this.transport.requests);
        PriceSnapshot prices = this.service.current();
        assertEquals(START, prices.retrievedMillis);
        assertEquals(PriceService.SOURCE, prices.source);
        assertEquals("Wheat", prices.get("WHEAT").name);
        assertEquals(4.2, prices.get("WHEAT").instantSell);
        assertEquals(6.0, prices.get("WHEAT").npc);
        assertEquals(10.0, prices.get("HOE").npc);
    }

    @Test
    void askingAgainStraightAwayUsesTheCacheInsteadOfTheNetwork() {
        this.service.refresh();
        PriceSnapshot first = this.service.current();
        this.clock.addAndGet(MINUTE);

        PriceService.Result again = this.service.refresh();
        assertEquals(Outcome.TOO_SOON, again.outcome());
        assertEquals(2, this.transport.requests.size(), "no further request was made");
        assertSame(first, this.service.current());
        assertTrue(this.service.millisUntilAllowed() > 0);

        this.clock.addAndGet(10 * MINUTE);
        assertEquals(0, this.service.millisUntilAllowed());
        assertEquals(Outcome.UPDATED, this.service.refresh().outcome());
        assertEquals(4, this.transport.requests.size());
    }

    @Test
    void aServerErrorKeepsTheOldPricesAndWaitsLongerEachTime() {
        this.service.refresh();
        PriceSnapshot good = this.service.current();
        this.clock.addAndGet(HOUR);

        this.transport.bazaar = new Response(503, "busy", Map.of());
        assertEquals(Outcome.FAILED, this.service.refresh().outcome());
        assertSame(good, this.service.current(), "a failure never replaces good prices");
        long firstWait = this.service.millisUntilAllowed();
        assertTrue(firstWait > 0);

        this.clock.addAndGet(firstWait);
        assertEquals(Outcome.FAILED, this.service.refresh().outcome());
        long secondWait = this.service.millisUntilAllowed();
        assertTrue(secondWait > firstWait, "it backs off");

        // Many failures in a row: the wait stops growing somewhere sensible.
        for (int i = 0; i < 20; i++) {
            this.clock.addAndGet(this.service.millisUntilAllowed());
            this.service.refresh();
        }
        assertTrue(this.service.millisUntilAllowed() <= 30 * MINUTE);

        // Then it works again, and the next failure starts from the short wait.
        this.clock.addAndGet(this.service.millisUntilAllowed());
        this.transport.bazaar = new Response(200, BAZAAR, Map.of());
        assertEquals(Outcome.UPDATED, this.service.refresh().outcome());
    }

    @Test
    void aTimeoutIsAFailureNotACrash() {
        this.transport.failure = new HttpTimeoutException("request timed out");
        PriceService.Result result = this.service.refresh();

        assertEquals(Outcome.FAILED, result.outcome());
        assertTrue(result.message().contains("timed out"));
        assertNull(this.service.current());
        assertEquals(1, this.transport.requests.size(), "it does not go on to the second list");
    }

    @Test
    void aRateLimitIsObeyedForAsLongAsTheServerSays() {
        this.transport.bazaar = new Response(429, "", Map.of("retry-after", "900"));
        PriceService.Result result = this.service.refresh();

        assertEquals(Outcome.RATE_LIMITED, result.outcome());
        assertEquals(15 * MINUTE, this.service.millisUntilAllowed());
        this.clock.addAndGet(14 * MINUTE);
        assertEquals(Outcome.TOO_SOON, this.service.refresh().outcome());
        assertEquals(1, this.transport.requests.size());
    }

    @Test
    void aRateLimitWithNoTimeGivenStillWaits() {
        this.transport.bazaar = new Response(429, "", Map.of());
        this.service.refresh();
        assertTrue(this.service.millisUntilAllowed() >= 5 * MINUTE);
    }

    @Test
    void aBadBazaarListIsAFailure() {
        this.transport.bazaar = new Response(200, "{\"success\":false,\"cause\":\"nope\"}", Map.of());
        assertEquals(Outcome.FAILED, this.service.refresh().outcome());
        assertNull(this.service.current());
    }

    @Test
    void theItemListFailingKeepsBazaarPricesAndTheNamesFromBefore() {
        this.service.refresh();
        this.clock.addAndGet(HOUR);
        this.transport.items = new Response(500, "", Map.of());

        PriceService.Result result = this.service.refresh();
        assertEquals(Outcome.UPDATED, result.outcome());
        assertTrue(result.message().contains("item list failed"));
        PriceSnapshot prices = this.service.current();
        assertEquals(START + HOUR, prices.retrievedMillis);
        assertEquals("Wheat", prices.get("WHEAT").name);
        assertEquals(6.0, prices.get("WHEAT").npc);
        assertEquals(4.2, prices.get("WHEAT").instantSell);
    }

    @Test
    void readingThePricesDoesNotWaitForARefreshInProgress() throws Exception {
        // A request that hangs until it is let go, as a slow network would.
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch letGo = new CountDownLatch(1);
        PriceSnapshot cached = Fixtures.prices();
        PriceService slow = new PriceService(url -> {
            requestStarted.countDown();
            letGo.await();
            return new Response(200, url.equals(PriceService.BAZAAR_URL) ? BAZAAR : ITEMS, Map.of());
        }, this.clock::get, cached);

        CompletableFuture<PriceService.Result> refresh = CompletableFuture.supplyAsync(slow::refresh);
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS));

        // The game's thread asks for the prices every frame. It must get the old ones at once.
        CompletableFuture<PriceSnapshot> read = CompletableFuture.supplyAsync(slow::current);
        assertSame(cached, read.get(2, TimeUnit.SECONDS), "not held up by the refresh");
        CompletableFuture<Long> wait = CompletableFuture.supplyAsync(slow::millisUntilAllowed);
        assertEquals(0L, wait.get(2, TimeUnit.SECONDS));

        letGo.countDown();
        assertEquals(Outcome.UPDATED, refresh.get(5, TimeUnit.SECONDS).outcome());
        assertEquals(4.2, slow.current().get("WHEAT").instantSell);
    }

    @Test
    void pricesLoadedFromDiskAreUsedUntilARefresh() {
        PriceSnapshot cached = Fixtures.prices();
        PriceService fromCache = new PriceService(this.transport, this.clock::get, cached);

        assertSame(cached, fromCache.current());
        assertTrue(this.transport.requests.isEmpty(), "having a cache makes no request");
        assertFalse(cached.isOlderThan(HOUR, START + MINUTE));
        assertTrue(cached.isOlderThan(HOUR, START + 2 * HOUR));
        assertNotNull(fromCache.current().get("WHEAT"));
    }
}
