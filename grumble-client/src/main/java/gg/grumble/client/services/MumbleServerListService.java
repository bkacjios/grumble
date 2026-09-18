package gg.grumble.client.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import gg.grumble.client.config.AppProperties;
import gg.grumble.client.models.MumbleServerList;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class MumbleServerListService {
    private final HttpClient client = HttpClient.newHttpClient();
    private final URI listUri;
    private final Duration fetchTimeout;
    private final XmlMapper xmlMapper = new XmlMapper();

    public MumbleServerListService() {
        String baseUrl = AppProperties.serverListUrl();
        this.listUri = URI.create((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                + "/v1/list");
        this.fetchTimeout = AppProperties.serverListFetchTimeout();
    }

    /** Fetches the raw XML as String, then parses into your POJO */
    public CompletableFuture<MumbleServerList> fetchServers() {
        HttpRequest request = HttpRequest.newBuilder(listUri)
                .header("Accept", "text/xml")
                .timeout(fetchTimeout)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parse);
    }

    private MumbleServerList parse(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Server list request failed with HTTP " + response.statusCode());
        }
        try {
            return xmlMapper.readValue(response.body(), MumbleServerList.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to parse MumbleServerList XML", e);
        }
    }
}
