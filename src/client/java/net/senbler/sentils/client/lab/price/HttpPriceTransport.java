package net.senbler.sentils.client.lab.price;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Plain GET requests with time limits. No API key or other header about the player is sent. */
public final class HttpPriceTransport implements PriceService.Transport {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private HttpClient http;

    @Override
    public PriceService.Response get(String url) throws IOException, InterruptedException {
        if (this.http == null) this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());

        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) headers.put(name.toLowerCase(Locale.ROOT), values.getFirst());
        });
        return new PriceService.Response(response.statusCode(), response.body(), headers);
    }
}
