package io.github.maxhayim.cleanyourheadunit.companion.adb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * One ADB packet: a 24-byte little-endian header and an optional payload. The format is the one
 * AOSP documents in system/core/adb/protocol.txt (now packages/modules/adb/protocol.txt).
 */
public final class AdbMessage {
    public static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257, STLS = 0x534c5453, SYNC = 0x434e5953;

    /** The original protocol version. Every adbd since Android 4.2 accepts it. */
    public static final int VERSION = 0x01000000;
    /** What we offer; the unit answers with its own limit and we never send more than that. */
    public static final int MAX_PAYLOAD = 256 * 1024;

    public static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;

    public static final int HEADER = 24;

    public final int command, arg0, arg1;
    public final byte[] payload;

    public AdbMessage(int command, int arg0, int arg1, byte[] payload) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public static AdbMessage of(int command, int arg0, int arg1, String text) {
        return new AdbMessage(command, arg0, arg1, (text + "\0").getBytes(StandardCharsets.UTF_8));
    }

    /** The checksum older adbd versions (protocol version 0x01000000) insist on. */
    public static int checksum(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        return sum;
    }

    public byte[] header() {
        ByteBuffer b = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(payload.length).putInt(checksum(payload)).putInt(~command);
        return b.array();
    }

    /** Parses a header; the payload is read separately, {@link #length} bytes of it. */
    public static int[] parseHeader(byte[] h) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = b.getInt(), a0 = b.getInt(), a1 = b.getInt(), len = b.getInt(), check = b.getInt(), magic = b.getInt();
        if (magic != ~cmd) throw new IOException("Not an ADB packet (bad magic).");
        if (len < 0 || len > 16 * 1024 * 1024) throw new IOException("ADB packet too large: " + len);
        return new int[]{cmd, a0, a1, len, check};
    }

    public String text() {
        int n = payload.length;
        while (n > 0 && payload[n - 1] == 0) n--;
        return new String(payload, 0, n, StandardCharsets.UTF_8);
    }

    public static String name(int command) {
        switch (command) {
            case CNXN: return "CNXN";
            case AUTH: return "AUTH";
            case OPEN: return "OPEN";
            case OKAY: return "OKAY";
            case CLSE: return "CLSE";
            case WRTE: return "WRTE";
            case STLS: return "STLS";
            case SYNC: return "SYNC";
            default: return String.format("0x%08x", command);
        }
    }

    @Override
    public String toString() {
        return name(command) + "(" + arg0 + ", " + arg1 + ", " + payload.length + " bytes)";
    }
}
