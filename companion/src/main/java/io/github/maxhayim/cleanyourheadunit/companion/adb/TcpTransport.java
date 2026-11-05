package io.github.maxhayim.cleanyourheadunit.companion.adb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** ADB over the network: the head unit's adbd listening on a TCP port (5555 after the bridge). */
public final class TcpTransport implements Transport {
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public TcpTransport(String host, int port, int timeoutMs) throws IOException {
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
        return "Wi-Fi";
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
