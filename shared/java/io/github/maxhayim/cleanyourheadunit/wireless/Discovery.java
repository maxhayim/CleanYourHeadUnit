package io.github.maxhayim.cleanyourheadunit.wireless;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Finds Android 11+ Wireless debugging on the local network. adbd announces two mDNS services
 * while Wireless debugging is on: one to connect to, and (only while "Pair device with pairing
 * code" is open) one to pair with. Blocking; call it off the main thread.
 */
public final class Discovery {
    private Discovery() {}

    public static final String CONNECT = "_adb-tls-connect._tcp", PAIRING = "_adb-tls-pairing._tcp";

    public static final class Found {
        public final String name, type, host;
        public final int port;
        public final boolean local;     // the device running this app

        Found(String name, String type, String host, int port, boolean local) {
            this.name = name;
            this.type = type;
            this.host = host;
            this.port = port;
            this.local = local;
        }

        public boolean pairing() {
            return type.startsWith(PAIRING);
        }

        public String address() {
            return host + ":" + port;
        }

        /** adbd names its services "adb-SERIAL-xxxxxx". Shown without the serial. */
        public String label() {
            return name.matches("adb-.+-[A-Za-z0-9]{6}") ? "Wireless debugging" : name;
        }
    }

    /** Everything of these types seen within the time, resolved. */
    @SuppressWarnings("deprecation") // resolveService: its replacement needs API 34
    public static List<Found> find(Context c, String[] types, long timeoutMs) {
        final NsdManager nsd = (NsdManager) c.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        List<Found> out = new ArrayList<>();
        if (nsd == null) return out;
        final BlockingQueue<NsdServiceInfo> seen = new LinkedBlockingQueue<>();
        List<NsdManager.DiscoveryListener> listeners = new ArrayList<>();
        for (String type : types) {
            NsdManager.DiscoveryListener dl = new NsdManager.DiscoveryListener() {
                public void onStartDiscoveryFailed(String t, int e) {}
                public void onStopDiscoveryFailed(String t, int e) {}
                public void onDiscoveryStarted(String t) {}
                public void onDiscoveryStopped(String t) {}
                public void onServiceFound(NsdServiceInfo s) { seen.add(s); }
                public void onServiceLost(NsdServiceInfo s) {}
            };
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, dl);
                listeners.add(dl);
            } catch (RuntimeException ignored) {
            }
        }
        Map<String, NsdServiceInfo> unique = new LinkedHashMap<>();
        long end = System.currentTimeMillis() + timeoutMs;
        try {
            while (System.currentTimeMillis() < end) {
                NsdServiceInfo s = seen.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (s == null) break;
                unique.put(s.getServiceType() + "/" + s.getServiceName(), s);
            }
            Set<InetAddress> mine = ownAddresses();
            // One resolve at a time: before API 34 a second concurrent resolve fails.
            for (NsdServiceInfo s : unique.values()) {
                final CountDownLatch done = new CountDownLatch(1);
                final NsdServiceInfo[] got = new NsdServiceInfo[1];
                try {
                    nsd.resolveService(s, new NsdManager.ResolveListener() {
                        public void onResolveFailed(NsdServiceInfo i, int e) { done.countDown(); }
                        public void onServiceResolved(NsdServiceInfo i) { got[0] = i; done.countDown(); }
                    });
                } catch (RuntimeException e) {
                    continue;
                }
                done.await(3, TimeUnit.SECONDS);
                NsdServiceInfo r = got[0];
                if (r == null || r.getHost() == null || r.getPort() <= 0) continue;
                String type = r.getServiceType() == null ? s.getServiceType() : r.getServiceType();
                type = type.replaceAll("^\\.+|\\.+$", "");
                out.add(new Found(r.getServiceName(), type, r.getHost().getHostAddress(), r.getPort(), mine.contains(r.getHost())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            for (NsdManager.DiscoveryListener dl : listeners) {
                try {
                    nsd.stopServiceDiscovery(dl);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return out;
    }

    /** The first port of this type announced by the device running this app; 0 if none. */
    public static int ownPort(Context c, String type, long timeoutMs) {
        for (Found f : find(c, new String[]{type}, timeoutMs)) if (f.local) return f.port;
        return 0;
    }

    public static Set<InetAddress> ownAddresses() {
        Set<InetAddress> out = new HashSet<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                out.addAll(Collections.list(n.getInetAddresses()));
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
