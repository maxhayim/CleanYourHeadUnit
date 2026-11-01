package io.github.maxhayim.cleanyourheadunit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The local firewall's packet and list handling, kept free of Android classes so it is tested on
 * the JVM: IPv4/UDP/DNS parsing, NXDOMAIN answers, and block-list matching.
 */
public final class Dns {
    private Dns() {}

    // --- block lists ---------------------------------------------------------------------------
    public static final class Lists {
        final Set<String> blocked = new HashSet<>();
        final Set<String> allowed = new HashSet<>();

        public int size() {
            return blocked.size();
        }

        public void block(String d) {
            String n = normalise(d);
            if (n != null) blocked.add(n);
        }

        public void allow(String d) {
            String n = normalise(d);
            if (n != null) allowed.add(n);
        }

        /** A name is blocked if it or a parent domain is listed and neither is allowlisted. */
        public boolean isBlocked(String name) {
            String n = normalise(name);
            if (n == null) return false;
            for (String s = n; s != null; s = parent(s)) if (allowed.contains(s)) return false;
            for (String s = n; s != null; s = parent(s)) if (blocked.contains(s)) return true;
            return false;
        }

        /** Reads hosts files (0.0.0.0 ads.example) and adblock-style lists (||ads.example^). Returns entries read. */
        public int read(Reader r) throws IOException {
            BufferedReader br = new BufferedReader(r);
            int n = 0;
            String line;
            while ((line = br.readLine()) != null) {
                String d = parseLine(line);
                if (d != null) {
                    blocked.add(d);
                    n++;
                }
            }
            return n;
        }
    }

    static String parent(String d) {
        int i = d.indexOf('.');
        if (i < 0) return null;
        String p = d.substring(i + 1);
        return p.indexOf('.') < 0 ? null : p;    // never block a whole top-level domain
    }

    public static String normalise(String d) {
        if (d == null) return null;
        d = d.trim().toLowerCase(Locale.ROOT);
        if (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (d.length() < 3 || d.length() > 253 || d.indexOf('.') < 0) return null;
        if (!d.matches("[a-z0-9_.-]+") || d.startsWith(".") || d.contains("..")) return null;
        return d;
    }

    static String parseLine(String line) {
        String s = line.trim();
        if (s.isEmpty() || s.startsWith("#") || s.startsWith("!") || s.startsWith("[")) return null;
        int hash = s.indexOf('#');
        if (hash > 0) s = s.substring(0, hash).trim();
        if (s.startsWith("||")) {
            int end = s.indexOf('^');
            if (end < 0 || s.contains("$") || s.contains("*") || s.contains("/")) return null;
            return normalise(s.substring(2, end));
        }
        if (s.startsWith("@@")) return null;
        String[] parts = s.split("\\s+");
        if (parts.length >= 2 && (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1") || parts[0].equals("::"))) {
            String d = normalise(parts[1]);
            return d == null || d.equals("localhost") || d.equals("0.0.0.0") || d.endsWith(".localdomain") ? null : d;
        }
        if (parts.length == 1 && !s.contains("/")) return normalise(s);
        return null;
    }

    // --- packets ---------------------------------------------------------------------------------
    /** A UDP-over-IPv4 packet the firewall cares about. Null fields when it's something else. */
    public static final class Udp {
        public byte[] src = new byte[4], dst = new byte[4];
        public int srcPort, dstPort;
        public byte[] payload;
    }

    public static Udp parseUdp(byte[] p, int len) {
        if (len < 28 || (p[0] >> 4) != 4) return null;
        int ihl = (p[0] & 0x0F) * 4;
        if (ihl < 20 || len < ihl + 8 || (p[9] & 0xFF) != 17) return null;
        int total = ((p[2] & 0xFF) << 8) | (p[3] & 0xFF);
        if (total > len || total < ihl + 8) return null;
        Udp u = new Udp();
        System.arraycopy(p, 12, u.src, 0, 4);
        System.arraycopy(p, 16, u.dst, 0, 4);
        u.srcPort = ((p[ihl] & 0xFF) << 8) | (p[ihl + 1] & 0xFF);
        u.dstPort = ((p[ihl + 2] & 0xFF) << 8) | (p[ihl + 3] & 0xFF);
        int ulen = ((p[ihl + 4] & 0xFF) << 8) | (p[ihl + 5] & 0xFF);
        if (ulen < 8 || ihl + ulen > total) return null;
        u.payload = new byte[ulen - 8];
        System.arraycopy(p, ihl + 8, u.payload, 0, ulen - 8);
        return u;
    }

    /** The first question's name of a DNS query, or null if it isn't a well-formed query. */
    public static String queryName(byte[] d) {
        if (d == null || d.length < 17) return null;
        if ((d[2] & 0x80) != 0) return null;                      // a response, not a query
        int qd = ((d[4] & 0xFF) << 8) | (d[5] & 0xFF);
        if (qd < 1) return null;
        StringBuilder sb = new StringBuilder();
        int i = 12;
        while (i < d.length) {
            int n = d[i] & 0xFF;
            if (n == 0) break;
            if (n > 63 || i + 1 + n > d.length) return null;      // compression is not used in queries
            if (sb.length() > 0) sb.append('.');
            for (int k = 0; k < n; k++) sb.append((char) (d[i + 1 + k] & 0xFF));
            i += 1 + n;
        }
        if (i >= d.length || i + 5 > d.length) return null;
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** A copy of the query turned into an NXDOMAIN answer: same id and question, no answers. */
    public static byte[] nxdomain(byte[] q) {
        int end = 12;
        while (end < q.length && (q[end] & 0xFF) != 0) end += 1 + (q[end] & 0xFF);
        end += 5;                                               // zero label + QTYPE + QCLASS
        byte[] r = new byte[Math.min(end, q.length)];
        System.arraycopy(q, 0, r, 0, r.length);
        r[2] = (byte) (0x80 | (q[2] & 0x01));                  // QR=1, opcode 0, keep RD
        r[3] = (byte) 0x83;                                     // RA=1, RCODE=3 (NXDOMAIN)
        r[4] = 0;
        r[5] = 1;                                               // one question
        r[6] = r[7] = r[8] = r[9] = r[10] = r[11] = 0;
        return r;
    }

    /** An IPv4/UDP packet from (srcIp:srcPort) to (dstIp:dstPort) carrying payload, with checksums. */
    public static byte[] udpPacket(byte[] srcIp, int srcPort, byte[] dstIp, int dstPort, byte[] payload) {
        int total = 28 + payload.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        p[2] = (byte) (total >> 8);
        p[3] = (byte) total;
        p[6] = 0x40;                                            // don't fragment
        p[8] = 64;
        p[9] = 17;
        System.arraycopy(srcIp, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        int ck = checksum(p, 0, 20, 0);
        p[10] = (byte) (ck >> 8);
        p[11] = (byte) ck;
        p[20] = (byte) (srcPort >> 8);
        p[21] = (byte) srcPort;
        p[22] = (byte) (dstPort >> 8);
        p[23] = (byte) dstPort;
        int ulen = 8 + payload.length;
        p[24] = (byte) (ulen >> 8);
        p[25] = (byte) ulen;
        System.arraycopy(payload, 0, p, 28, payload.length);
        long pseudo = 0;
        for (int i = 0; i < 4; i += 2) {
            pseudo += ((srcIp[i] & 0xFF) << 8) | (srcIp[i + 1] & 0xFF);
            pseudo += ((dstIp[i] & 0xFF) << 8) | (dstIp[i + 1] & 0xFF);
        }
        pseudo += 17 + ulen;
        int uck = checksum(p, 20, ulen, pseudo);
        if (uck == 0) uck = 0xFFFF;
        p[26] = (byte) (uck >> 8);
        p[27] = (byte) uck;
        return p;
    }

    static int checksum(byte[] b, int off, int len, long start) {
        long sum = start;
        for (int i = 0; i < len - 1; i += 2) sum += ((b[off + i] & 0xFF) << 8) | (b[off + i + 1] & 0xFF);
        if ((len & 1) == 1) sum += (b[off + len - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }
}
