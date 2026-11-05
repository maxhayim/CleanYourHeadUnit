package io.github.maxhayim.cleanyourheadunit.companion.adb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;

/**
 * ADB over USB, with this phone as the USB host (an OTG cable or adapter) and the head unit as
 * the USB device. Works only when the unit's port can act as a device and USB debugging is on;
 * then the unit offers an interface of class 0xFF, subclass 0x42, protocol 0x01.
 */
public final class UsbTransport implements Transport {
    public static final int ADB_CLASS = 0xff, ADB_SUBCLASS = 0x42, ADB_PROTOCOL = 0x01;
    private static final int TIMEOUT_MS = 3000;

    private final UsbDeviceConnection conn;
    private final UsbInterface intf;
    private final UsbEndpoint in, out;

    private UsbTransport(UsbDeviceConnection conn, UsbInterface intf, UsbEndpoint in, UsbEndpoint out) {
        this.conn = conn;
        this.intf = intf;
        this.in = in;
        this.out = out;
    }

    /** The ADB interface of a USB device, or null if it has none (debugging off, or not a device port). */
    public static UsbInterface adbInterface(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            if (f.getInterfaceClass() == ADB_CLASS && f.getInterfaceSubclass() == ADB_SUBCLASS
                    && f.getInterfaceProtocol() == ADB_PROTOCOL) return f;
        }
        return null;
    }

    /** Opens the ADB interface. The caller has already got the owner's USB permission for this device. */
    public static UsbTransport open(UsbManager um, UsbDevice d) throws IOException {
        UsbInterface f = adbInterface(d);
        if (f == null) throw new IOException("This USB device has no ADB interface. Is USB debugging on?");
        UsbEndpoint in = null, out = null;
        for (int i = 0; i < f.getEndpointCount(); i++) {
            UsbEndpoint e = f.getEndpoint(i);
            if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e;
            else out = e;
        }
        if (in == null || out == null) throw new IOException("The ADB interface has no bulk endpoints.");
        UsbDeviceConnection c = um.openDevice(d);
        if (c == null) throw new IOException("Android didn't open the USB device (permission not given?).");
        if (!c.claimInterface(f, true)) {
            c.close();
            throw new IOException("Couldn't claim the ADB interface.");
        }
        return new UsbTransport(c, f, in, out);
    }

    @Override
    public synchronized void send(AdbMessage m) throws IOException {
        write(m.header());
        if (m.payload.length > 0) {
            write(m.payload);
            // A transfer that fills whole packets needs a zero-length packet to end it.
            if (m.payload.length % out.getMaxPacketSize() == 0) conn.bulkTransfer(out, new byte[0], 0, TIMEOUT_MS);
        }
    }

    private void write(byte[] data) throws IOException {
        int off = 0;
        while (off < data.length) {
            int n = Math.min(data.length - off, 16384);
            byte[] chunk = new byte[n];
            System.arraycopy(data, off, chunk, 0, n);
            int w = conn.bulkTransfer(out, chunk, n, TIMEOUT_MS);
            if (w < 0) throw new IOException("USB write failed (cable unplugged?).");
            off += w;
        }
    }

    @Override
    public void readFully(byte[] buf) throws IOException {
        int off = 0;
        byte[] tmp = new byte[16384];
        while (off < buf.length) {
            int want = Math.min(buf.length - off, tmp.length);
            int n = conn.bulkTransfer(in, tmp, want, 0);
            if (n < 0) throw new IOException("USB read failed (cable unplugged?).");
            System.arraycopy(tmp, 0, buf, off, n);
            off += n;
        }
    }

    @Override
    public String kind() {
        return "USB";
    }

    @Override
    public void close() {
        try {
            conn.releaseInterface(intf);
        } catch (RuntimeException ignored) {
        }
        conn.close();
    }
}
