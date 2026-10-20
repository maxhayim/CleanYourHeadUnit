#!/usr/bin/env python3
"""Forward 127.0.0.1:<local port> to <tv host>:<tv port>.

For when the process running tv.py is not allowed onto the LAN (macOS Local Network privacy) but a
terminal is: run this in the terminal, then point tv.py at TV_HOST=127.0.0.1 TV_PORT=<local port>.

Usage: relay.py <tv host> [tv port=5555] [local port=15555]
"""
import socket
import sys
import threading

HOST = sys.argv[1]
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
LOCAL = int(sys.argv[3]) if len(sys.argv) > 3 else 15555


def pipe(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def main():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", LOCAL))
    srv.listen(8)
    print(f"relay 127.0.0.1:{LOCAL} -> {HOST}:{PORT}", flush=True)
    while True:
        client, _ = srv.accept()
        try:
            tv = socket.create_connection((HOST, PORT), timeout=10)
            tv.settimeout(None)
        except OSError as e:
            print("connect failed:", e, flush=True)
            client.close()
            continue
        for a, b in ((client, tv), (tv, client)):
            threading.Thread(target=pipe, args=(a, b), daemon=True).start()


if __name__ == "__main__":
    main()
