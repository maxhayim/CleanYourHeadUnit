package io.github.maxhayim.cleanyourheadunit.companion.adb;

import java.io.Closeable;
import java.io.IOException;

/** A byte pipe to adbd: a TCP socket or a USB bulk interface. */
public interface Transport extends Closeable {
    /** Sends one ADB packet (header, then payload). */
    void send(AdbMessage m) throws IOException;

    /** Reads exactly buf.length bytes, or throws. */
    void readFully(byte[] buf) throws IOException;

    /** "USB" or "Wi-Fi", for the screen. */
    String kind();

    @Override
    void close();
}
