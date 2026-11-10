package io.github.maxhayim.cleanyourheadunit.adb;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SPAKE2 over edwards25519, compatible with BoringSSL's SPAKE2 (crypto/curve25519/spake25519.c),
 * which is what Android's Wireless debugging uses to check the six-digit pairing code. The code
 * itself is never sent: both sides only learn the same key if they used the same code.
 *
 * The fixed points M and N are BoringSSL's: the first valid point found by repeatedly SHA-256
 * hashing "edwards25519 point generation seed (M)" (or "(N)"). Their encodings are checked in tests.
 */
final class Spake2 {
    enum Role { ALICE, BOB }

    static final Ed25519.Point M = Ed25519.decode(Ed25519.hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"));
    static final Ed25519.Point N = Ed25519.decode(Ed25519.hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"));

    private final Role role;
    private final byte[] myName, theirName;
    private BigInteger privateKey, passwordScalar;
    private byte[] passwordHash, myMsg;

    Spake2(Role role, byte[] myName, byte[] theirName) {
        this.role = role;
        this.myName = myName.clone();
        this.theirName = theirName.clone();
    }

    /** Our 32-byte message: (private key)·B + (password scalar)·(M for Alice, N for Bob). */
    byte[] generateMsg(byte[] password, SecureRandom random) {
        byte[] r = new byte[64];
        random.nextBytes(r);
        // Reduce mod l, then multiply by the cofactor (BoringSSL's left_shift_3).
        privateKey = Ed25519.fromLittleEndian(r).mod(Ed25519.L).shiftLeft(3);
        Ed25519.Point p = Ed25519.BASE.multiply(privateKey);

        passwordHash = sha512(password);
        BigInteger s = Ed25519.fromLittleEndian(passwordHash).mod(Ed25519.L);
        // BoringSSL's "password scalar hack": add l, 2l and 4l as needed to make it a multiple of 8.
        BigInteger l = Ed25519.L;
        for (int bit = 0; bit < 3; bit++) {
            if (s.testBit(bit)) s = s.add(l);
            l = l.shiftLeft(1);
        }
        passwordScalar = s;
        Ed25519.Point mask = (role == Role.ALICE ? M : N).multiply(passwordScalar);
        myMsg = p.add(mask).encode();
        return myMsg.clone();
    }

    /** The shared 64-byte key, or an exception if their message isn't a valid point. */
    byte[] processMsg(byte[] theirMsg) throws java.io.IOException {
        if (myMsg == null) throw new IllegalStateException("generateMsg first");
        Ed25519.Point qStar = Ed25519.decode(theirMsg);
        if (qStar == null) throw new java.io.IOException("The pairing message from the unit isn't valid.");
        Ed25519.Point peersMask = (role == Role.ALICE ? N : M).multiply(passwordScalar);
        Ed25519.Point q = qStar.add(peersMask.negate());
        byte[] dh = q.multiply(privateKey).encode();
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (role == Role.ALICE) {
            prefixed(sha, myName);
            prefixed(sha, theirName);
            prefixed(sha, myMsg);
            prefixed(sha, theirMsg);
        } else {
            prefixed(sha, theirName);
            prefixed(sha, myName);
            prefixed(sha, theirMsg);
            prefixed(sha, myMsg);
        }
        prefixed(sha, dh);
        prefixed(sha, passwordHash);
        byte[] key = sha.digest();
        Arrays.fill(dh, (byte) 0);
        return key;
    }

    /** An 8-byte little-endian length, then the data. */
    private static void prefixed(MessageDigest sha, byte[] data) {
        long n = data.length;
        byte[] len = new byte[8];
        for (int i = 0; i < 8; i++) len[i] = (byte) (n >>> (8 * i));
        sha.update(len);
        sha.update(data);
    }

    static byte[] sha512(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
