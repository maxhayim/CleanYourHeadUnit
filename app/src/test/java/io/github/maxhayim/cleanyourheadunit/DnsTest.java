package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;

/** The local firewall's packet and list code, on the JVM. Proves the bytes; not that a unit's network behaves. */
public class DnsTest {

    static byte[] query(String name, int id) {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        b.write(id >> 8);
        b.write(id);
        b.write(0x01);  // RD
        b.write(0x00);
        b.write(0); b.write(1);   // QDCOUNT
        for (int i = 0; i < 6; i++) b.write(0);
        for (String label : name.split("\\.")) {
            b.write(label.length());
            for (char ch : label.toCharArray()) b.write(ch);
        }
        b.write(0);
        b.write(0); b.write(1);   // A
        b.write(0); b.write(1);   // IN
        return b.toByteArray();
    }

    @Test
    public void readsQueryName() {
        assertEquals("bigdata.adups.com", Dns.queryName(query("BigData.Adups.com", 7)));
        byte[] resp = query("x.example", 1);
        resp[2] |= (byte) 0x80;
        assertNull(Dns.queryName(resp));
        assertNull(Dns.queryName(new byte[5]));
        byte[] bad = query("x.example", 1);
        bad[12] = 99;                             // label longer than the packet
        assertNull(Dns.queryName(bad));
    }

    @Test
    public void nxdomainKeepsIdAndQuestion() {
        byte[] q = query("ads.example.com", 0x1234);
        byte[] r = Dns.nxdomain(q);
        assertEquals(q.length, r.length);
        assertEquals(0x12, r[0] & 0xFF);
        assertEquals(0x34, r[1] & 0xFF);
        assertEquals(0x81, r[2] & 0xFF);          // response, RD kept
        assertEquals(0x83, r[3] & 0xFF);          // RA, NXDOMAIN
        assertEquals(0, r[7]);                    // no answers
    }

    @Test
    public void udpPacketRoundTripsWithValidChecksums() {
        byte[] src = {10, 111, (byte) 222, 2}, dst = {10, 111, (byte) 222, 1};
        byte[] payload = query("example.org", 9);
        byte[] p = Dns.udpPacket(src, 53, dst, 40000, payload);
        Dns.Udp u = Dns.parseUdp(p, p.length);
        assertNotNull(u);
        assertEquals(53, u.srcPort);
        assertEquals(40000, u.dstPort);
        assertEquals("example.org", Dns.queryName(u.payload));
        assertEquals(0, Dns.checksum(p, 0, 20, 0));   // IPv4 header sums to zero with its checksum
        long pseudo = 0;
        for (int i = 0; i < 4; i += 2) {
            pseudo += ((src[i] & 0xFF) << 8) | (src[i + 1] & 0xFF);
            pseudo += ((dst[i] & 0xFF) << 8) | (dst[i + 1] & 0xFF);
        }
        pseudo += 17 + 8 + payload.length;
        assertEquals(0, Dns.checksum(p, 20, 8 + payload.length, pseudo));
        assertNull(Dns.parseUdp(p, 10));
        p[9] = 6;                                       // TCP: not ours
        assertNull(Dns.parseUdp(p, p.length));
    }

    @Test
    public void listsReadHostsAndAdblockAndMatchParents() throws Exception {
        Dns.Lists l = new Dns.Lists();
        int n = l.read(new StringReader("# comment\n0.0.0.0 ads.example.com\n127.0.0.1 localhost\n||tracker.example.net^\n"
                + "||bad.example.org^$third-party\n@@||ok.example.com^\nplain.example.io\n[Adblock Plus]\n0.0.0.0 0.0.0.0\n"));
        assertEquals(3, n);
        assertTrue(l.isBlocked("ads.example.com"));
        assertTrue(l.isBlocked("x.y.ads.example.com"));
        assertTrue(l.isBlocked("tracker.example.net."));
        assertFalse(l.isBlocked("example.com"));
        assertFalse(l.isBlocked("bad.example.org"));
        assertFalse(l.isBlocked("localhost"));
        l.allow("ads.example.com");
        assertFalse(l.isBlocked("cdn.ads.example.com"));
        l.block("com");                                  // a bare top-level domain is never accepted
        assertFalse(l.isBlocked("anything.com"));
        assertNull(Dns.normalise("bad domain.com"));
    }
}
