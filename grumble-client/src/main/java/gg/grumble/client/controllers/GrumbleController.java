package gg.grumble.client.controllers;

import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEvents;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
public class GrumbleController {

    private final MumbleClient client;

    public GrumbleController() {
        client = new MumbleClient("pi-two.lan");
        try {
            client.connect();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        client.addEventListener(MumbleEvents.Connected.class, event -> {
            client.authenticate("Java-BOT");
        });
        client.addEventListener(MumbleEvents.Disconnected.class, event -> {
            System.out.println("Disconnected from mumble server: " + event.reason());
        });
    }

    public void initialize() {

    }
}
