package gg.grumble.client.utils;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;

public class MumbleBonjourBrowser {

    private final JmDNS jmdns;

    public MumbleBonjourBrowser() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
    }

    public void start() {
        String type = "_mumble._tcp.local.";
        jmdns.addServiceListener(type, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent ev) {
                jmdns.requestServiceInfo(ev.getType(), ev.getName(), 1000);
            }

            @Override
            public void serviceRemoved(ServiceEvent ev) {
                System.out.println("Mumble service gone: " + ev.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent ev) {
                ServiceInfo info = ev.getInfo();
                String name = info.getName();
                String host = info.getHostAddresses()[0];
                int port = info.getPort();

                System.out.printf("Discovered %s → %s:%d%n", name, host, port);
            }
        });
    }

    public void stop() throws IOException {
        jmdns.close();
    }
}
