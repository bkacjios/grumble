package gg.grumble.client.utils;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;

public class MumbleBonjourBrowser {

    private static final String SERVICE_TYPE = "_mumble._tcp.local.";

    public interface Listener {
        void onServiceFound(String name, String host, int port);

        void onServiceLost(String name);
    }

    private final JmDNS jmdns;
    private volatile Listener listener;

    public MumbleBonjourBrowser() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent ev) {
                jmdns.requestServiceInfo(ev.getType(), ev.getName(), 1000);
            }

            @Override
            public void serviceRemoved(ServiceEvent ev) {
                Listener l = listener;
                if (l != null) {
                    l.onServiceLost(ev.getName());
                }
            }

            @Override
            public void serviceResolved(ServiceEvent ev) {
                ServiceInfo info = ev.getInfo();
                String[] addresses = info.getHostAddresses();
                if (addresses.length == 0) return;

                Listener l = listener;
                if (l != null) {
                    l.onServiceFound(info.getName(), addresses[0], info.getPort());
                }
            }
        });
    }

    public void stop() throws IOException {
        jmdns.close();
    }
}
