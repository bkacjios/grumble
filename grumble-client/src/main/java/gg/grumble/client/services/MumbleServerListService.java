package gg.grumble.client.services;

import gg.grumble.client.models.MumbleServerList;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MumbleServerListService {
    private final WebClient client;
    private final XmlMapper xmlMapper = new XmlMapper();

    public MumbleServerListService(@Value("${mumble.server.url}") String url, WebClient.Builder builder) {
        this.client = builder
                .baseUrl(url)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_XML_VALUE)
                .build();
    }

    /** Fetches the raw XML as String, then parses into your POJO */
    public Mono<MumbleServerList> fetchServers() {
        return client.get()
                .uri("/v1/list")
                .retrieve()
                .bodyToMono(String.class)
                .map(xml -> {
                    try {
                        return xmlMapper.readValue(xml, MumbleServerList.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse MumbleServerList XML", e);
                    }
                });
    }
}
