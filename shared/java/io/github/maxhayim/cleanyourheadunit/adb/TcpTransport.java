package io.github.maxhayim.cleanyourheadunit.adb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLContext;

/**
 * ADB over the network: adbd listening on a TCP port (5555 after the bridge or `adb tcpip`), or
 * Android 11+ Wireless debugging, which switches the same connection to TLS when adbd asks (STLS).
 */
public final class TcpTransport implements Transport {
    private final String host;
    private final int port;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private SSLContext tls;

    public TcpTransport(String host, int port, int timeoutMs) throws IOException {
        this.host = host;
        this.port = port;
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs * 4);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    @Override
    public synchronized void send(AdbMessage m) throws IOException {
        out.write(m.header());
        if (m.payload.length > 0) out.write(m.payload);
        out.flush();
    }

    @Override
    public void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("The head unit closed the connection.");
            off += n;
        }
    }

    @Override
    public String kind() {
        return tls != null && socket instanceof javax.net.ssl.SSLSocket ? "Wireless debugging" : "Wi-Fi";
    }

    /** Allows the switch to TLS when adbd asks for it (Wireless debugging, after pairing). */
    public TcpTransport withTls(SSLContext ctx) {
        this.tls = ctx;
        return this;
    }

    boolean canTls() {
        return tls != null;
    }

    /** Called by AdbConnection after both sides sent STLS. */
    void startTls() throws IOException {
        javax.net.ssl.SSLSocket s = AdbTls.client(tls, socket, host, port);
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    /** Longer reads while the owner answers the "Allow debugging?" prompt on the unit. */
    public void setReadTimeout(int ms) throws IOException {
        socket.setSoTimeout(ms);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
