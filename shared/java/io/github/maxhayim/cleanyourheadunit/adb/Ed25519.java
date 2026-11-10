package io.github.maxhayim.cleanyourheadunit.adb;

import java.math.BigInteger;

/**
 * Just enough edwards25519 arithmetic for SPAKE2 (see {@link Spake2}): points in extended
 * coordinates, addition, scalar multiplication, and the standard 32-byte encoding. Written from
 * RFC 8032 section 5.1. It uses BigInteger and is not constant-time; it only runs once per pairing,
 * on the unit itself, against its own Wireless debugging service.
 */
final class Ed25519 {
    private Ed25519() {}

    static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    /** The order of the prime-order subgroup. */
    static final BigInteger L = BigInteger.ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));
    static final BigInteger D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
    private static final BigInteger D2 = D.add(D).mod(P);
    private static final BigInteger SQRT_M1 = BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).shiftRight(2), P);

    static final Point IDENTITY = new Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);
    static final Point BASE = decode(hex("5866666666666666666666666666666666666666666666666666666666666666"));

    /** (X : Y : Z : T) with x = X/Z, y = Y/Z, x*y = T/Z. */
    static final class Point {
        final BigInteger x, y, z, t;

        Point(BigInteger x, BigInteger y, BigInteger z, BigInteger t) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = t;
        }

        Point add(Point q) {
            BigInteger a = y.subtract(x).multiply(q.y.subtract(q.x)).mod(P);
            BigInteger b = y.add(x).multiply(q.y.add(q.x)).mod(P);
            BigInteger c = t.multiply(D2).multiply(q.t).mod(P);
            BigInteger d = z.shiftLeft(1).multiply(q.z).mod(P);
            BigInteger e = b.subtract(a), f = d.subtract(c), g = d.add(c), h = b.add(a);
            return new Point(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P));
        }

        Point negate() {
            return new Point(P.subtract(x).mod(P), y, z, P.subtract(t).mod(P));
        }

        /** this * k for any non-negative integer k (not reduced: the caller decides). */
        Point multiply(BigInteger k) {
            Point r = IDENTITY;
            for (int i = k.bitLength() - 1; i >= 0; i--) {
                r = r.add(r);
                if (k.testBit(i)) r = r.add(this);
            }
            return r;
        }

        byte[] encode() {
            BigInteger zi = z.modInverse(P);
            BigInteger ax = x.multiply(zi).mod(P), ay = y.multiply(zi).mod(P);
            byte[] out = toLittleEndian(ay, 32);
            if (ax.testBit(0)) out[31] |= (byte) 0x80;
            return out;
        }

        boolean sameAs(Point q) {
            return x.multiply(q.z).subtract(q.x.multiply(z)).mod(P).signum() == 0
                    && y.multiply(q.z).subtract(q.y.multiply(z)).mod(P).signum() == 0;
        }
    }

    /** Decodes a point (RFC 8032 5.1.3), or returns null if the bytes aren't a point on the curve. */
    static Point decode(byte[] s) {
        if (s == null || s.length != 32) return null;
        byte[] b = s.clone();
        boolean sign = (b[31] & 0x80) != 0;
        b[31] &= 0x7f;
        BigInteger y = fromLittleEndian(b);
        if (y.compareTo(P) >= 0) return null;
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
        BigInteger v = D.multiply(y2).add(BigInteger.ONE).mod(P);
        BigInteger x = u.multiply(v.modInverse(P)).mod(P).modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        BigInteger x2v = x.multiply(x).multiply(v).mod(P);
        if (!x2v.equals(u)) {
            if (x2v.equals(P.subtract(u).mod(P))) x = x.multiply(SQRT_M1).mod(P);
            else return null;
        }
        if (x.signum() == 0 && sign) return null;
        if (x.testBit(0) != sign) x = P.subtract(x);
        return new Point(x, y, BigInteger.ONE, x.multiply(y).mod(P));
    }

    static BigInteger fromLittleEndian(byte[] b) {
        byte[] be = new byte[b.length + 1];
        for (int i = 0; i < b.length; i++) be[b.length - i] = b[i];
        return new BigInteger(be);
    }

    static byte[] toLittleEndian(BigInteger v, int len) {
        byte[] be = v.toByteArray();
        byte[] out = new byte[len];
        for (int i = 0; i < len && i < be.length; i++) out[i] = be[be.length - 1 - i];
        return out;
    }

    static byte[] hex(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
