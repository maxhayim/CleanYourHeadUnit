package io.github.maxhayim.cleanyourheadunit;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The local firewall (see Firewall for what each mode does). Starts only from the app, after
 * Android's own VPN consent; never at boot. While it runs, Android shows its key icon and this
 * service shows a notification with a Stop button.
 */
public final class FirewallService extends VpnService implements Runnable {
    static final String TAG = "CYHU-Firewall";
    static final String ACTION_STOP = "io.github.maxhayim.cleanyourheadunit.STOP_FIREWALL";
    static final byte[] TUN_IP = {10, 111, (byte) 222, 1};
    static final byte[] DNS_IP = {10, 111, (byte) 222, 2};
    static final String CHANNEL = "firewall";

    private ParcelFileDescriptor tun;
    private Thread loop;
    private ExecutorService pool;
    private Dns.Lists lists;
    private String mode;
    private FileOutputStream out;
    private List<InetAddress> upstream = new ArrayList<>();
    private long upstreamTime;

    private static volatile FirewallService instance;

    /** Called from the app's own screen, so the app is in the foreground and may start a service. */
    public static void start(Context c) {
        c.startService(new Intent(c, FirewallService.class));
    }

    public static void stop(Context c) {
        FirewallService s = instance;
        if (s != null) {
            s.teardown();
            s.stopSelf();
        }
    }

    /** The notification's Stop button. */
    public static final class StopReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            stop(c);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            teardown();
            stopSelf();
            return START_NOT_STICKY;
        }
        showNotification();
        if (tun == null) {
            String err = open();
            if (err != null) {
                Log.w(TAG, err);
                getSharedPreferences("firewall", MODE_PRIVATE).edit().putString("lastError", err).apply();
                teardown();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        // Not sticky: if Android stops the service, protection is off and the app says so. It
        // never comes back by itself.
        return START_NOT_STICKY;
    }

    private String open() {
        mode = Firewall.mode(this);
        Builder b = new Builder().setSession("CleanYourHeadUnit firewall").setMtu(1500);
        b.addAddress(ipString(TUN_IP), 32);
        if (Firewall.MODE_APPS.equals(mode)) {
            if (Build.VERSION.SDK_INT < 21) return "App block needs Android 5.0 or newer.";
            int added = 0;
            for (String pkg : Firewall.blockedApps(this)) {
                try {
                    allow(b, pkg);
                    added++;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (added == 0) return "No installed app is selected for blocking.";
            b.addRoute("0.0.0.0", 0);
            b.addAddress("fd00:6379:6875::1", 128);
            b.addRoute("::", 0);
        } else {
            lists = Firewall.loadLists(this);
            if (upstreamServers().isEmpty()) return "Couldn't find this network's DNS server.";
            b.addDnsServer(ipString(DNS_IP));
            b.addRoute(ipString(DNS_IP), 32);
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    disallowSelf(b);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            pool = Executors.newFixedThreadPool(4);
        }
        try {
            tun = b.establish();
        } catch (RuntimeException e) {
            return "Android refused the VPN: " + e.getMessage();
        }
        if (tun == null) return "VPN permission was not given.";
        out = new FileOutputStream(tun.getFileDescriptor());
        Firewall.running = true;
        Firewall.runningMode = mode;
        getSharedPreferences("firewall", MODE_PRIVATE).edit().remove("lastError").putLong("since", System.currentTimeMillis()).apply();
        loop = new Thread(this, "cyhu-firewall");
        loop.start();
        return null;
    }

    @TargetApi(21)
    private static void allow(Builder b, String pkg) throws PackageManager.NameNotFoundException {
        b.addAllowedApplication(pkg);
    }

    @TargetApi(21)
    private void disallowSelf(Builder b) throws PackageManager.NameNotFoundException {
        b.addDisallowedApplication(getPackageName());
    }

    @Override
    public void run() {
        byte[] buf = new byte[32767];
        long dropped = 0, lastFlush = System.currentTimeMillis();
        try (FileInputStream in = new FileInputStream(tun.getFileDescriptor())) {
            while (!Thread.interrupted()) {
                int n = in.read(buf);
                if (n <= 0) continue;
                if (Firewall.MODE_APPS.equals(mode)) {
                    dropped++;                                  // traffic of a blocked app: dropped
                    if (System.currentTimeMillis() - lastFlush > 2000) {
                        Firewall.add(this, "dropped_packets", dropped);
                        dropped = 0;
                        lastFlush = System.currentTimeMillis();
                    }
                    continue;
                }
                handleDns(buf, n);
            }
        } catch (IOException e) {
            Log.i(TAG, "firewall loop ended: " + e.getMessage());
        } finally {
            if (dropped > 0) Firewall.add(this, "dropped_packets", dropped);
        }
    }

    private void handleDns(byte[] buf, int n) {
        final Dns.Udp u = Dns.parseUdp(buf, n);
        if (u == null || u.dstPort != 53) return;
        final String name = Dns.queryName(u.payload);
        if (name == null) return;
        if (lists.isBlocked(name)) {
            Firewall.add(this, "dns_blocked", 1);
            Firewall.recordBlocked(this, name);
            write(Dns.udpPacket(u.dst, 53, u.src, u.srcPort, Dns.nxdomain(u.payload)));
            return;
        }
        Firewall.add(this, "dns_allowed", 1);
        pool.execute(() -> forward(u));
    }

    private void forward(Dns.Udp u) {
        for (InetAddress server : upstreamServers()) {
            try (DatagramSocket s = new DatagramSocket()) {
                protect(s);
                s.setSoTimeout(5000);
                s.send(new DatagramPacket(u.payload, u.payload.length, server, 53));
                byte[] r = new byte[4096];
                DatagramPacket p = new DatagramPacket(r, r.length);
                s.receive(p);
                byte[] ans = new byte[p.getLength()];
                System.arraycopy(r, 0, ans, 0, ans.length);
                write(Dns.udpPacket(u.dst, 53, u.src, u.srcPort, ans));
                return;
            } catch (IOException e) {
                Log.d(TAG, "DNS forward to " + server + " failed: " + e.getMessage());
            }
        }
        Firewall.add(this, "dns_failed", 1);
    }

    private synchronized void write(byte[] p) {
        try {
            if (out != null) out.write(p);
        } catch (IOException e) {
            Log.d(TAG, "tun write: " + e.getMessage());
        }
    }

    /** The DNS servers of the network underneath (never one of ours, never a third party). */
    private synchronized List<InetAddress> upstreamServers() {
        if (!upstream.isEmpty() && System.currentTimeMillis() - upstreamTime < 30000) return upstream;
        List<InetAddress> found = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 21) {
            found.addAll(networkDns());
        } else {
            for (String k : new String[]{"net.dns1", "net.dns2"}) {
                String v = Unit.sysProp(k);
                try {
                    if (v != null && !v.isEmpty()) found.add(InetAddress.getByName(v));
                } catch (IOException ignored) {
                }
            }
        }
        List<InetAddress> v4 = new ArrayList<>();
        for (InetAddress a : found) if (a.getAddress().length == 4) v4.add(a);
        upstream = v4.isEmpty() ? found : v4;
        upstreamTime = System.currentTimeMillis();
        return upstream;
    }

    @TargetApi(21)
    private List<InetAddress> networkDns() {
        List<InetAddress> out = new ArrayList<>();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return out;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null || nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) out.addAll(lp.getDnsServers());
        }
        return out;
    }

    private void showNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Local firewall", NotificationManager.IMPORTANCE_LOW));
        }
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent stop = PendingIntent.getBroadcast(this, 1, new Intent(this, StopReceiver.class), flags);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class).putExtra("section", "privacy"), flags);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : legacyBuilder();
        nb.setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Privacy Shield firewall is on")
                .setContentText(Firewall.MODE_APPS.equals(Firewall.mode(this)) ? "Blocking the network of the apps you chose" : "Filtering DNS lookups on this unit")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(0, "Stop", stop);
        // A plain ongoing notification: Android keeps a bound VPN service running by itself.
        if (nm != null) nm.notify(7, nb.build());
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder legacyBuilder() {
        return new Notification.Builder(this);
    }

    private void teardown() {
        Firewall.running = false;
        Firewall.runningMode = null;
        if (loop != null) loop.interrupt();
        if (pool != null) pool.shutdownNow();
        try {
            if (tun != null) tun.close();
        } catch (IOException ignored) {
        }
        tun = null;
        out = null;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(7);
    }

    @Override
    public void onRevoke() {
        // Another VPN took over, or the owner turned it off in Android's VPN settings.
        teardown();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        instance = null;
        teardown();
        super.onDestroy();
    }

    static String ipString(byte[] ip) {
        return (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
    }
}
