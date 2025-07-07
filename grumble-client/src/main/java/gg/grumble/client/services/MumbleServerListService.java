package gg.grumble.client.services;

import gg.grumble.client.models.MumbleServerList;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MumbleServerListService {
    private final WebClient client;

    public MumbleServerListService(WebClient.Builder builder) {
        this.client = builder
                .baseUrl("https://publist.mumble.info")
                .build();
    }

    /** Fetches and returns the list of servers */
    public Mono<MumbleServerList> fetchServers() {
        return client.get()
                .uri("/v1/list")
                .retrieve()
                .bodyToMono(MumbleServerList.class);
    }
}
